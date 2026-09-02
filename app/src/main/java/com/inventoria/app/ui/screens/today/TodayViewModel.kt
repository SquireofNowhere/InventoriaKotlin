package com.inventoria.app.ui.screens.today

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.ScheduleBlockRepository
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.model.modalTypeIdFor
import com.inventoria.app.data.model.reminderTriggerAt
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.screens.task.TaskTimerService
import com.inventoria.app.util.currentMinuteOfDay
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * What the top of Today should say right now, in priority order: something is running; something
 * is paused; nothing is running but the schedule has designated this hour; nothing at all.
 */
sealed interface NowState {
    /** Running segments, newest first. */
    data class Running(val sessions: List<Task>) : NowState

    /** One segment per paused session (its latest), none running. */
    data class Paused(val sessions: List<Task>) : NowState

    /** Idle, and a schedule block covers this minute. */
    data class Planned(val block: ScheduleBlock) : NowState

    /** Idle with nothing planned now; [nextBlock] is the next block later today, if any. */
    data class Idle(val nextBlock: ScheduleBlock?) : NowState
}

/** One row of the Up Next card: a schedule block starting later today, or a todo due at a time
 * later today. Ordered by [minuteOfDay]. */
sealed interface UpNextItem {
    val minuteOfDay: Int

    data class Block(val block: ScheduleBlock) : UpNextItem {
        override val minuteOfDay: Int get() = block.startMinuteOfDay
    }

    data class Due(val todo: Todo) : UpNextItem {
        override val minuteOfDay: Int get() = todo.deadlineMinuteOfDay ?: 0
    }
}

/**
 * What the red banner at the top of Today has to say. Only built when at least one field is
 * non-empty; the screen shows nothing otherwise.
 *
 * [dueSoon] is the rent case: incomplete todos whose alarm will ring, or whose due time falls,
 * within the next hour. The banner exists so that a deadline you are about to miss is on the
 * home screen before the alarm, not only in it.
 */
data class Nudge(
    val overdueCount: Int,
    val lateTodayCount: Int,
    val dueSoon: List<Todo>
)

private const val SOON_MINUTES = 60

/**
 * Everything the Today screen needs that isn't a todo: the task list behind the 24-hour timeline
 * and the kind breakdown, the Now card's state, the top bar's refresh, the focus preference that
 * orders the screen, and -- under Inventory focus only -- the three numbers on the summary card.
 *
 * The inventory flows are cheap COUNT/SUM queries (the same ones InventoryHubViewModel serves)
 * and WhileSubscribed, so they only run while the Inventory branch of the screen is actually
 * collecting them. That keeps faith with why this class replaced the old inventory dashboard
 * ViewModel: that one resolved a display location for every item in the database to reach the
 * single task list Today wanted. Nothing like that belongs on the start destination.
 *
 * Deliberately does NOT touch TaskTrackerViewModel: that binds TaskTimerService in its init and
 * renders the post-session check-in dialog from TaskTrackerScreen only. Pulling it in here would
 * open a second service binding and orphan any check-in it triggered. That is also why the Now
 * card can *start* a task (a plain insert, the same thing TodoViewModel.startTaskFromTodo does)
 * but never pauses or stops one -- stopping runs the interruption-chain, flow-mode and todo
 * check-in logic that lives on the tracker, so the card sends you there for that.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    scheduleBlockRepository: ScheduleBlockRepository,
    private val todoRepository: TodoRepository,
    inventoryRepository: InventoryRepository,
    collectionRepository: CollectionRepository,
    settingsRepository: SettingsRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    /** Feeds LinearProductivityChart and KindBreakdownDonut, which filter to the current day. */
    val tasks: StateFlow<List<Task>> = taskRepository.getVisibleTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val blocks: StateFlow<List<ScheduleBlock>> = scheduleBlockRepository.getVisibleBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Re-evaluates the Now card once a minute so a block starting or ending flips it on its own. */
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val nowState: StateFlow<NowState> = combine(tasks, blocks, minuteTicker) { taskList, blockList, _ ->
        computeNow(taskList, blockList)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowState.Idle(null))

    // Read here as well as in TodoViewModel (which owns the list): this screen's Up Next and
    // nudge only need the raw rows, and sharing the other ViewModel's instance is exactly what
    // TodayScreen's KDoc rules out.
    private val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The next few things on today's clock after this minute, soonest first, at most three. */
    val upNext: StateFlow<List<UpNextItem>> = combine(blocks, todos, minuteTicker) { blockList, todoList, _ ->
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val minute = currentMinuteOfDay()
        val laterBlocks = blockList
            .filter { it.occursOn(todayStart) && it.startMinuteOfDay > minute }
            .map { UpNextItem.Block(it) }
        val laterTodos = todoList
            .filter {
                it.state != TodoState.COMPLETE && it.deadline == todayStart &&
                    (it.deadlineMinuteOfDay ?: -1) > minute
            }
            .map { UpNextItem.Due(it) }
        (laterBlocks + laterTodos).sortedBy { it.minuteOfDay }.take(3)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Null when there is nothing to nag about, which should be most of the time. */
    val nudge: StateFlow<Nudge?> = combine(todos, minuteTicker) { todoList, _ ->
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)
        val minute = currentMinuteOfDay()
        val open = todoList.filter { it.state != TodoState.COMPLETE }
        val overdue = open.count { (it.deadline ?: Long.MAX_VALUE) < todayStart }
        val lateToday = open.count {
            it.deadline == todayStart && it.deadlineMinuteOfDay != null && it.deadlineMinuteOfDay!! < minute
        }
        val soonEnd = now + SOON_MINUTES * 60_000L
        val dueSoon = open.filter { todo ->
            val ringsSoon = todo.reminderTriggerAt()?.let { it > now && it <= soonEnd } == true
            val dueTime = todo.deadlineMinuteOfDay
            val dueSoonToday = todo.deadline == todayStart && dueTime != null &&
                dueTime > minute && dueTime <= minute + SOON_MINUTES
            ringsSoon || dueSoonToday
        }.sortedBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }
        if (overdue == 0 && lateToday == 0 && dueSoon.isEmpty()) null
        else Nudge(overdue, lateToday, dueSoon)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun computeNow(taskList: List<Task>, blockList: List<ScheduleBlock>): NowState {
        val running = taskList.filter { it.isRunning }.sortedByDescending { it.startTime }
        if (running.isNotEmpty()) return NowState.Running(running)

        // A paused session still has isSessionActive segments; show its most recent one.
        val paused = taskList.filter { it.isSessionActive && !it.isRunning }
            .groupBy { it.groupId }
            .map { (_, segments) -> segments.maxBy { it.startTime } }
            .sortedByDescending { it.startTime }
        if (paused.isNotEmpty()) return NowState.Paused(paused)

        val todayStart = getStartOfDay(System.currentTimeMillis())
        val minute = currentMinuteOfDay()
        val today = blockList.filter { it.occursOn(todayStart) }.sortedBy { it.startMinuteOfDay }
        val current = today.firstOrNull { it.startMinuteOfDay <= minute && minute < it.endMinuteOfDay }
        if (current != null) return NowState.Planned(current)
        return NowState.Idle(today.firstOrNull { it.startMinuteOfDay > minute })
    }

    val focusArea: StateFlow<FocusArea> = settingsRepository.getFocusArea()
        .map { FocusArea.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusArea.DEFAULT)

    val totalItems: StateFlow<Int> = inventoryRepository.getItemCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalValue: StateFlow<Double> = inventoryRepository.getTotalValue()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // No dedicated count query exists and the collection list is small; counting the list keeps
    // CollectionRepository's surface as-is.
    val collectionCount: StateFlow<Int> = collectionRepository.getAllCollections()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val showTotalValue: StateFlow<Boolean> = settingsRepository.getShowValueOnDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun refresh() {
        syncRepository.triggerFullSync()
    }

    /**
     * "Start this": a tracked session named after the block, in the block's kind and type. Same
     * shape as TodoViewModel.startTaskFromTodo minus the todo linkage -- a block is a plan, not a
     * todo, so there is nothing to check in on when the session stops. The type is what makes a
     * planned hour and the hour actually spent land under the same activity.
     */
    fun startTaskFromBlock(block: ScheduleBlock) {
        startSession(block.title, block.kind, block.taskTypeId)
    }

    /**
     * Quick capture, the todo half: a todo titled [title], due today, all-day, with the same
     * default alarm a todo created in the dialog gets. Kind and priority stay at their defaults;
     * this is for getting the thought down, the row can be opened and filled in later.
     */
    fun addQuickTodo(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            todoRepository.insertTodo(
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = trimmed,
                    deadline = getStartOfDay(System.currentTimeMillis()),
                    reminderOffsetMinutes = 0
                )
            )
        }
    }

    /** Quick capture, the task half: start tracking [title] right now, untyped and in the
     * neutral kind (the tracker's own new-task default); the name's learned type still applies. */
    fun startQuickTask(title: String) {
        startSession(title, TaskKind.GRAPHITE)
    }

    /** [taskTypeId] wins when given (a typed block); otherwise the name's learned type applies. */
    private fun startSession(title: String, kind: TaskKind, taskTypeId: String? = null) {
        viewModelScope.launch {
            val name = title.trim().ifBlank { "Task" }
            // Checked against the table rather than the derived flow, so a double tap before the
            // next emission can't open two sessions with one name.
            val alreadyRunning = taskRepository.getVisibleTasksList()
                .any { it.isRunning && it.name.trim().equals(name, ignoreCase = true) }
            if (alreadyRunning) return@launch
            val task = Task(
                id = UUID.randomUUID().toString(),
                groupId = UUID.randomUUID().toString(),
                name = name,
                kind = kind,
                taskTypeId = taskTypeId ?: learnedTypeIdForName(name),
                isRunning = true,
                startTime = System.currentTimeMillis()
            )
            taskRepository.insertTask(task)
            val intent = Intent(context, TaskTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            syncRepository.triggerFullSync()
        }
    }

    /** Whatever type tasks named [name] have settled on -- the tracker's autofill rule, so a
     * session started from a block lands typed the same way typing the name would. */
    private suspend fun learnedTypeIdForName(name: String): String? {
        val target = name.trim().lowercase()
        if (target.isBlank()) return null
        val sameName = taskRepository.getVisibleTasksList().filter { it.name.trim().lowercase() == target }
        return if (sameName.isEmpty()) null else modalTypeIdFor(sameName)
    }
}
