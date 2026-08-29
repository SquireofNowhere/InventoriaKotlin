package com.inventoria.app.ui.screens.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.deletedRowPurgeThreshold
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats
import com.inventoria.app.data.model.computeTaskTypeStats
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.repository.CalendarRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.components.UndoableDeleteController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Every term that goes into one category's score for today, kept separate instead of pre-summed so
 * the Productivity screen can show the arithmetic rather than just its result.
 *
 * [dampeningAbsorbed] is the points the diminishing curve swallowed -- the gap between what the
 * day's time-tracking earned on paper and what it was allowed to contribute. Note this is a
 * *today* concept only: lifetime totals are the plain historical sum and are never dampened.
 */
data class CategoryScoreBreakdown(
    val category: TaskCategory,
    val trackedTaskCount: Int,
    val rawTracked: Int,
    val dampenedTracked: Int,
    val todoPoints: Int,
    val overduePenalty: Int,
    val todoProcrastinationPenalty: Int,
    val taskProcrastinationPenalty: Int
) {
    val dampeningAbsorbed: Int get() = rawTracked - dampenedTracked
    val total: Int get() =
        dampenedTracked + todoPoints - overduePenalty - todoProcrastinationPenalty - taskProcrastinationPenalty
}

/**
 * What makes two sittings "the same thing": the name and the type, with the Kind deliberately
 * excluded -- lunch at home and lunch out are one activity scored differently, and forcing the
 * Kind to match would split them apart again (which is what the old merge-by-name did).
 *
 * Compared case-insensitively on a trimmed name, matching how the autofill's own per-name
 * calculations key themselves.
 */
data class ActivityKey(val name: String, val taskTypeId: String?)

fun activityKeyOf(task: Task): ActivityKey = ActivityKey(task.name.trim().lowercase(), task.taskTypeId)

/**
 * Several completed sittings of one activity, presented as a single card.
 *
 * This is the grouping that groupId used to provide by merging sessions together, rebuilt as a
 * read-time view: each sitting keeps its own groupId (so stop/pause/resume, deletion and the
 * streak counter still see distinct sessions), and only the display joins them. A session's key
 * comes from its first segment -- individual segments can be renamed one at a time from the task
 * detail dialog, and the session's own identity shouldn't shift under it when that happens.
 */
data class ActivityGroup(
    val key: ActivityKey,
    val displayName: String,
    /** One inner list per sitting, each already sorted newest segment first. */
    val sessions: List<List<Task>>
) {
    val segments: List<Task> get() = sessions.flatten().sortedByDescending { it.startTime }
    val sessionCount: Int get() = sessions.size
    val groupIds: List<String> get() = sessions.mapNotNull { it.firstOrNull()?.groupId }.distinct()
    val mostRecentStartTime: Long get() = sessions.maxOfOrNull { s -> s.maxOfOrNull { it.startTime } ?: 0L } ?: 0L
}

data class TaskSessionUI(
    val groupId: String,
    val segments: List<Task>,
    val isExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val activeSegment: RunningTaskUI? = null
)

data class RunningTaskUI(
    val task: Task,
    val elapsedTime: MutableStateFlow<Long> = MutableStateFlow(0L),
    var timerJob: Job? = null
)

/** Bundles the five procrastination-penalty settings into one value so categoryScoreToday only
 * needs a single extra parameter. Parsed from raw DataStore primitives here (rather than in
 * SettingsRepository) to match this codebase's existing convention of keeping the repository
 * dealing in primitives and letting the consuming ViewModel own the typed parsing (see
 * getInventorySortOption returning a raw String, not an enum). */
data class ProcrastinationSettings(
    val todoEnabled: Boolean,
    val todoCutoff: TodoPriority,
    val taskEnabled: Boolean,
    val taskKinds: Set<TaskKind>,
    val penaltyAmount: Int
)

@HiltViewModel
class TaskTrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val taskTypeRepository: com.inventoria.app.data.TaskTypeRepository,
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: com.inventoria.app.data.repository.FirebaseSyncRepository
) : ViewModel() {

    private val _activeSessions = MutableStateFlow<List<TaskSessionUI>>(emptyList())
    val activeSessions: StateFlow<List<TaskSessionUI>> = _activeSessions.asStateFlow()

    private val _completedSessions = MutableStateFlow<List<List<Task>>>(emptyList())
    val completedSessions: StateFlow<List<List<Task>>> = _completedSessions.asStateFlow()

    /** [completedSessions] rolled up by [ActivityKey], for the grouped view. Sessions keep their
     * own identity underneath; this only decides what shares a card. */
    val completedActivityGroups: StateFlow<List<ActivityGroup>> = _completedSessions
        .map { sessions ->
            sessions
                .filter { it.isNotEmpty() }
                .groupBy { activityKeyOf(it.first()) }
                .map { (key, sameActivity) ->
                    ActivityGroup(
                        key = key,
                        displayName = sameActivity.first().first().name,
                        sessions = sameActivity.sortedByDescending { s -> s.maxOfOrNull { it.startTime } ?: 0L }
                    )
                }
                .sortedByDescending { it.mostRecentStartTime }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val undoController = UndoableDeleteController()

    /** Emits the label of a just-deleted segment or session, for the "Undo" snackbar. */
    val undoPrompts: SharedFlow<String> = undoController.prompts

    private val _calendarTrigger = MutableStateFlow(0)
    
    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds: StateFlow<Set<String>> = _selectedTaskIds.asStateFlow()

    val isFlowModeEnabled: StateFlow<Boolean> = settingsRepository.isFlowModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Eagerly, not WhileSubscribed: pauseResumeTask() reads these .value directly and they must
    // always be current even if no composable happens to be collecting them at that moment (see
    // the Flow Mode carry-over saga for why a subscriber-gated policy silently breaks this).
    val isInnerTaskEnabled: StateFlow<Boolean> = settingsRepository.isInnerTaskEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasSeenInnerTaskPrompt: StateFlow<Boolean> = settingsRepository.hasSeenInnerTaskPrompt()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isTaskHistoryFlatView: StateFlow<Boolean> = settingsRepository.isTaskHistoryFlatView()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTaskHistoryFlatView(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTaskHistoryFlatView(enabled) }
    }

    val isRecentSessionsFlatView: StateFlow<Boolean> = settingsRepository.isRecentSessionsFlatView()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setRecentSessionsFlatView(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecentSessionsFlatView(enabled) }
    }

    // Task History's flat mode: every individual completed segment, regardless of which session
    // it belongs to, ordered purely by when it happened rather than grouped under its session.
    val flatCompletedTasks: StateFlow<List<Task>> = _completedSessions.map { sessions ->
        sessions.flatten().sortedByDescending { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // groupId of the session that was just paused, awaiting a response to the first-time
    // "enable interruption tracking?" explanation dialog.
    private val _pendingInnerTaskPrompt = MutableStateFlow<String?>(null)
    val pendingInnerTaskPrompt: StateFlow<String?> = _pendingInnerTaskPrompt.asStateFlow()

    // The inner task, already created and running, awaiting an optional rename. It starts
    // immediately on pause rather than waiting for a name so the interruption is timed from
    // the moment it actually begins, not from whenever the user finishes typing.
    private val _pendingInnerTaskRename = MutableStateFlow<Task?>(null)
    val pendingInnerTaskRename: StateFlow<Task?> = _pendingInnerTaskRename.asStateFlow()

    private val _isAutoStartPending = MutableStateFlow(false)
    val isAutoStartPending: StateFlow<Boolean> = _isAutoStartPending.asStateFlow()

    // The Todo a just-stopped session was started from, awaiting "is this done, or still
    // ongoing?" (see stopTask()) -- null when there's nothing to ask about.
    private val _pendingTodoCompletionCheckIn = MutableStateFlow<Todo?>(null)
    val pendingTodoCompletionCheckIn: StateFlow<Todo?> = _pendingTodoCompletionCheckIn.asStateFlow()

    fun respondToTodoCompletionCheckIn(complete: Boolean) {
        val todo = _pendingTodoCompletionCheckIn.value ?: return
        _pendingTodoCompletionCheckIn.value = null
        if (complete) {
            viewModelScope.launch { todoRepository.setStateWithCascade(todo.id, true) }
        }
    }

    private var flowModeJob: Job? = null
    private var hasLoadedInitialTasks = false

    fun toggleFlowMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFlowModeEnabled(enabled)
            if (!enabled) {
                flowModeJob?.cancel()
                _isAutoStartPending.value = false
            }
        }
    }

    private var timerService: TaskTimerService? = null
    private var isBound = false
    private var taskCounter = 1

    // Every segment that has actually finished (isRunning = false), whether its parent session
    // is still active (paused, might resume later) or fully stopped. Metrics previously only
    // read from _completedSessions (fully-stopped sessions only), so an already-worked, already-
    // paused segment of a still-in-progress session silently didn't count toward today's/lifetime
    // totals until the whole session was eventually stopped -- sometimes hours or days later.
    val allFinishedTasks: StateFlow<List<Task>> = combine(_activeSessions, _completedSessions) { active, completed ->
        completed.flatten() + active.flatMap { it.segments }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskTypes: StateFlow<List<TaskType>> = taskTypeRepository.getVisibleTaskTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Type id -> display name, for the cards that only need to *label* a task's type rather than
     * work with the whole row. Built off the visible types, so a task pointing at a soft-deleted
     * type resolves to null and simply shows no type label -- the type no longer exists as far as
     * the user is concerned, and the id stays on the task in case it comes back. */
    val taskTypeNamesById: StateFlow<Map<String, String>> = taskTypes
        .map { types -> types.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ONE shared aggregate pass, deliberately not a stat-per-flow -- see computeTaskTypeStats.
    // Everything that needs per-type numbers (the autofill's kind prefill, the Task Types manager
    // rows, the By Type stats tab) reads this same map rather than re-scanning the task list.
    val taskTypeStats: StateFlow<Map<String, TaskTypeStats>> =
        combine(taskTypes, allFinishedTasks) { types, tasks ->
            computeTaskTypeStats(types, tasks.filter { !it.isDeleted })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Read only for scoring below -- the Todos screen itself owns the full Todo UI/StateFlow set
    // (TodoViewModel). Kept private since nothing outside scoring needs the raw list here.
    private val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // WhileSubscribed, not Eagerly, unlike isInnerTaskEnabled above -- this only ever flows
    // reactively into personalScoreToday/socialScoreToday's own combine() below, never read via
    // a synchronous .value snapshot the way pauseResumeTask() reads isInnerTaskEnabled, so there's
    // no staleness risk to guard against.
    private val procrastinationSettings: StateFlow<ProcrastinationSettings> = combine(
        settingsRepository.isProcrastinationTodoEnabled(),
        settingsRepository.getProcrastinationTodoCutoff(),
        settingsRepository.isProcrastinationTaskEnabled(),
        settingsRepository.getProcrastinationTaskKinds(),
        settingsRepository.getProcrastinationPenaltyAmount()
    ) { todoEnabled, cutoffName, taskEnabled, kindNames, penaltyAmount ->
        ProcrastinationSettings(
            todoEnabled = todoEnabled,
            todoCutoff = try { TodoPriority.valueOf(cutoffName) } catch (e: IllegalArgumentException) { TodoPriority.B1 },
            taskEnabled = taskEnabled,
            taskKinds = kindNames.mapNotNull { name -> try { TaskKind.valueOf(name) } catch (e: IllegalArgumentException) { null } }.toSet(),
            penaltyAmount = penaltyAmount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProcrastinationSettings(false, TodoPriority.B1, false, emptySet(), 2))

    /** Diminishing-returns squash toward +/-[ceiling]: approaches but never reaches it, so raw
     * effort differences between time-tracked sessions still show up (unlike a hard min/max cap)
     * while keeping their combined contribution from swamping the day the way an unbounded sum
     * could (a single long Peacock session could otherwise outscore everything else combined).
     * Symmetric across zero so draining kinds' negative scores get the same treatment rather than
     * blowing up unbounded in the other direction. Completed Todos bypass this entirely and keep
     * their full kind.productivityValue -- see categoryScoreToday below. */
    private fun dampen(raw: Int, ceiling: Double = 5.0, decayConstant: Double = 15.0): Int {
        val sign = if (raw < 0) -1.0 else 1.0
        return (sign * ceiling * (1 - exp(-abs(raw.toDouble()) / decayConstant))).roundToInt()
    }

    /** Exposes [dampen] for UI display -- e.g. showing "raw total -> dampened" context in the
     * Activity Breakdown's per-task calculation dropdown -- without letting the UI reimplement
     * the formula itself. */
    fun previewDampen(raw: Int): Int = dampen(raw)

    /** One category's (Personal or Social) contribution to today's score: dampened time-tracked
     * total, plus the full undamped value of every Todo of this category completed today, minus
     * an escalating penalty (capped at 5/todo/day) for every still-incomplete Todo of this
     * category that's overdue right now, minus the procrastination penalty (if enabled) for
     * every Todo completed today below the configured priority cutoff (or unset) and every
     * time-tracked task completed today whose Kind is flagged as procrastination. Both
     * procrastination penalties are derived live here, the same way overduePenalty already is,
     * rather than baked into a stored field -- so changing the settings later doesn't leave
     * already-completed items' contribution silently stale. */
    private fun categoryScoreToday(tasks: List<Task>, todoList: List<Todo>, category: TaskCategory, settings: ProcrastinationSettings): Int =
        categoryBreakdownToday(tasks, todoList, category, settings).total

    private fun categoryBreakdownToday(tasks: List<Task>, todoList: List<Todo>, category: TaskCategory, settings: ProcrastinationSettings): CategoryScoreBreakdown {
        val todayStart = getTodayStart()
        val todayEnd = todayStart + 86_400_000L
        // Overlap check, not a startTime filter -- a task starting before midnight and ending
        // after (e.g. 11:40 PM -> 12:10 AM) genuinely spans today, matching how the pie chart
        // and allTodayTasks already treat it. A startTime-only filter silently dropped that
        // task's whole score from BOTH days. endTime ?: Long.MAX_VALUE treats a still-running
        // task as unbounded, so it always overlaps "now" without needing a currentTime param.
        val rawTracked = tasks
            .filter { (it.endTime ?: Long.MAX_VALUE) > todayStart && it.startTime < todayEnd && it.kind.category == category }
            .sumOf { it.score }
        val todoPoints = todoList
            .filter { it.state == TodoState.COMPLETE && (it.completedAt ?: 0L) >= todayStart && it.kind.category == category }
            .sumOf { it.kind.productivityValue }
        val overduePenalty = todoList
            .filter { it.state != TodoState.COMPLETE && it.deadline != null && it.deadline!! < todayStart && it.kind.category == category }
            .sumOf { minOf(((todayStart - it.deadline!!) / 86_400_000L).toInt(), 5) }
        val todoProcrastinationPenalty = if (settings.todoEnabled) {
            todoList
                .filter {
                    it.state == TodoState.COMPLETE && (it.completedAt ?: 0L) >= todayStart && it.kind.category == category &&
                        (it.priority == null || it.priority!!.ordinal >= settings.todoCutoff.ordinal)
                }
                .sumOf { settings.penaltyAmount }
        } else 0
        val taskProcrastinationPenalty = if (settings.taskEnabled) {
            tasks
                .filter {
                    (it.endTime ?: Long.MAX_VALUE) > todayStart && it.startTime < todayEnd &&
                        it.kind.category == category && it.kind in settings.taskKinds
                }
                .sumOf { settings.penaltyAmount }
        } else 0
        return CategoryScoreBreakdown(
            category = category,
            trackedTaskCount = tasks.count {
                (it.endTime ?: Long.MAX_VALUE) > todayStart && it.startTime < todayEnd && it.kind.category == category
            },
            rawTracked = rawTracked,
            dampenedTracked = dampen(rawTracked),
            todoPoints = todoPoints,
            overduePenalty = overduePenalty,
            todoProcrastinationPenalty = todoProcrastinationPenalty,
            taskProcrastinationPenalty = taskProcrastinationPenalty
        )
    }

    /** Today's score with every term of [categoryBreakdownToday] left visible, for the Productivity
     * screen's Today tab. Built from the same function the score flows themselves use, so the
     * explanation can't drift from the number it explains. */
    val scoreBreakdownToday: StateFlow<List<CategoryScoreBreakdown>> =
        combine(allFinishedTasks, todos, procrastinationSettings) { tasks, todoList, settings ->
            listOf(TaskCategory.PERSONAL, TaskCategory.SOCIAL).map {
                categoryBreakdownToday(tasks, todoList, it, settings)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalScoreToday: StateFlow<Int> = combine(allFinishedTasks, todos, procrastinationSettings) { tasks, todoList, settings ->
        categoryScoreToday(tasks, todoList, TaskCategory.PERSONAL, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScoreToday: StateFlow<Int> = combine(allFinishedTasks, todos, procrastinationSettings) { tasks, todoList, settings ->
        categoryScoreToday(tasks, todoList, TaskCategory.SOCIAL, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // NEUTRAL-kind items (Graphite/Grape, productivityValue 0) never contribute either way, so
    // Personal + Social already is the full total -- no separate unfiltered sum needed.
    val totalScoreToday: StateFlow<Int> = combine(personalScoreToday, socialScoreToday) { p, s -> p + s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val personalScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.kind.category == TaskCategory.PERSONAL }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.kind.category == TaskCategory.SOCIAL }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TaskTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            viewModelScope.launch {
                timerService?.taskUpdates?.collect { updates ->
                    _activeSessions.value.forEach { session ->
                        session.activeSegment?.let { ui ->
                            updates[ui.task.id]?.let { elapsed -> ui.elapsedTime.value = elapsed }
                        }
                    }
                }
            }
            _activeSessions.value.forEach { session ->
                session.activeSegment?.let { ui ->
                    // Service now manages its own observing
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    init {
        observeTasks()
        startPeriodicCleanup()
        startCalendarPeriodicRefresh()
        val intent = Intent(context, TaskTimerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startCalendarPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30000)
                refreshCalendar()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTasks() {
        viewModelScope.launch {
            val visibleTasksFlow = repository.getVisibleTasks()
            val calendarTasksFlow = _calendarTrigger.flatMapLatest {
                flow { emit(calendarRepository.getInventoriaTasksFromCalendar()) }
            }
            combine(
                visibleTasksFlow,
                calendarTasksFlow,
                settingsRepository.getHiddenCalendarTaskIds()
            ) { local, calendarAll, hidden ->
                // Calendar rows are rebuilt from the system calendar on every refresh, so there is
                // nothing to soft-delete -- dismissing one can only mean skipping it here.
                val calendar = calendarAll.filter { it.id !in hidden }
                val calendarIds = calendar.map { it.id }.toSet()
                val filteredLocal = local.filter { it.isRunning || it.id !in calendarIds.map { id -> id.removePrefix("cal_") } }
                filteredLocal + calendar
            }.collect { allTasks -> processTasks(allTasks) }
        }
    }

    private fun processTasks(tasks: List<Task>) {
        val grouped = tasks.groupBy { it.groupId }
        val active = grouped.filter { (_, sessionTasks) -> sessionTasks.any { it.isSessionActive } }
            .map { (groupId, sessionTasks) ->
                val runningTask = sessionTasks.find { it.isRunning }
                val runningUI = runningTask?.let { task ->
                    val existing = _activeSessions.value.find { it.groupId == groupId }?.activeSegment
                    if (existing != null && existing.task.id == task.id) { existing.copy(task = task) }
                    else { RunningTaskUI(task).also { startLocalTimer(it) } }
                }
                val segments = sessionTasks.filter { !it.isRunning }.sortedByDescending { it.startTime }
                val isExpanded = _activeSessions.value.find { it.groupId == groupId }?.isExpanded ?: MutableStateFlow(false)
                TaskSessionUI(groupId, segments, isExpanded, runningUI)
            }.sortedByDescending { it.activeSegment?.task?.startTime ?: it.segments.firstOrNull()?.startTime ?: 0L }
        _activeSessions.value = active

        if (!hasLoadedInitialTasks) {
            hasLoadedInitialTasks = true
            if (isFlowModeEnabled.value && active.isEmpty()) {
                addNewTask()
            }
        }

        val completed = grouped.filter { (_, sessionTasks) -> sessionTasks.all { !it.isSessionActive } }
            .values.map { it.sortedByDescending { t -> t.startTime } }.sortedByDescending { it.firstOrNull()?.startTime ?: 0L }
        _completedSessions.value = completed

        tasks.forEach { task ->
            if (task.name.startsWith("Task ")) {
                task.name.substringAfter("Task ").toIntOrNull()?.let { num -> if (num >= taskCounter) taskCounter = num + 1 }
            }
        }
    }

    private fun startLocalTimer(uiTask: RunningTaskUI) {
        uiTask.timerJob?.cancel()
        uiTask.timerJob = viewModelScope.launch {
            while (isActive) {
                uiTask.elapsedTime.value = System.currentTimeMillis() - uiTask.task.startTime
                delay(100)
            }
        }
    }

    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (isActive) {
                repository.purgeOldDeletedTasks(deletedRowPurgeThreshold())
                delay(60000)
            }
        }
    }

    fun addNewTask() {
        flowModeJob?.cancel()
        _isAutoStartPending.value = false
        if (_activeSessions.value.size >= 5) return
        viewModelScope.launch {
            val task = Task(id = UUID.randomUUID().toString(), groupId = UUID.randomUUID().toString(), name = "Task $taskCounter", isRunning = true, startTime = System.currentTimeMillis())
            repository.insertTask(task)
            val intent = Intent(context, TaskTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { context.startForegroundService(intent) }
            else { context.startService(intent) }

            // Force immediate sync after insertion
            syncRepository.triggerFullSync()
        }
    }

    fun pauseResumeTask(session: TaskSessionUI) {
        viewModelScope.launch {
            session.activeSegment?.let { ui ->
                // PAUSING
                val now = System.currentTimeMillis()
                repository.pauseSegment(ui.task, now)

                if (!hasSeenInnerTaskPrompt.value) {
                    _pendingInnerTaskPrompt.value = session.groupId
                } else if (isInnerTaskEnabled.value) {
                    _pendingInnerTaskRename.value = createAndStartInnerTask(session.groupId)
                }
            } ?: run {
                // RESUMING: resuming this session directly means "back to this" -- collapse the
                // whole interruption chain on top of it (however many levels deep, whatever their
                // individual pause state), since none of them have anything left to return to.
                val now = System.currentTimeMillis()
                stopActiveInterruptionChain(session.groupId, now)
                resumeSession(session)
            }
        }
    }

    /** Finds the session that is interrupting [groupId] -- i.e. whose own (first) segment has
     * [Task.interruptedGroupId] pointing at it -- regardless of whether that interrupting session
     * currently has a running segment or is itself paused (because IT has a further interruption
     * on top). Unlike checking only [TaskSessionUI.activeSegment], this finds the whole chain. */
    private fun findInterruptionSessionFor(groupId: String): TaskSessionUI? =
        _activeSessions.value.find { s ->
            (s.activeSegment?.task ?: s.segments.firstOrNull())?.interruptedGroupId == groupId
        }

    /** Stops whatever is interrupting [groupId] -- and, recursively, whatever is interrupting
     * THAT -- deepest first, regardless of how many levels are currently paused partway down the
     * chain. Used whenever a session is being stopped or resumed outright (rather than simply
     * un-pausing straight back into an active interruption), so a chain of interruptions doesn't
     * get left dangling with nothing left to eventually return to. */
    private suspend fun stopActiveInterruptionChain(groupId: String, now: Long) {
        val interrupting = findInterruptionSessionFor(groupId) ?: return
        stopActiveInterruptionChain(interrupting.groupId, now)
        interrupting.activeSegment?.let { ui ->
            repository.stopTaskAndSession(ui.task.id, interrupting.groupId, now, now - ui.task.startTime, ui.task.kind)
        } ?: repository.endSession(interrupting.groupId)
    }

    private suspend fun resumeSession(session: TaskSessionUI) {
        val first = session.segments.firstOrNull() ?: return
        // interruptedGroupId/countsForStreak/originTodoId must carry over from the session's own
        // segments, or a session that gets paused (to spawn an interruption, or just paused and
        // later resumed) silently loses its parent-child link, streak opt-in, or Todo origin the
        // moment it's resumed, since a fresh Task() defaults all three back to null/false -- and
        // since resumeSession always reads the MOST RECENT segment, a single un-propagated resume
        // would otherwise permanently sever the link for every resume after it too.
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            groupId = session.groupId,
            name = first.name,
            kind = first.kind,
            isRunning = true,
            startTime = System.currentTimeMillis(),
            interruptedGroupId = first.interruptedGroupId,
            countsForStreak = first.countsForStreak,
            originTodoId = first.originTodoId
        )
        repository.insertTask(newTask)
    }

    /** User answered the first-time "enable interruption tracking?" prompt. */
    fun respondToInnerTaskPrompt(enable: Boolean, pausedGroupId: String) {
        viewModelScope.launch {
            settingsRepository.setInnerTaskPromptShown(true)
            _pendingInnerTaskPrompt.value = null
            if (enable) {
                settingsRepository.setInnerTaskEnabled(true)
                _pendingInnerTaskRename.value = createAndStartInnerTask(pausedGroupId)
            }
        }
    }

    private suspend fun createAndStartInnerTask(interruptedGroupId: String): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            groupId = UUID.randomUUID().toString(),
            name = "Interruption",
            isRunning = true,
            startTime = System.currentTimeMillis(),
            interruptedGroupId = interruptedGroupId
        )
        repository.insertTask(task)
        val intent = Intent(context, TaskTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { context.startForegroundService(intent) }
        else { context.startService(intent) }
        syncRepository.triggerFullSync()
        return task
    }

    fun renameInnerTask(task: Task, name: String, countsForStreak: Boolean, kind: TaskKind, taskTypeId: String? = null) {
        _pendingInnerTaskRename.value = null
        val finalName = name.ifBlank { task.name }
        if (finalName == task.name && countsForStreak == task.countsForStreak && kind == task.kind && taskTypeId == task.taskTypeId) return
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    name = finalName,
                    isNameCustom = finalName != task.name,
                    countsForStreak = countsForStreak,
                    kind = kind,
                    isKindCustom = kind != task.kind,
                    taskTypeId = taskTypeId
                )
            )
        }
    }

    fun dismissInnerTaskRenameDialog() {
        _pendingInnerTaskRename.value = null
    }

    /** Lets the user change their mind about whether a still-running (or already-renamed)
     * interruption should count toward streaks, from its session card, not just the initial dialog. */
    fun setInnerTaskCountsForStreak(task: Task, countsForStreak: Boolean) {
        if (countsForStreak == task.countsForStreak) return
        viewModelScope.launch {
            repository.updateTask(task.copy(countsForStreak = countsForStreak))
        }
    }

    fun stopTask(session: TaskSessionUI) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = System.currentTimeMillis()
            // A session being stopped might itself be paused right now because IT has an active
            // interruption on top of it (e.g. stopping "Interruption 1" while "Interruption 2" is
            // still running on top of it) -- activeSegment is null in that case, so read
            // interruptedGroupId from the session's own segments instead of assuming it's running.
            val interruptedGroupId = (session.activeSegment?.task ?: session.segments.firstOrNull())?.interruptedGroupId
            // Same "might already be paused" reasoning as interruptedGroupId above -- read the
            // origin off whichever segment is actually available.
            val originTodoId = (session.activeSegment?.task ?: session.segments.firstOrNull())?.originTodoId

            // Cascade: stop whatever is actively interrupting this session first (recursively,
            // in case that interruption has its own interruption on top of it), since it has
            // nothing left to "return to" once this session is gone.
            stopActiveInterruptionChain(session.groupId, now)

            session.activeSegment?.let { ui ->
                repository.stopTaskAndSession(ui.task.id, session.groupId, now, now - ui.task.startTime, ui.task.kind)
            } ?: run { repository.endSession(session.groupId) }
            _isLoading.value = false

            // A session started from a Todo's Start button just ended -- ask whether that actually
            // finished the todo or it's still open. Nothing needs freeing up first: the todo's
            // "in progress" state is derived from its tasks (TodoViewModel.todoIdsWithActiveSession),
            // and the session stopping is itself what releases it.
            if (originTodoId != null) {
                todoRepository.getTodoById(originTodoId)?.let { _pendingTodoCompletionCheckIn.value = it }
            }

            // Stopping an interruption means "back to what I was doing" -- resume it automatically
            // rather than leaving the user to find and un-pause it themselves.
            if (interruptedGroupId != null) {
                _activeSessions.value
                    .find { it.groupId == interruptedGroupId && it.activeSegment == null }
                    ?.let { resumeSession(it) }
            }

            // Flow Mode's auto-start-next-task only makes sense for a genuine stop -- an
            // interruption stop already resumes the parent task above, so auto-starting a THIRD
            // task on top of that would be wrong (and is exactly what was happening before this
            // check existed).
            if (isFlowModeEnabled.value && interruptedGroupId == null) {
                flowModeJob?.cancel()
                flowModeJob = viewModelScope.launch {
                    _isAutoStartPending.value = true
                    delay(1000)
                    if (isFlowModeEnabled.value && _activeSessions.value.size < 5) {
                        // Wait for syncIgnoreCount to settle
                        while (syncRepository.isSyncing()) delay(100)
                        addNewTask()
                    }
                    _isAutoStartPending.value = false
                }
            }
        }
    }

    fun updateSessionName(groupId: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionName(groupId, newName)
            _isLoading.value = false
        }
    }

    /** Autofill picked a previously-used name: take on that name's wording, its kind, and whichever
     * type it has settled on ([modalTypeIdFor]).
     *
     * This is what's left of the old merge-by-name once the identity half is gone. Picking a known
     * name used to move this session into that name's group, which is where it inherited the kind
     * from; now the three inherited values are written onto THIS session and its group id never
     * changes. Same convenience, none of the history rewriting -- see TaskRepository's
     * updateSessionName for why that mattered.
     *
     * A null [typeId] leaves the type alone rather than clearing it: "this name has no majority
     * type yet" is not a reason to discard a type the user set by hand. */
    fun applyRecentSuggestion(groupId: String, newName: String, kind: TaskKind, typeId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionName(groupId, newName)
            repository.updateSessionKind(groupId, kind)
            if (typeId != null) repository.updateSessionTaskType(groupId, typeId)
            _isLoading.value = false
        }
    }

    fun updateSessionTaskType(groupId: String, newTaskTypeId: String?) {
        viewModelScope.launch { repository.updateSessionTaskType(groupId, newTaskTypeId) }
    }

    /** Autofill picked a Task Type: stamp the type on the session and, when that type has history
     * to learn from, pre-set the Kind to whichever one the user most often files it under. The
     * Kind stays freely editable afterward -- this is a starting point, not a lock. */
    fun applyTaskTypeSuggestion(groupId: String, typeId: String) {
        viewModelScope.launch {
            repository.updateSessionTaskType(groupId, typeId)
            taskTypeStats.value[typeId]?.mostUsedKind?.let { kind ->
                repository.updateSessionKind(groupId, kind)
            }
        }
    }

    fun updateSessionKind(groupId: String, newKind: TaskKind) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionKind(groupId, newKind)
            _isLoading.value = false
        }
    }

    /** Retags exactly one segment (see TaskRepository.updateSegmentKind) -- used by
     * ActiveSessionCard's inline Kind dropdown, which shows/edits whichever segment is actually
     * "current" (the running one, or the most recent paused one if nothing's running) and
     * should only affect that one, not the whole session's history. */
    fun updateSegmentKind(taskId: String, newKind: TaskKind) {
        viewModelScope.launch { repository.updateSegmentKind(taskId, newKind) }
    }

    fun updateCompletedTaskName(task: Task, newName: String) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateTask(task.copy(name = newName, isNameCustom = true)) }
    }

    fun updateCompletedTaskKind(task: Task, newKind: TaskKind) {
        if (task.id.startsWith("cal_")) return
        // Same staleness class as ActiveSessionCard's dropdown -- reuses updateSegmentKind
        // rather than writing kind directly, so this task's frozen score gets recomputed under
        // the new Kind instead of being left mismatched to the old one.
        viewModelScope.launch { repository.updateSegmentKind(task.id, newKind) }
    }

    /** Segment-scoped, matching updateCompletedTaskKind above -- TaskDetailDialog is editing this
     * one finished segment. The whole-session equivalent is updateSessionTaskType. */
    fun updateCompletedTaskType(task: Task, newTaskTypeId: String?) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateSegmentTaskType(task.id, newTaskTypeId) }
    }

    fun updateSegmentTime(task: Task, start: Long, end: Long) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateSegmentTime(task, start, end) }
    }

    fun setSegmentCalendarStatus(task: Task, isSaved: Boolean) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(task.copy(savedToCalendar = isSaved, savedToCalendarAt = if (isSaved) System.currentTimeMillis() else null))
        }
    }

    /** Deletes every sitting behind one activity card. Only ever reached through the scope prompt
     * -- a card that stands for several sessions must never delete them all on a single tap. */
    fun deleteSessions(groupIds: List<String>) {
        groupIds.forEach { deleteSession(it) }
    }

    /* Activity-wide edits: the same per-session writes, applied to every sitting behind one
     * activity card. All three are reached only through the scope prompt -- the "change all" arm.
     * Renaming in particular has to be all-or-nothing to be coherent: the name is half the
     * activity key, so renaming one sitting is exactly how you'd split it back out. */

    fun updateActivityName(groupIds: List<String>, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            groupIds.forEach { repository.updateSessionName(it, newName) }
            _isLoading.value = false
        }
    }

    fun updateActivityKind(groupIds: List<String>, newKind: TaskKind) {
        viewModelScope.launch {
            _isLoading.value = true
            groupIds.forEach { repository.updateSessionKind(it, newKind) }
            _isLoading.value = false
        }
    }

    fun updateActivityTaskType(groupIds: List<String>, newTaskTypeId: String?) {
        viewModelScope.launch { groupIds.forEach { repository.updateSessionTaskType(it, newTaskTypeId) } }
    }

    fun deleteSession(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // Read the label before the delete: afterwards the session is a tombstone and the
            // completedSessions flow no longer carries it.
            val label = _completedSessions.value
                .firstOrNull { s -> s.any { it.groupId == groupId } }
                ?.firstOrNull()?.name.orEmpty().ifBlank { "session" }
            repository.softDeleteSession(groupId)
            _isLoading.value = false
            undoController.offer(label) { repository.restoreSession(groupId) }
        }
    }

    /**
     * Throws away a session that is still running, rather than stopping it and keeping the record.
     *
     * Shares [stopTask]'s cascade -- an interruption sitting on top of this one has nothing left to
     * return to once it is gone, and an interrupted parent underneath still wants resuming -- but
     * deliberately not its other two tails. The Todo completion check-in and Flow Mode's
     * auto-start-next both exist to follow up on work that *happened*; asking whether a discarded
     * session finished a todo, or starting the next task off the back of it, would be answering a
     * question the user just declined to ask.
     */
    fun discardSession(session: TaskSessionUI) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = System.currentTimeMillis()
            val interruptedGroupId =
                (session.activeSegment?.task ?: session.segments.firstOrNull())?.interruptedGroupId

            stopActiveInterruptionChain(session.groupId, now)
            repository.softDeleteSession(session.groupId)
            _isLoading.value = false
            // Offered back like any other delete. The confirm in front of it is about the time
            // being thrown away, not about it being unrecoverable -- the rows are tombstoned for
            // the same retention window as everything else.
            undoController.offer(
                (session.activeSegment?.task ?: session.segments.firstOrNull())?.name.orEmpty().ifBlank { "session" }
            ) { repository.restoreSession(session.groupId) }

            if (interruptedGroupId != null) {
                _activeSessions.value
                    .find { it.groupId == interruptedGroupId && it.activeSegment == null }
                    ?.let { resumeSession(it) }
            }
        }
    }

    /**
     * Takes a calendar-sourced task off the list.
     *
     * Not a delete, and the UI must not call it one: these rows are re-read from the system
     * calendar on every refresh and have no local row to remove, and the app holds READ_CALENDAR
     * only, so the event itself is not ours to touch. This records the id as skipped; the event
     * stays exactly where it is in the user's calendar.
     */
    fun hideCalendarTask(task: Task) {
        if (!task.id.startsWith("cal_")) return
        viewModelScope.launch { settingsRepository.hideCalendarTask(task.id) }
    }

    /** Un-hides every calendar row previously dismissed with [hideCalendarTask]. */
    fun restoreHiddenCalendarTasks() {
        viewModelScope.launch { settingsRepository.clearHiddenCalendarTasks() }
    }

    fun deleteSegment(task: Task) {
        viewModelScope.launch {
            repository.softDeleteTask(task.id)
            undoController.offer(task.name.ifBlank { "segment" }) { repository.restoreTask(task.id) }
        }
    }

    fun undoLastDelete() {
        viewModelScope.launch { undoController.undo() }
    }

    /** Live point estimate for the Task Edit screen -- a running task's "so far" total, or a
     * what-if preview if the user changes Kind before saving. See TaskRepository.previewScore. */
    suspend fun previewScore(kind: TaskKind, durationMs: Long): Int = repository.previewScore(kind, durationMs)

    fun deleteSelectedTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            val ids = _selectedTaskIds.value
            ids.forEach { id ->
                val task = _completedSessions.value.flatten().find { it.id == id }
                    ?: _activeSessions.value.flatMap { it.segments }.find { it.id == id }
                task?.let { repository.softDeleteTask(it.id) }
            }
            _selectedTaskIds.value = emptySet()
            _isLoading.value = false
        }
    }

    fun saveSelectedTasksToCalendar() {
        viewModelScope.launch {
            val ids = _selectedTaskIds.value
            ids.forEach { id ->
                val task = _completedSessions.value.flatten().find { it.id == id }
                    ?: _activeSessions.value.flatMap { it.segments }.find { it.id == id }
                task?.let { setSegmentCalendarStatus(it, true) }
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun flattenSession(groupId: String) {
        viewModelScope.launch {
            val tasks = _completedSessions.value.find { it.firstOrNull()?.groupId == groupId } 
                ?: _activeSessions.value.find { it.groupId == groupId }?.segments
                ?: return@launch
            if (tasks.size <= 1) return@launch
            _isLoading.value = true
            val sorted = tasks.sortedBy { it.startTime }
            val first = sorted.first()
            val last = sorted.last()
            val flattened = first.copy(endTime = last.endTime, duration = tasks.sumOf { it.duration })
            repository.updateTask(flattened)
            tasks.filter { it.id != first.id }.forEach { repository.softDeleteTask(it.id) }
            _isLoading.value = false
        }
    }

    /** Default name offered for a split's second half, the same auto-naming a brand new task
     * gets via [addNewTask] -- lets the Split dialog auto-fill a sensible default instead of
     * just repeating the original segment's name. */
    val nextTaskName: String get() = "Task $taskCounter"

    /** Inverse of [flattenSession]: cuts one segment into two at [splitTime]. Unlike a
     * paused-segment + running-segment pair (which share a groupId), the second half gets its
     * OWN fresh groupId -- it's meant to count as a genuinely new, independent task/session
     * rather than a sub-segment nested under the original one. [task] may be running (in which
     * case the second half keeps running to "now", open-ended, mirroring how [resumeSession]
     * starts a fresh running segment) or already a completed/paused segment (in which case the
     * second half is also completed, ending where [task] used to end). Both halves' scores are
     * computed up front from the CURRENT streak, before either write lands, so freezing the
     * first half doesn't shift the streak lookback out from under the second half's
     * calculation. */
    fun splitSegment(task: Task, splitTime: Long, secondName: String, secondKind: TaskKind, secondTypeId: String? = null) {
        if (task.id.startsWith("cal_")) return
        val effectiveEnd = task.endTime ?: System.currentTimeMillis()
        if (splitTime <= task.startTime || splitTime >= effectiveEnd) return
        viewModelScope.launch {
            _isLoading.value = true
            val firstDuration = splitTime - task.startTime
            val secondDuration = effectiveEnd - splitTime
            val firstScore = repository.previewScore(task.kind, firstDuration)
            val secondScore = if (task.isRunning) 0 else repository.previewScore(secondKind, secondDuration)
            val finalSecondName = secondName.ifBlank { task.name }

            repository.updateTask(
                task.copy(endTime = splitTime, duration = firstDuration, isRunning = false, isPaused = true, score = firstScore)
            )
            repository.insertTask(
                Task(
                    id = UUID.randomUUID().toString(),
                    groupId = UUID.randomUUID().toString(),
                    name = finalSecondName,
                    kind = secondKind,
                    startTime = splitTime,
                    endTime = if (task.isRunning) null else task.endTime,
                    duration = if (task.isRunning) 0L else secondDuration,
                    isRunning = task.isRunning,
                    isPaused = if (task.isRunning) false else task.isPaused,
                    isSessionActive = task.isSessionActive,
                    // Matches the convention processTasks()/updateSessionName() already use
                    // elsewhere to recognize an untouched auto-generated "Task N" placeholder --
                    // if the user left the dialog's auto-filled default alone, this isn't a
                    // custom name.
                    isNameCustom = !finalSecondName.startsWith("Task "),
                    isKindCustom = secondKind != task.kind,
                    taskTypeId = secondTypeId,
                    interruptedGroupId = task.interruptedGroupId,
                    countsForStreak = task.countsForStreak,
                    originTodoId = task.originTodoId,
                    score = secondScore
                )
            )
            _isLoading.value = false
        }
    }

    fun toggleTaskSelection(taskId: String) {
        val current = _selectedTaskIds.value
        _selectedTaskIds.value = if (taskId in current) current - taskId else current + taskId
    }

    fun clearSelection() { _selectedTaskIds.value = emptySet() }
    fun refreshCalendar() { _calendarTrigger.value++ }

    private fun getTodayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) { context.unbindService(serviceConnection); isBound = false }
    }
}
