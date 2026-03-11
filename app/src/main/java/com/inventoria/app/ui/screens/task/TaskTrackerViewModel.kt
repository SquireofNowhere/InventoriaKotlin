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
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _activeSessions = MutableStateFlow<List<TaskSessionUI>>(emptyList())
    val activeSessions: StateFlow<List<TaskSessionUI>> = _activeSessions.asStateFlow()

    private val _completedSessions = MutableStateFlow<List<List<Task>>>(emptyList())
    val completedSessions: StateFlow<List<List<Task>>> = _completedSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _calendarTrigger = MutableStateFlow(0)
    
    // Selection state for multi-select
    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds: StateFlow<Set<String>> = _selectedTaskIds.asStateFlow()

    private var timerService: TaskTimerService? = null
    private var isBound = false
    private var taskCounter = 1

    // Cache to store original states for reversion logic
    private val originalTaskStates = mutableMapOf<String, Pair<String, TaskKind>>()

    val personalScore: StateFlow<Int> = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= getTodayStart() && it.kind.category.name == "PERSONAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScore: StateFlow<Int> = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= getTodayStart() && it.kind.category.name == "SOCIAL" }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScore: StateFlow<Int> = _completedSessions.map { sessions ->
        sessions.flatten().filter { it.startTime >= getTodayStart() }.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scoreBreakdown: StateFlow<List<Pair<TaskKind, Int>>> = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= getTodayStart() }
            .groupBy { it.kind }
            .map { (kind, tasks) -> kind to tasks.size }
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
                            updates[ui.task.id]?.let { elapsed ->
                                ui.elapsedTime.value = elapsed
                            }
                        }
                    }
                }
            }

            _activeSessions.value.forEach { session ->
                session.activeSegment?.let { ui ->
                    if (ui.task.isRunning) {
                        timerService?.startTask(ui.task.id, ui.task.startTime, 0L)
                        timerService?.updateTaskName(ui.task.id, ui.task.name)
                    }
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
        
        val intent = Intent(context, TaskTimerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

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
            }.collect { allTasks ->
                processTasks(allTasks)
            }
        }
    }

    private fun processTasks(tasks: List<Task>) {
        val grouped = tasks.groupBy { it.groupId }
        
        // Process Active Sessions
        val active = grouped.filter { (_, sessionTasks) -> sessionTasks.any { it.isSessionActive } }
            .map { (groupId, sessionTasks) ->
                val runningTask = sessionTasks.find { it.isRunning }
                val runningUI = runningTask?.let { task ->
                    val existing = _activeSessions.value.find { it.groupId == groupId }?.activeSegment
                    if (existing != null && existing.task.id == task.id) {
                        existing.copy(task = task)
                    } else {
                        RunningTaskUI(task).also { startLocalTimer(it) }
                    }
                }
                
                val segments = sessionTasks.filter { !it.isRunning }.sortedByDescending { it.startTime }
                val isExpanded = _activeSessions.value.find { it.groupId == groupId }?.isExpanded 
                    ?: MutableStateFlow(false)
                
                TaskSessionUI(groupId, segments, isExpanded, runningUI)
            }.sortedByDescending { session ->
                session.activeSegment?.task?.startTime ?: session.segments.firstOrNull()?.startTime ?: 0L
            }
        _activeSessions.value = active

        // Process Completed Sessions
        val completed = grouped.filter { (_, sessionTasks) -> sessionTasks.all { !it.isSessionActive } }
            .values.map { sessionTasks ->
                sessionTasks.sortedByDescending { it.startTime }
            }.sortedByDescending { it.firstOrNull()?.startTime ?: 0L }
        _completedSessions.value = completed

        // Capture Originals for running tasks
        tasks.forEach { task ->
            if (task.isRunning && !originalTaskStates.containsKey(task.id)) {
                originalTaskStates[task.id] = task.groupId to task.kind
            }
            if (task.name.startsWith("Task ")) {
                task.name.substringAfter("Task ").toIntOrNull()?.let { num ->
                    if (num >= taskCounter) taskCounter = num + 1
                }
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
        if (_activeSessions.value.size >= 5) return
        viewModelScope.launch {
            val task = Task(
                id = UUID.randomUUID().toString(),
                groupId = UUID.randomUUID().toString(),
                name = "Task $taskCounter",
                isRunning = true,
                startTime = System.currentTimeMillis()
            )
            repository.insertTask(task)
            
            val intent = Intent(context, TaskTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun pauseResumeTask(session: TaskSessionUI) {
        viewModelScope.launch {
            session.activeSegment?.let { ui ->
                val now = System.currentTimeMillis()
                val updatedTask = ui.task.copy(
                    isRunning = false,
                    isPaused = true,
                    endTime = now,
                    duration = now - ui.task.startTime
                )
                timerService?.stopTask(ui.task.id)
                repository.updateTask(updatedTask)
            } ?: run {
                val first = session.segments.firstOrNull() ?: return@launch
                val newTask = Task(
                    id = UUID.randomUUID().toString(),
                    groupId = session.groupId,
                    name = first.name,
                    kind = first.kind,
                    isRunning = true,
                    startTime = System.currentTimeMillis()
                )
                repository.insertTask(newTask)
            }
        }
    }

    fun stopTask(session: TaskSessionUI) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = System.currentTimeMillis()
            session.activeSegment?.let { ui ->
                timerService?.stopTask(ui.task.id)
                repository.stopTaskAndSession(ui.task.id, session.groupId, now, now - ui.task.startTime)
            } ?: run {
                repository.endSession(session.groupId)
            }
            _isLoading.value = false
        }
    }

    fun updateSessionName(groupId: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Reversion Logic: If name is returned to "Task X" or similar default
            val runningTask = _activeSessions.value.find { it.groupId == groupId }?.activeSegment?.task
            if (runningTask != null && (newName.isBlank() || newName.startsWith("Task "))) {
                originalTaskStates[runningTask.id]?.let { (originalGroupId, originalKind) ->
                    repository.updateSessionNameAndGroupId(groupId, newName, originalGroupId)
                    repository.updateSessionKind(originalGroupId, originalKind)
                } ?: repository.updateSessionName(groupId, newName)
            } else {
                repository.updateSessionName(groupId, newName)
            }
            
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
        viewModelScope.launch {
            repository.updateTask(task.copy(name = newName, isNameCustom = true))
        }
    }

    fun updateCompletedTaskKind(task: Task, newKind: TaskKind) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(task.copy(kind = newKind, isKindCustom = true))
        }
    }

    fun updateSegment(task: Task) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun updateSegmentTime(task: Task, start: Long, end: Long) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(task.copy(startTime = start, endTime = end, duration = end - start))
        }
    }

    fun setSegmentCalendarStatus(task: Task, isSaved: Boolean) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(task.copy(
                savedToCalendar = isSaved,
                savedToCalendarAt = if (isSaved) System.currentTimeMillis() else null
            ))
        }
    }

    fun clearSession(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.softDeleteSession(groupId)
            _isLoading.value = false
        }
    }

    fun deleteSessionPermanently(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deleteSession(groupId)
            _isLoading.value = false
        }
    }

    fun clearSegment(task: Task) {
        viewModelScope.launch {
            repository.softDeleteTask(task.id)
        }
    }

    fun deleteSegmentPermanently(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
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
            
            val flattened = first.copy(
                endTime = last.endTime,
                duration = tasks.sumOf { it.duration }
            )
            
            repository.updateTask(flattened)
            tasks.filter { it.id != first.id }.forEach { repository.softDeleteTask(it.id) }
            _isLoading.value = false
        }
    }

    fun toggleTaskSelection(taskId: String) {
        val current = _selectedTaskIds.value
        _selectedTaskIds.value = if (taskId in current) current - taskId else current + taskId
    }

    fun clearSelection() {
        _selectedTaskIds.value = emptySet()
    }

    fun refreshCalendar() {
        _calendarTrigger.value++
    }

    private fun getTodayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
