package com.inventoria.app.ui.screens.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.repository.CalendarRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

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

@HiltViewModel
class TaskTrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: com.inventoria.app.data.repository.FirebaseSyncRepository
) : ViewModel() {

    private val _activeSessions = MutableStateFlow<List<TaskSessionUI>>(emptyList())
    val activeSessions: StateFlow<List<TaskSessionUI>> = _activeSessions.asStateFlow()

    private val _completedSessions = MutableStateFlow<List<List<Task>>>(emptyList())
    val completedSessions: StateFlow<List<List<Task>>> = _completedSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private val originalTaskStates = mutableMapOf<String, Pair<String, TaskKind>>()

    // Every segment that has actually finished (isRunning = false), whether its parent session
    // is still active (paused, might resume later) or fully stopped. Metrics previously only
    // read from _completedSessions (fully-stopped sessions only), so an already-worked, already-
    // paused segment of a still-in-progress session silently didn't count toward today's/lifetime
    // totals until the whole session was eventually stopped -- sometimes hours or days later.
    val allFinishedTasks: StateFlow<List<Task>> = combine(_activeSessions, _completedSessions) { active, completed ->
        completed.flatten() + active.flatMap { it.segments }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalScoreToday: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.startTime >= getTodayStart() && it.kind.category.name == "PERSONAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScoreToday: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.startTime >= getTodayStart() && it.kind.category.name == "SOCIAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScoreToday: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks.filter { it.startTime >= getTodayStart() }.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val personalScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.kind.category.name == "PERSONAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.kind.category.name == "SOCIAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScoreLifetime: StateFlow<Int> = allFinishedTasks.map { tasks ->
        tasks.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scoreBreakdownToday: StateFlow<List<Pair<TaskKind, Int>>> = allFinishedTasks.map { tasks ->
        tasks
            .filter { it.startTime >= getTodayStart() }
            .groupBy { it.kind }
            .map { (kind, kindTasks) -> kind to kindTasks.size }
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scoreBreakdownLifetime: StateFlow<List<Pair<TaskKind, Int>>> = allFinishedTasks.map { tasks ->
        tasks
            .groupBy { it.kind }
            .map { (kind, kindTasks) -> kind to kindTasks.size }
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            combine(visibleTasksFlow, calendarTasksFlow) { local, calendar ->
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
            if (task.isRunning && !originalTaskStates.containsKey(task.id)) {
                originalTaskStates[task.id] = task.groupId to task.kind
            }
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
                repository.purgeOldDeletedTasks(System.currentTimeMillis() - 86400000)
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
        // interruptedGroupId/countsForStreak must carry over from the session's own segments, or
        // an interruption that gets paused (to spawn a further interruption of its own) silently
        // loses its parent-child link -- and its streak opt-in -- the moment it's resumed, since
        // a fresh Task() defaults both back to null/false.
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            groupId = session.groupId,
            name = first.name,
            kind = first.kind,
            isRunning = true,
            startTime = System.currentTimeMillis(),
            interruptedGroupId = first.interruptedGroupId,
            countsForStreak = first.countsForStreak
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

    fun renameInnerTask(task: Task, name: String, countsForStreak: Boolean, kind: TaskKind) {
        _pendingInnerTaskRename.value = null
        val finalName = name.ifBlank { task.name }
        if (finalName == task.name && countsForStreak == task.countsForStreak && kind == task.kind) return
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    name = finalName,
                    isNameCustom = finalName != task.name,
                    countsForStreak = countsForStreak,
                    kind = kind,
                    isKindCustom = kind != task.kind
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

            // Cascade: stop whatever is actively interrupting this session first (recursively,
            // in case that interruption has its own interruption on top of it), since it has
            // nothing left to "return to" once this session is gone.
            stopActiveInterruptionChain(session.groupId, now)

            session.activeSegment?.let { ui ->
                repository.stopTaskAndSession(ui.task.id, session.groupId, now, now - ui.task.startTime, ui.task.kind)
            } ?: run { repository.endSession(session.groupId) }
            _isLoading.value = false

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
            val runningTask = _activeSessions.value.find { it.groupId == groupId }?.activeSegment?.task
            if (runningTask != null && (newName.isBlank() || newName.startsWith("Task "))) {
                originalTaskStates[runningTask.id]?.let { (origId, origKind) ->
                    repository.updateSessionNameAndGroupId(groupId, newName, origId)
                    repository.updateSessionKind(origId, origKind)
                } ?: repository.updateSessionName(groupId, newName)
            } else { repository.updateSessionName(groupId, newName) }
            _isLoading.value = false
        }
    }

    fun updateSessionNameAndGroup(oldGroupId: String, newName: String, newGroupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionNameAndGroupId(oldGroupId, newName, newGroupId)
            _isLoading.value = false
        }
    }

    fun updateSessionKind(groupId: String, newKind: TaskKind) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionKind(groupId, newKind)
            _isLoading.value = false
        }
    }

    fun updateCompletedTaskName(task: Task, newName: String) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateTask(task.copy(name = newName, isNameCustom = true)) }
    }

    fun updateCompletedTaskKind(task: Task, newKind: TaskKind) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateTask(task.copy(kind = newKind, isKindCustom = true)) }
    }

    fun updateSegment(task: Task) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch { repository.updateTask(task) }
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

    fun deleteSession(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.softDeleteSession(groupId)
            _isLoading.value = false
        }
    }

    fun deleteSegment(task: Task) {
        viewModelScope.launch { repository.softDeleteTask(task.id) }
    }

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
