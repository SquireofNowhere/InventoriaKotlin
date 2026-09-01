package com.inventoria.app.ui.screens.todo

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.deletedRowPurgeThreshold
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
import com.inventoria.app.ui.components.UndoableDeleteController
import com.inventoria.app.ui.screens.task.TaskTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

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

    /** Whether the Todos screen is currently hiding finished work. On by default. */
    val hideCompleted: StateFlow<Boolean> = settingsRepository.isTodoHideCompletedEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Todos whose sub-todos are folded away on the Todos screen. */
    val collapsedTodoIds: StateFlow<Set<String>> = settingsRepository.getCollapsedTodoIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val undatedTodoEntries: StateFlow<List<TodoTreeEntry>> =
        combine(todos, hideCompleted, collapsedTodoIds) { list, hide, collapsed ->
            TodoSections.undated(list, hide, collapsed)
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
        .map { list -> TodoSections.build(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** [todoSections] with this screen's hide/collapse preferences applied. */
    val plannerSections: StateFlow<List<TodoDaySection>> =
        combine(todos, hideCompleted, collapsedTodoIds) { list, hide, collapsed ->
            TodoSections.build(list, hide, collapsed)
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

    private val undoController = UndoableDeleteController()

    /** Emits the label of a just-deleted todo, for the screen's "Undo" snackbar. */
    val undoPrompts: SharedFlow<String> = undoController.prompts

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
                todoRepository.purgeOldDeletedTodos(deletedRowPurgeThreshold())
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

    fun addTodo(
        title: String,
        kind: TaskKind,
        taskTypeId: String?,
        deadline: Long?,
        deadlineMinuteOfDay: Int?,
        reminderOffsetMinutes: Int?,
        parentTodoId: String?,
        priority: TodoPriority?
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val time = deadlineMinuteOfDay.takeIf { deadline != null }
        val reminder = reminderOffsetMinutes.takeIf { deadline != null }
        viewModelScope.launch {
            todoRepository.insertTodo(
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = trimmed,
                    kind = kind,
                    taskTypeId = taskTypeId,
                    deadline = deadline,
                    deadlineMinuteOfDay = time,
                    reminderOffsetMinutes = reminder,
                    parentTodoId = parentTodoId,
                    priority = priority
                )
            )
        }
        _isAddingNew.value = false
    }

    fun saveEditedTodo(
        todo: Todo,
        title: String,
        kind: TaskKind,
        taskTypeId: String?,
        deadline: Long?,
        deadlineMinuteOfDay: Int?,
        reminderOffsetMinutes: Int?,
        parentTodoId: String?,
        priority: TodoPriority?
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        // A time without a date would be unreachable in the UI and would sort/display as a due
        // time nothing is actually due at, so the "null deadline clears the time" invariant is
        // enforced here rather than trusted from the dialog. The alarm follows the same rule: an
        // alarm with nothing to ring for is cleared, not carried around waiting for a date.
        val time = deadlineMinuteOfDay.takeIf { deadline != null }
        val reminder = reminderOffsetMinutes.takeIf { deadline != null }
        viewModelScope.launch {
            todoRepository.updateTodo(
                todo.copy(
                    title = trimmed,
                    kind = kind,
                    taskTypeId = taskTypeId,
                    deadline = deadline,
                    deadlineMinuteOfDay = time,
                    reminderOffsetMinutes = reminder,
                    parentTodoId = parentTodoId,
                    priority = priority
                )
            )
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

    /**
     * Soft-deletes a todo and offers it straight back.
     *
     * Descendants are deliberately left alone -- softDeleteTodo only tombstones this row, so a
     * parent's children survive it and reparent themselves visually via the existing
     * "parent outside the scope" path in buildTodoTree. Undo therefore only has this one row to
     * put back.
     */
    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            todoRepository.softDeleteTodo(todo.id)
            undoController.offer(todo.title.ifBlank { "todo" }) {
                todoRepository.restoreTodo(todo.id)
            }
        }
    }

    fun undoLastDelete() {
        viewModelScope.launch { undoController.undo() }
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
}
