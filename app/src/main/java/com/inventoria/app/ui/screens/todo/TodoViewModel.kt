package com.inventoria.app.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.Todo
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

/** One flattened row in a todo hierarchy display -- [depth] and [parentName] mirror
 * TaskTrackerScreen's ActiveSessionTreeEntry (same DFS-pre-order-over-a-parent-pointer shape),
 * except a todo's parent can live in a different day section than the child (differing
 * deadlines), so [parentName] is populated even at depth 0 whenever the parent exists but isn't
 * part of the current scoped list -- the child still needs its "sub-todo of" breadcrumb even
 * though it can't be visually nested under a parent that isn't here. [childProgress] is
 * (completed, total) direct children, computed globally regardless of scope, so a parent shows
 * accurate progress even if some children are filed under a different day. */
data class TodoTreeEntry(
    val todo: Todo,
    val depth: Int,
    val parentName: String?,
    val childProgress: Pair<Int, Int>?
)

/** One day's worth of dated todos. [visibleTodos] is what actually gets rendered under this
 * section -- for "Today" that also includes currently-overdue todos pulled in from other days
 * (carry-over, no cloning), while [totalDueCount]/[completedDueCount] stay keyed strictly to
 * todos whose OWN deadline is this day, so a day's completion percentage doesn't get skewed by
 * whatever happens to be visually parked under Today while overdue. */
data class TodoDaySection(
    val dayStart: Long,
    val visibleTodos: List<TodoTreeEntry>,
    val totalDueCount: Int,
    val completedDueCount: Int
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val undatedTodoEntries: StateFlow<List<TodoTreeEntry>> = todos
        .map { list ->
            val byId = list.associateBy { it.id }
            val childCounts = computeChildCounts(list)
            buildTodoTree(list.filter { it.deadline == null }, byId, childCounts)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todoSections: StateFlow<List<TodoDaySection>> = todos
        .map { list -> buildTodoSections(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAddingNew = MutableStateFlow(false)
    val isAddingNew: StateFlow<Boolean> = _isAddingNew.asStateFlow()

    private val _pendingEditTodo = MutableStateFlow<Todo?>(null)
    val pendingEditTodo: StateFlow<Todo?> = _pendingEditTodo.asStateFlow()

    init {
        startPeriodicCleanup()
    }

    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (isActive) {
                todoRepository.purgeOldDeletedTodos(System.currentTimeMillis() - 86_400_000)
                delay(60_000)
            }
        }
    }

    fun startAddingTodo() {
        _isAddingNew.value = true
    }

    fun startEditingTodo(todo: Todo) {
        _pendingEditTodo.value = todo
    }

    fun dismissDialog() {
        _isAddingNew.value = false
        _pendingEditTodo.value = null
    }

    fun addTodo(title: String, kind: TaskKind, deadline: Long?, parentTodoId: String?) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            todoRepository.insertTodo(
                Todo(id = UUID.randomUUID().toString(), title = trimmed, kind = kind, deadline = deadline, parentTodoId = parentTodoId)
            )
        }
        _isAddingNew.value = false
    }

    fun saveEditedTodo(todo: Todo, title: String, kind: TaskKind, deadline: Long?, parentTodoId: String?) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            todoRepository.updateTodo(todo.copy(title = trimmed, kind = kind, deadline = deadline, parentTodoId = parentTodoId))
        }
        _pendingEditTodo.value = null
    }

    fun setCompleted(todo: Todo, completed: Boolean) {
        viewModelScope.launch { todoRepository.setCompleted(todo.id, completed) }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch { todoRepository.softDeleteTodo(todo.id) }
    }

    /** Every id that would become a cycle if picked as [todoId]'s parent -- itself, plus every
     * descendant (direct or transitive), since a descendant becoming an ancestor loops the tree. */
    fun invalidParentIds(todoId: String, all: List<Todo>): Set<String> {
        val childrenByParentId = all.groupBy { it.parentTodoId }
        val result = mutableSetOf(todoId)
        fun visit(id: String) {
            childrenByParentId[id]?.forEach { child ->
                if (result.add(child.id)) visit(child.id)
            }
        }
        visit(todoId)
        return result
    }

    private fun computeChildCounts(all: List<Todo>): Map<String, Pair<Int, Int>> =
        all.filter { it.parentTodoId != null }
            .groupBy { it.parentTodoId!! }
            .mapValues { (_, children) -> children.count { it.isCompleted } to children.size }

    /** DFS pre-order over [scoped], grouping strictly within it (a child only nests under its
     * parent when both share this scope, e.g. the same day section) -- copies
     * TaskTrackerScreen's buildActiveSessionTree shape exactly: group by parent pointer, roots
     * are items whose parent is null OR outside the current scope, recurse depth-first. */
    private fun buildTodoTree(
        scoped: List<Todo>,
        allTodosById: Map<String, Todo>,
        childCounts: Map<String, Pair<Int, Int>>
    ): List<TodoTreeEntry> {
        val scopedIds = scoped.map { it.id }.toSet()
        val childrenByParentId = scoped.groupBy { it.parentTodoId }
        val result = mutableListOf<TodoTreeEntry>()

        fun visit(todo: Todo, depth: Int) {
            val parentName = todo.parentTodoId?.let { allTodosById[it]?.title }
            result.add(TodoTreeEntry(todo, depth, parentName, childCounts[todo.id]))
            childrenByParentId[todo.id]?.forEach { child -> visit(child, depth + 1) }
        }

        scoped.filter { it.parentTodoId == null || it.parentTodoId !in scopedIds }
            .forEach { visit(it, 0) }
        return result
    }

    private fun buildTodoSections(all: List<Todo>): List<TodoDaySection> {
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val byId = all.associateBy { it.id }
        val childCounts = computeChildCounts(all)
        val dated = all.filter { it.deadline != null }
        val byDeadline = dated.groupBy { it.deadline!! }
        // Overdue = still incomplete past its own deadline -- carried over into Today's section
        // (same row, no cloning) rather than left invisible under a day nobody's looking at.
        val overdue = dated.filter { !it.isCompleted && it.deadline!! < todayStart }
        val overdueIds = overdue.map { it.id }.toSet()

        val sections = mutableListOf<TodoDaySection>()

        val todayOwnTodos = byDeadline[todayStart] ?: emptyList()
        if (todayOwnTodos.isNotEmpty() || overdue.isNotEmpty()) {
            sections.add(
                TodoDaySection(
                    dayStart = todayStart,
                    visibleTodos = buildTodoTree(todayOwnTodos + overdue, byId, childCounts),
                    totalDueCount = todayOwnTodos.size,
                    completedDueCount = todayOwnTodos.count { it.isCompleted }
                )
            )
        }

        // Upcoming days, soonest first.
        byDeadline.keys.filter { it > todayStart }.sorted().forEach { day ->
            val forDay = byDeadline[day]!!
            sections.add(TodoDaySection(day, buildTodoTree(forDay, byId, childCounts), forDay.size, forDay.count { it.isCompleted }))
        }

        // Past days, most recent first -- only what's left after pulling overdue rows into Today
        // (i.e. whatever already got completed) is actually shown as rows, but the percentage
        // still reflects everything that was originally due that day.
        byDeadline.keys.filter { it < todayStart }.sortedDescending().forEach { day ->
            val forDay = byDeadline[day]!!
            val visible = forDay.filter { it.id !in overdueIds }
            if (visible.isNotEmpty()) {
                sections.add(TodoDaySection(day, buildTodoTree(visible, byId, childCounts), forDay.size, forDay.count { it.isCompleted }))
            }
        }

        return sections
    }
}
