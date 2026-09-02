package com.inventoria.app.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.ScheduleBlockRepository
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TaskTypeRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.deletedRowPurgeThreshold
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.components.UndoableDeleteController
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/** A tracked task clipped to one calendar day, in minutes since that day's midnight. [endMinute]
 * is null while the task is still running -- the screen draws it to the current minute so a live
 * session grows without waiting for a database emission. */
data class DayTaskSegment(val task: Task, val startMinute: Float, val endMinute: Float?)

/** Everything the day timeline shows for one day. [blocks] includes weekly repeats that land on
 * this weekday; [timedTodos] are due at a time of day, [allDayTodos] just on the day. */
data class ScheduleDay(
    val dayStart: Long,
    val blocks: List<ScheduleBlock>,
    val timedTodos: List<Todo>,
    val allDayTodos: List<Todo>,
    val tasks: List<DayTaskSegment>
)

/** One cell of the week strip: the day, and which of the three things it has any of. */
data class WeekDayMarker(
    val dayStart: Long,
    val hasBlocks: Boolean,
    val hasTodos: Boolean,
    val hasTasks: Boolean
)

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** [day] shifted by [days] calendar days, DST-safe (a plain add of 24h drifts across a change). */
fun plusDays(day: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = day
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis.let { getStartOfDay(it) }

/** Start of the locale's week containing [day]. */
fun weekStartOf(day: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = day }
    val first = cal.firstDayOfWeek
    while (cal.get(Calendar.DAY_OF_WEEK) != first) cal.add(Calendar.DAY_OF_YEAR, -1)
    return getStartOfDay(cal.timeInMillis)
}

/**
 * The Schedule segment's state: which day is showing, what is on it, and the block being edited.
 *
 * Blocks are *designated* time and tasks are *used* time; this class only lines them up on the
 * same day so the screen can draw them side by side. It never turns one into the other.
 *
 * Todos come along read-mostly. Editing a todo lives on the Todos segment (the same reasoning as
 * TodayScreen's KDoc: the dialog belongs to the ViewModel that renders it), so the one thing this
 * offers for a todo is ticking it off.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleBlockRepository: ScheduleBlockRepository,
    private val todoRepository: TodoRepository,
    taskRepository: TaskRepository,
    taskTypeRepository: TaskTypeRepository
) : ViewModel() {

    /** For the block dialog's type picker and the type label on blocks -- the same visible-only
     * list TodoViewModel exposes, so a soft-deleted type reads as unset here too. */
    val taskTypes: StateFlow<List<TaskType>> = taskTypeRepository.getVisibleTaskTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskTypeNamesById: StateFlow<Map<String, String>> = taskTypes
        .map { types -> types.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedDay = MutableStateFlow(getStartOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    private val _weekStart = MutableStateFlow(weekStartOf(_selectedDay.value))
    val weekStart: StateFlow<Long> = _weekStart.asStateFlow()

    private val blocks: StateFlow<List<ScheduleBlock>> = scheduleBlockRepository.getVisibleBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tasks: StateFlow<List<Task>> = taskRepository.getVisibleTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val day: StateFlow<ScheduleDay> =
        combine(selectedDay, blocks, todos, tasks) { day, blockList, todoList, taskList ->
            buildDay(day, blockList, todoList, taskList)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ScheduleDay(_selectedDay.value, emptyList(), emptyList(), emptyList(), emptyList())
            )

    val weekDays: StateFlow<List<WeekDayMarker>> =
        combine(weekStart, blocks, todos, tasks) { start, blockList, todoList, taskList ->
            (0 until 7).map { offset ->
                val dayStart = plusDays(start, offset)
                WeekDayMarker(
                    dayStart = dayStart,
                    hasBlocks = blockList.any { it.occursOn(dayStart) },
                    hasTodos = todoList.any { it.deadline == dayStart && it.state != TodoState.COMPLETE },
                    hasTasks = taskList.any { overlapsDay(it, dayStart) }
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDay(dayStart: Long) {
        _selectedDay.value = dayStart
        val week = weekStartOf(dayStart)
        if (_weekStart.value != week) _weekStart.value = week
    }

    /** Moves the strip a week without moving the selection -- the selected day may scroll out of
     * view, which is what a week browser does; tapping a cell brings the selection along. */
    fun shiftWeek(weeks: Int) {
        _weekStart.value = plusDays(_weekStart.value, weeks * 7)
    }

    fun goToToday() = selectDay(getStartOfDay(System.currentTimeMillis()))

    // ---- Block editing --------------------------------------------------------------------

    private val undoController = UndoableDeleteController()

    /** Emits the title of a just-deleted block, for the screen's "Undo" snackbar. */
    val undoPrompts: SharedFlow<String> = undoController.prompts

    /** A block being created (id blank) or edited (id set); null when no dialog is open. */
    private val _pendingBlock = MutableStateFlow<ScheduleBlock?>(null)
    val pendingBlock: StateFlow<ScheduleBlock?> = _pendingBlock.asStateFlow()

    /** Opens the dialog for a new block on the selected day, starting at [startMinute] (snapped to
     * the hour by the caller) and lasting an hour, capped at midnight. */
    fun startAddingBlock(startMinute: Int = 9 * 60) {
        val start = startMinute.coerceIn(0, 23 * 60)
        _pendingBlock.value = ScheduleBlock(
            id = "",
            dayStart = _selectedDay.value,
            startMinuteOfDay = start,
            endMinuteOfDay = minOf(start + 60, 24 * 60)
        )
    }

    fun startEditingBlock(block: ScheduleBlock) {
        _pendingBlock.value = block
    }

    fun dismissDialog() {
        _pendingBlock.value = null
    }

    fun saveBlock(
        title: String,
        kind: TaskKind,
        taskTypeId: String?,
        dayStart: Long,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        repeatWeekly: Boolean,
        notes: String
    ) {
        val pending = _pendingBlock.value ?: return
        val trimmed = title.trim()
        if (trimmed.isBlank() || endMinuteOfDay <= startMinuteOfDay) return
        val block = pending.copy(
            id = pending.id.ifBlank { UUID.randomUUID().toString() },
            title = trimmed,
            kind = kind,
            taskTypeId = taskTypeId,
            dayStart = dayStart,
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            repeatWeekly = repeatWeekly,
            notes = notes.trim()
        )
        viewModelScope.launch {
            if (pending.id.isBlank()) scheduleBlockRepository.insertBlock(block)
            else scheduleBlockRepository.updateBlock(block)
        }
        _pendingBlock.value = null
    }

    /** Soft-deletes a block and offers it straight back, same as the Todos screen. */
    fun deleteBlock(block: ScheduleBlock) {
        _pendingBlock.value = null
        viewModelScope.launch {
            scheduleBlockRepository.softDeleteBlock(block.id)
            undoController.offer(block.title.ifBlank { "block" }) {
                scheduleBlockRepository.restoreBlock(block.id)
            }
        }
    }

    fun undoLastDelete() {
        viewModelScope.launch { undoController.undo() }
    }

    /** The one todo action this segment offers -- see the class KDoc. */
    fun toggleTodoComplete(todo: Todo) {
        viewModelScope.launch {
            todoRepository.setStateWithCascade(todo.id, complete = todo.state != TodoState.COMPLETE)
        }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                scheduleBlockRepository.purgeOldDeletedBlocks(deletedRowPurgeThreshold())
                delay(60_000)
            }
        }
    }

    // ---- Assembly -------------------------------------------------------------------------

    private fun overlapsDay(task: Task, dayStart: Long): Boolean {
        val end = task.endTime ?: System.currentTimeMillis()
        return task.startTime < dayStart + DAY_MILLIS && end > dayStart
    }

    private fun buildDay(
        dayStart: Long,
        blockList: List<ScheduleBlock>,
        todoList: List<Todo>,
        taskList: List<Task>
    ): ScheduleDay {
        val dayEnd = dayStart + DAY_MILLIS
        val dueToday = todoList.filter { it.deadline == dayStart }
        return ScheduleDay(
            dayStart = dayStart,
            blocks = blockList.filter { it.occursOn(dayStart) }.sortedBy { it.startMinuteOfDay },
            timedTodos = dueToday.filter { it.deadlineMinuteOfDay != null }.sortedBy { it.deadlineMinuteOfDay },
            allDayTodos = dueToday.filter { it.deadlineMinuteOfDay == null },
            tasks = taskList.filter { overlapsDay(it, dayStart) }.map { task ->
                val start = maxOf(task.startTime, dayStart)
                val end = task.endTime?.let { minOf(it, dayEnd) }
                DayTaskSegment(
                    task = task,
                    startMinute = (start - dayStart) / 60_000f,
                    // A running task that started before today is still "running" on today's view;
                    // one that is running but shown on an earlier day ends at that day's midnight.
                    endMinute = when {
                        end != null -> (end - dayStart) / 60_000f
                        dayEnd <= System.currentTimeMillis() -> 24 * 60f
                        else -> null
                    }
                )
            }.sortedBy { it.startMinute }
        )
    }
}
