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
import com.inventoria.app.data.repository.SettingsRepository
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
 * todo itself. [hasVisibleChildren] is whether this entry would actually nest anything *in this
 * list* -- not whether the todo has children at all, since a child filed under a different day
 * cannot be folded away from here -- and [isCollapsed] is that fold being applied, which is why a
 * collapsed entry still reports [childProgress] (the only thing left saying what is hidden). */
data class TodoTreeEntry(
    val todo: Todo,
    val depth: Int,
    val parentName: String?,
    val childProgress: Pair<Int, Int>?,
    val effectiveState: TodoState,
    val hasVisibleChildren: Boolean = false,
    val isCollapsed: Boolean = false
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
    private val syncRepository: FirebaseSyncRepository,
    private val settingsRepository: SettingsRepository
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

    /** Which todos currently have a tracked session running (or paused, but not yet stopped).
     *
     * Derived from the tasks themselves rather than read off a Todo.activeSessionGroupId written
     * at start and cleared at stop. That stored pointer had no way to be right in every case: it
     * survived a session being *deleted* rather than stopped, stranding the todo as permanently
     * "in progress" with no Start button, and it made two separately-synced entities responsible
     * for one fact, each converging last-write-wins on its own. The task rows are the fact; a
     * deleted session simply stops matching. */
    val todoIdsWithActiveSession: StateFlow<Set<String>> = taskRepository.getVisibleTasks()
        .map { tasks ->
            tasks.filter { it.isSessionActive }.mapNotNull { it.originTodoId }.toSet()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Whether the Todos screen is currently hiding finished work. Off by default. */
    val hideCompleted: StateFlow<Boolean> = settingsRepository.isTodoHideCompletedEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Todos whose sub-todos are folded away on the Todos screen. */
    val collapsedTodoIds: StateFlow<Set<String>> = settingsRepository.getCollapsedTodoIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val undatedTodoEntries: StateFlow<List<TodoTreeEntry>> =
        combine(todos, hideCompleted, collapsedTodoIds) { list, hide, collapsed ->
            val byId = list.associateBy { it.id }
            val childCounts = computeChildCounts(list)
            val todayStart = getStartOfDay(System.currentTimeMillis())
            val undated = (if (hide) withoutCompleted(list, byId) else list)
                .filter { effectiveSectionDay(it, byId, todayStart) == null }
            buildTodoTree(undated, byId, childCounts, collapsed)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The unfiltered, unfolded sections -- the Today screen's source.
     *
     * Hiding and collapsing are Todos-screen view state, and Today is a different screen with a
     * toggle the user cannot see from there; silently emptying the home screen off a control on
     * another tab would be a surprise. Today reads this, the Todos screen reads
     * [plannerSections].
     */
    val todoSections: StateFlow<List<TodoDaySection>> = todos
        .map { list -> buildTodoSections(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** [todoSections] with this screen's hide/collapse preferences applied. */
    val plannerSections: StateFlow<List<TodoDaySection>> =
        combine(todos, hideCompleted, collapsedTodoIds) { list, hide, collapsed ->
            buildTodoSections(list, hide, collapsed)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleHideCompleted() {
        viewModelScope.launch { settingsRepository.setTodoHideCompleted(!hideCompleted.value) }
    }

    /**
     * Folds a todo's sub-todos away, or unfolds them.
     *
     * The id is kept even once the todo stops having visible children (it may regain some, and a
     * fold the user set should not silently reset); ids of deleted todos are pruned on the next
     * toggle rather than watched for, since a stale id in this set does nothing at all.
     */
    fun toggleCollapsed(todoId: String) {
        viewModelScope.launch {
            val live = todos.value.mapTo(mutableSetOf()) { it.id }
            val current = collapsedTodoIds.value
            val next = if (todoId in current) current - todoId else current + todoId
            // Guarded: todos is WhileSubscribed, and pruning against a list that has not emitted
            // yet would clear every fold the user has set rather than the dead ids it is after.
            settingsRepository.saveCollapsedTodoIds(if (live.isEmpty()) next else next.intersect(live))
        }
    }

    fun expandAll() {
        viewModelScope.launch { settingsRepository.saveCollapsedTodoIds(emptySet()) }
    }

    /** Every todo that currently parents something, folded in one go. */
    fun collapseAll() {
        viewModelScope.launch {
            val parents = todos.value.mapNotNull { it.parentTodoId }.toSet()
            settingsRepository.saveCollapsedTodoIds(parents)
        }
    }

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
     * Read once here rather than off [todoIdsWithActiveSession]'s upstream: this is needed at the
     * instant a task is started, and a one-shot read is right whether or not anything is currently
     * subscribed to that flow. */
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
        viewModelScope.launch {
            // Checked against the table rather than the (subscription-dependent, one-emission-
            // behind) derived flow, so a double tap before the first emission can't open a second
            // session for the same todo.
            val alreadyRunning = taskRepository.getVisibleTasksList()
                .any { it.originTodoId == todo.id && it.isSessionActive }
            if (alreadyRunning) return@launch
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
        childCounts: Map<String, Pair<Int, Int>>,
        collapsedIds: Set<String> = emptySet()
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
            // Children *in this list*, which is not the same as children at all: one filed under a
            // different day is not something this row can fold away, so it must not offer to.
            val children = childrenByParentId[todo.id].orEmpty()
            val collapsed = children.isNotEmpty() && todo.id in collapsedIds
            result.add(
                TodoTreeEntry(todo, depth, parentName, progress, effectiveState, children.isNotEmpty(), collapsed)
            )
            if (!collapsed) children.forEach { child -> visit(child, depth + 1) }
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
     * only reached when nothing in the whole ancestor chain has a deadline at all).
     *
     * A *completed* sub-todo also defers to its parent, even when it has a deadline of its own.
     * Ticking one off otherwise tore it out of the group it belonged to and refiled it alone under
     * its own date, which is precisely the moment the hierarchy is most worth keeping intact: the
     * parent's "2/3 sub-todos complete" line pointed at rows that had scattered across the list.
     * Only display moves -- the day header's own X% Done still counts it against the day it was
     * genuinely due, which is why [buildTodoSections] keys those counts off `deadline` directly. */
    private fun effectiveSectionDay(todo: Todo, byId: Map<String, Todo>, todayStart: Long): Long? {
        var current: Todo? = todo
        // parentTodoId cycles are prevented when parents are assigned (see invalidParentIds), but a
        // cycle arriving from sync has no such gate and would spin here forever.
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.id)) {
            val parent = current.parentTodoId?.let { byId[it] }
            val defersToParent = current.state == TodoState.COMPLETE && parent != null
            val deadline = current.deadline
            if (deadline != null && !defersToParent) {
                return if (current.state != TodoState.COMPLETE && deadline < todayStart) todayStart else deadline
            }
            current = parent
        }
        return null
    }

    /**
     * Drops completed todos, keeping any that still have unfinished work hanging off them.
     *
     * A completed parent is not noise while one of its children is outstanding -- it is the thing
     * that child is nested under, and removing it would strand the child at the top level looking
     * unrelated to anything. So the rule is "keep every incomplete todo, and every ancestor of
     * one", rather than a flat state filter.
     */
    private fun withoutCompleted(all: List<Todo>, byId: Map<String, Todo>): List<Todo> {
        val keep = mutableSetOf<String>()
        all.filter { it.state != TodoState.COMPLETE }.forEach { todo ->
            var current: Todo? = todo
            // add() returning false means this ancestor chain has already been walked -- which also
            // stops a synced parent cycle from looping.
            while (current != null && keep.add(current.id)) {
                current = current.parentTodoId?.let { byId[it] }
            }
        }
        return all.filter { it.id in keep }
    }

    /** Within a day, todos carrying a deadline time come first in chronological order, all-day
     * ones after them. sortedBy is stable, so everything untimed keeps the DAO's createdAt DESC
     * order untouched, and so do timed todos sharing a minute. */
    private fun sortedByDeadlineTime(todos: List<Todo>): List<Todo> =
        todos.sortedBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }

    /**
     * [hideCompleted] and [collapsedIds] only ever remove *rows*. Everything a header counts --
     * childCounts, ownDueByDeadline, and the ancestor walk in effectiveSectionDay -- is computed
     * over the full list, so folding a branch away or hiding finished work never makes a day's
     * "X% Done" or a parent's "2/3 sub-todos complete" quietly disagree with reality.
     */
    private fun buildTodoSections(
        all: List<Todo>,
        hideCompleted: Boolean = false,
        collapsedIds: Set<String> = emptySet()
    ): List<TodoDaySection> {
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val byId = all.associateBy { it.id }
        val childCounts = computeChildCounts(all)

        // "Due" stats (the day header's X% Done bar) are strictly about todos with their OWN
        // deadline -- a deadline-less child inheriting a section from its parent doesn't make it
        // "due" that day for percentage purposes.
        val ownDueByDeadline = all.filter { it.deadline != null }.groupBy { it.deadline!! }

        val sectionDayById = all.associate { it.id to effectiveSectionDay(it, byId, todayStart) }
        val rendered = if (hideCompleted) withoutCompleted(all, byId) else all
        val bySectionDay = rendered.filter { sectionDayById[it.id] != null }.groupBy { sectionDayById[it.id]!! }

        fun sectionFor(day: Long): TodoDaySection {
            val ownForDay = ownDueByDeadline[day] ?: emptyList()
            return TodoDaySection(
                dayStart = day,
                visibleTodos = buildTodoTree(sortedByDeadlineTime(bySectionDay[day]!!), byId, childCounts, collapsedIds),
                totalDueCount = ownForDay.size,
                completedDueCount = ownForDay.count { it.state == TodoState.COMPLETE }
            )
        }

        val sections = mutableListOf<TodoDaySection>()

        if (bySectionDay[todayStart]?.isNotEmpty() == true) sections.add(sectionFor(todayStart))

        // Upcoming days, soonest first.
        bySectionDay.keys.filter { it > todayStart }.sorted().forEach { sections.add(sectionFor(it)) }

        // Past days, most recent first -- overdue-and-incomplete todos (and any deadline-less
        // children following them) already resolved to Today above via effectiveSectionDay, so
        // whatever's left here is exactly what's since been completed.
        bySectionDay.keys.filter { it < todayStart }.sortedDescending().forEach { sections.add(sectionFor(it)) }

        return sections
    }
}
