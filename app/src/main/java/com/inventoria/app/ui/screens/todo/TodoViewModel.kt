package com.inventoria.app.ui.screens.todo

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TaskTypeRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.modalTypeIdFor
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.ui.screens.task.TaskTimerService
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * accurate progress even if some children are filed under a different day. [effectiveState] is
 * what should actually be displayed/clicked: [todo]'s own stored state, EXCEPT when it isn't
 * already COMPLETE and has at least one COMPLETE direct child, which displays as IN_PROGRESS
 * regardless of what's actually stored -- a live-computed override, never written back to the
 * todo itself. */
data class TodoTreeEntry(
    val todo: Todo,
    val depth: Int,
    val parentName: String?,
    val childProgress: Pair<Int, Int>?,
    val effectiveState: TodoState
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
    @ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository,
    private val taskRepository: TaskRepository,
    private val taskTypeRepository: TaskTypeRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** For the edit dialog's type picker and the type label on rows. Same visible-only list the
     * Tasks screen uses, so a soft-deleted type shows as unset in both places. */
    val taskTypes: StateFlow<List<TaskType>> = taskTypeRepository.getVisibleTaskTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskTypeNamesById: StateFlow<Map<String, String>> = taskTypes
        .map { types -> types.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val undatedTodoEntries: StateFlow<List<TodoTreeEntry>> = todos
        .map { list ->
            val byId = list.associateBy { it.id }
            val childCounts = computeChildCounts(list)
            val todayStart = getStartOfDay(System.currentTimeMillis())
            val undated = list.filter { effectiveSectionDay(it, byId, todayStart) == null }
            buildTodoTree(undated, byId, childCounts)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todoSections: StateFlow<List<TodoDaySection>> = todos
        .map { list -> buildTodoSections(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAddingNew = MutableStateFlow(false)
    val isAddingNew: StateFlow<Boolean> = _isAddingNew.asStateFlow()

    private val _pendingEditTodo = MutableStateFlow<Todo?>(null)
    val pendingEditTodo: StateFlow<Todo?> = _pendingEditTodo.asStateFlow()

    // Tap-to-select: a selected todo highlights on screen and becomes the default parent for any
    // new todo created via the FAB. Tapping the already-selected todo opens it for editing
    // instead (see TodoScreen's row click handling); tapping anything else (a different todo,
    // background space) re-targets or clears this.
    private val _selectedTodoId = MutableStateFlow<String?>(null)
    val selectedTodoId: StateFlow<String?> = _selectedTodoId.asStateFlow()

    fun selectTodo(id: String) {
        _selectedTodoId.value = id
    }

    fun clearSelection() {
        _selectedTodoId.value = null
    }

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

    /** The edit dialog's "Create Sub-Todo" button: close the edit dialog and open Add, pre-parented
     * to [parent] -- reuses the same selectedTodoId the FAB already reads for its own prefill, so
     * there's only one "what parent should a new todo default to" channel. */
    fun startAddingSubTodoOf(parent: Todo) {
        selectTodo(parent.id)
        _pendingEditTodo.value = null
        _isAddingNew.value = true
    }

    fun addTodo(title: String, kind: TaskKind, taskTypeId: String?, deadline: Long?, deadlineMinuteOfDay: Int?, parentTodoId: String?, priority: TodoPriority?) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val time = deadlineMinuteOfDay.takeIf { deadline != null }
        viewModelScope.launch {
            todoRepository.insertTodo(
                Todo(id = UUID.randomUUID().toString(), title = trimmed, kind = kind, taskTypeId = taskTypeId, deadline = deadline, deadlineMinuteOfDay = time, parentTodoId = parentTodoId, priority = priority)
            )
        }
        _isAddingNew.value = false
    }

    fun saveEditedTodo(todo: Todo, title: String, kind: TaskKind, taskTypeId: String?, deadline: Long?, deadlineMinuteOfDay: Int?, parentTodoId: String?, priority: TodoPriority?) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        // A time without a date would be unreachable in the UI and would sort/display as a due
        // time nothing is actually due at, so the "null deadline clears the time" invariant is
        // enforced here rather than trusted from the dialog.
        val time = deadlineMinuteOfDay.takeIf { deadline != null }
        viewModelScope.launch {
            todoRepository.updateTodo(todo.copy(title = trimmed, kind = kind, taskTypeId = taskTypeId, deadline = deadline, deadlineMinuteOfDay = time, parentTodoId = parentTodoId, priority = priority))
        }
        _pendingEditTodo.value = null
    }

    /** Tapping a todo's checkbox: COMPLETE -> INCOMPLETE, anything else (INCOMPLETE or a
     * currently-displayed IN_PROGRESS, whether stored or live-derived from mixed children) ->
     * COMPLETE. See TodoRepository.setStateWithCascade for what happens to descendants. */
    fun toggleComplete(todo: Todo) {
        viewModelScope.launch {
            todoRepository.setStateWithCascade(todo.id, complete = todo.state != TodoState.COMPLETE)
        }
    }

    /** Whatever type tasks named [name] have settled on, or null if that name is new or has no
     * majority yet -- the same rule the Tasks screen's autofill applies, so starting "Eating with
     * V" from a todo lands on the same type as typing it into the tracker would.
     *
     * Read once here rather than held as a StateFlow: it is only needed at the instant a task is
     * started, and the Todos screen has no other reason to subscribe to the whole task table. */
    private suspend fun learnedTypeIdForName(name: String): String? {
        val target = name.trim().lowercase()
        if (target.isBlank()) return null
        val sameName = taskRepository.getVisibleTasksList().filter { it.name.trim().lowercase() == target }
        return if (sameName.isEmpty()) null else modalTypeIdFor(sameName)
    }

    /** Kicks off a real tracked session from this todo -- same shape as
     * TaskTrackerViewModel.addNewTask(), just seeded with the todo's title/kind and tagged with
     * Task.originTodoId so the completion check-in can find its way back to this todo once the
     * session stops (see TaskTrackerViewModel.stopTask()). */
    fun startTaskFromTodo(todo: Todo) {
        if (todo.activeSessionGroupId != null) return
        viewModelScope.launch {
            val groupId = UUID.randomUUID().toString()
            val task = Task(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                name = todo.title,
                kind = todo.kind,
                taskTypeId = todo.taskTypeId ?: learnedTypeIdForName(todo.title),
                isRunning = true,
                startTime = System.currentTimeMillis(),
                originTodoId = todo.id
            )
            taskRepository.insertTask(task)
            todoRepository.setActiveSessionGroupId(todo.id, groupId)
            val intent = Intent(context, TaskTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { context.startForegroundService(intent) }
            else { context.startService(intent) }
            syncRepository.triggerFullSync()
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch { todoRepository.softDeleteTodo(todo.id) }
    }

    /** Drag-and-drop parenting: [child] dropped onto [newParentId]. Only parentTodoId changes --
     * [child]'s own deadline/kind/etc. are untouched, and since a todo's children follow it in
     * whichever day section it lands in (see effectiveSectionDay), dragging a todo that already
     * has its own children brings that whole subtree along automatically. */
    fun setParent(child: Todo, newParentId: String) {
        if (newParentId == child.parentTodoId) return
        viewModelScope.launch { todoRepository.updateTodo(child.copy(parentTodoId = newParentId)) }
    }

    /** Drag-and-drop detach: dropping a todo on anything that isn't another todo row (a day
     * header, the "No Deadline" label, blank space) removes its parent entirely. */
    fun clearParent(child: Todo) {
        if (child.parentTodoId == null) return
        viewModelScope.launch { todoRepository.updateTodo(child.copy(parentTodoId = null)) }
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
            .mapValues { (_, children) -> children.count { it.state == TodoState.COMPLETE } to children.size }

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
            val progress = childCounts[todo.id]
            // Any completed direct child reads as "in progress" (unless this todo is already
            // COMPLETE itself, which is never downgraded) -- purely a display computation, never
            // written back. Covers both a genuine partial mix and the "only child, now done" case.
            val effectiveState = if (todo.state != TodoState.COMPLETE && progress != null && progress.first >= 1) {
                TodoState.IN_PROGRESS
            } else {
                todo.state
            }
            result.add(TodoTreeEntry(todo, depth, parentName, progress, effectiveState))
            childrenByParentId[todo.id]?.forEach { child -> visit(child, depth + 1) }
        }

        scoped.filter { it.parentTodoId == null || it.parentTodoId !in scopedIds }
            .forEach { visit(it, 0) }
        return result
    }

    /** Which day a todo visually belongs to: its own deadline if it has one -- redirected to
     * today if that deadline has passed and it's still incomplete (the overdue carry-over) --
     * else walks up parentTodoId until it finds a dated ancestor and inherits THAT ancestor's
     * resolved day (so a deadline-less child sits with its parent, including following the
     * parent into Today if the parent itself is overdue), else null (the "No Deadline" section,
     * only reached when nothing in the whole ancestor chain has a deadline at all). */
    private fun effectiveSectionDay(todo: Todo, byId: Map<String, Todo>, todayStart: Long): Long? {
        var current: Todo? = todo
        while (current != null) {
            val deadline = current.deadline
            if (deadline != null) {
                return if (current.state != TodoState.COMPLETE && deadline < todayStart) todayStart else deadline
            }
            current = current.parentTodoId?.let { byId[it] }
        }
        return null
    }

    /** Within a day, todos carrying a deadline time come first in chronological order, all-day
     * ones after them. sortedBy is stable, so everything untimed keeps the DAO's createdAt DESC
     * order untouched, and so do timed todos sharing a minute. */
    private fun sortedByDeadlineTime(todos: List<Todo>): List<Todo> =
        todos.sortedBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }

    private fun buildTodoSections(all: List<Todo>): List<TodoDaySection> {
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val byId = all.associateBy { it.id }
        val childCounts = computeChildCounts(all)

        // "Due" stats (the day header's X% Done bar) are strictly about todos with their OWN
        // deadline -- a deadline-less child inheriting a section from its parent doesn't make it
        // "due" that day for percentage purposes.
        val ownDueByDeadline = all.filter { it.deadline != null }.groupBy { it.deadline!! }

        val sectionDayById = all.associate { it.id to effectiveSectionDay(it, byId, todayStart) }
        val bySectionDay = all.filter { sectionDayById[it.id] != null }.groupBy { sectionDayById[it.id]!! }

        val sections = mutableListOf<TodoDaySection>()

        val todaySectionTodos = bySectionDay[todayStart] ?: emptyList()
        if (todaySectionTodos.isNotEmpty()) {
            val todayOwnTodos = ownDueByDeadline[todayStart] ?: emptyList()
            sections.add(
                TodoDaySection(
                    dayStart = todayStart,
                    visibleTodos = buildTodoTree(sortedByDeadlineTime(todaySectionTodos), byId, childCounts),
                    totalDueCount = todayOwnTodos.size,
                    completedDueCount = todayOwnTodos.count { it.state == TodoState.COMPLETE }
                )
            )
        }

        // Upcoming days, soonest first.
        bySectionDay.keys.filter { it > todayStart }.sorted().forEach { day ->
            val sectionTodos = sortedByDeadlineTime(bySectionDay[day]!!)
            val ownForDay = ownDueByDeadline[day] ?: emptyList()
            sections.add(TodoDaySection(day, buildTodoTree(sectionTodos, byId, childCounts), ownForDay.size, ownForDay.count { it.state == TodoState.COMPLETE }))
        }

        // Past days, most recent first -- overdue-and-incomplete todos (and any deadline-less
        // children following them) already resolved to Today above via effectiveSectionDay, so
        // whatever's left here is exactly what's since been completed.
        bySectionDay.keys.filter { it < todayStart }.sortedDescending().forEach { day ->
            val sectionTodos = sortedByDeadlineTime(bySectionDay[day]!!)
            val ownForDay = ownDueByDeadline[day] ?: emptyList()
            sections.add(TodoDaySection(day, buildTodoTree(sectionTodos, byId, childCounts), ownForDay.size, ownForDay.count { it.state == TodoState.COMPLETE }))
        }

        return sections
    }
}
