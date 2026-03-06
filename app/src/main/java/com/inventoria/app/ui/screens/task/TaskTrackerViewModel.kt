package com.inventoria.app.ui.screens.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.ServiceConnection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

// UI wrapper for a session (group of task segments)
data class TaskSessionUI(
    val groupId: String,
    val segments: List<Task>,
    val isExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val activeSegment: RunningTaskUI? = null
)

// UI wrapper for a running segment to manage local timer state
data class RunningTaskUI(
    val task: Task,
    val elapsedTime: MutableStateFlow<Long> = MutableStateFlow(0L),
    var timerJob: Job? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TaskTrackerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private var taskCounter = 1
    
    // Active sessions being tracked
    private val _activeSessions = MutableStateFlow<List<TaskSessionUI>>(emptyList())
    val activeSessions: StateFlow<List<TaskSessionUI>> = _activeSessions.asStateFlow()

    // Completed sessions (grouped by groupId)
    private val _completedSessions = MutableStateFlow<List<List<Task>>>(emptyList())
    val completedSessions: StateFlow<List<List<Task>>> = _completedSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _calendarTrigger = MutableStateFlow(0)

    private val todayStart: Long
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    val personalScore = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= todayStart }
            .filter { it.kind.category == TaskCategory.PERSONAL }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScore = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= todayStart }
            .filter { it.kind.category == TaskCategory.SOCIAL }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalScore = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= todayStart }
            .sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scoreBreakdown = _completedSessions.map { sessions ->
        sessions.flatten()
            .filter { it.startTime >= todayStart }
            .filter { it.kind.productivityValue != 0 }
            .groupBy { it.kind }
            .mapValues { entry -> entry.value.sumOf { it.score } }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var timerService: TaskTimerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TaskTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            
            viewModelScope.launch {
                timerService?.taskUpdates?.collect { updates ->
                    updates.forEach { (id, elapsed) ->
                        _activeSessions.value.forEach { session ->
                            if (session.activeSegment?.task?.id == id) {
                                session.activeSegment.elapsedTime.value = elapsed
                            }
                        }
                    }
                }
            }
            
            // Re-sync active segments to service
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
        // Observe all tasks and group them into sessions
        viewModelScope.launch {
            // Combine local database tasks with calendar tasks
            combine(
                repository.getVisibleTasks(), 
                _calendarTrigger.flatMapLatest { 
                    flow {
                        // Scan events from the last 364 days for stats and history
                        emit(calendarRepository.getInventoriaTasksFromCalendar(daysBack = 364))
                    }
                }
            ) { localTasks, calendarTasks ->
                // Filter out local tasks that are already represented by a calendar entry
                val localTasksToKeep = localTasks.filter { local ->
                    if (local.isRunning) return@filter true
                    val isDuplicate = calendarTasks.any { cal -> 
                        cal.id == "cal_${local.id}" || 
                        (cal.name == local.name && abs(cal.startTime - local.startTime) < 2000)
                    }
                    !isDuplicate
                }
                localTasksToKeep + calendarTasks
            }.collect { allMergedTasks ->
                val groups = allMergedTasks.groupBy { it.groupId }
                
                // Active Sessions: At least one segment has isSessionActive = true
                val active = groups.filter { (_, segments) -> segments.any { it.isSessionActive } }
                    .map { (groupId, segments) ->
                        val runningSegment = segments.find { it.isRunning }
                        val activeUI = runningSegment?.let { segment ->
                            val existing = _activeSessions.value.find { it.groupId == groupId }?.activeSegment
                            if (existing != null && existing.task.id == segment.id) {
                                existing.copy(task = segment)
                            } else {
                                val ui = RunningTaskUI(task = segment)
                                startLocalTimer(ui)
                                timerService?.startTask(segment.id, segment.startTime, 0L)
                                timerService?.updateTaskName(segment.id, segment.name)
                                ui
                            }
                        }
                        
                        val existingSession = _activeSessions.value.find { it.groupId == groupId }
                        TaskSessionUI(
                            groupId = groupId,
                            segments = segments.filter { !it.isRunning }.sortedByDescending { it.startTime },
                            isExpanded = existingSession?.isExpanded ?: MutableStateFlow(false),
                            activeSegment = activeUI
                        )
                    }
                    .sortedByDescending { session ->
                        session.activeSegment?.task?.startTime ?: session.segments.firstOrNull()?.startTime ?: 0L
                    }
                _activeSessions.value = active

                // Completed Sessions: All segments have isSessionActive = false
                val completed = groups.filter { (_, segments) -> 
                    // Session is completed if NO segment is sessionActive
                    // AND it has a groupId (none session tasks will be grouped or treated as sessions too)
                    segments.all { !it.isSessionActive }
                }
                .values.toList()
                .map { segments -> segments.sortedByDescending { it.startTime } }
                .sortedByDescending { it.first().startTime }
                _completedSessions.value = completed
                
                // Update counter based on highest task number
                allMergedTasks.forEach { task ->
                    if (task.name.startsWith("Task ")) {
                        val num = task.name.substringAfter("Task ").toIntOrNull()
                        if (num != null && num >= taskCounter) {
                            taskCounter = num + 1
                        }
                    }
                }
            }
        }

        // Periodic calendar refresh
        viewModelScope.launch {
            while (isActive) {
                refreshCalendar()
                delay(30000)
            }
        }

        // Cleanup and Purge Job
        viewModelScope.launch {
            while (isActive) {
                // 1. Mark tasks saved to calendar for auto-deletion
                checkAndCleanupSavedTasks()
                // 2. Physically purge old tombstones (older than 24 hours)
                purgeOldDeletedTasks()
                delay(60000)
            }
        }

        Intent(context, TaskTimerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun refreshCalendar() {
        _calendarTrigger.value += 1
    }
    
    private suspend fun checkAndCleanupSavedTasks() {
        val now = System.currentTimeMillis()
        val twentyFourHours = 24 * 60 * 60 * 1000L
        
        repository.getVisibleTasks().first().forEach { task ->
            if (!task.id.startsWith("cal_") && task.savedToCalendar && task.savedToCalendarAt != null) {
                if (now - task.savedToCalendarAt!! >= twentyFourHours) {
                    repository.softDeleteTask(task.id)
                }
            }
        }
    }

    private suspend fun purgeOldDeletedTasks() {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        repository.purgeOldDeletedTasks(twentyFourHoursAgo)
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

    fun addNewTask() {
        if (_activeSessions.value.size >= 5) return

        val groupId = UUID.randomUUID().toString()
        val name = "Task ${taskCounter++}"
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = name,
            startTime = System.currentTimeMillis(),
            isRunning = true,
            isSessionActive = true,
            kind = TaskKind.GRAPHITE
        )
        
        viewModelScope.launch {
            repository.insertTask(newTask)
            
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
            if (session.activeSegment != null) {
                // Pause
                val currentTask = session.activeSegment.task
                val duration = System.currentTimeMillis() - currentTask.startTime
                val pausedTask = currentTask.copy(
                    isRunning = false,
                    isPaused = true,
                    endTime = System.currentTimeMillis(),
                    duration = duration
                )
                timerService?.stopTask(currentTask.id)
                repository.updateTask(pausedTask)
            } else {
                // Resume
                val firstSegment = session.segments.firstOrNull() 
                if (firstSegment != null) {
                    val newSegment = Task(
                        id = UUID.randomUUID().toString(),
                        groupId = session.groupId,
                        name = firstSegment.name,
                        kind = firstSegment.kind,
                        startTime = System.currentTimeMillis(),
                        isRunning = true,
                        isPaused = false,
                        isSessionActive = true
                    )
                    repository.insertTask(newSegment)
                }
            }
        }
    }

    fun stopTask(session: TaskSessionUI) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = System.currentTimeMillis()
            val activeId = session.activeSegment?.task?.id
            val activeDuration = session.activeSegment?.task?.let { now - it.startTime } ?: 0L
            
            if (activeId != null) {
                timerService?.stopTask(activeId)
                repository.stopTaskAndSession(activeId, session.groupId, now, activeDuration)
            } else {
                repository.endSession(session.groupId)
            }
            _isLoading.value = false
        }
    }
    
    fun updateSessionName(groupId: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateSessionName(groupId, newName)
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

    fun updateSegmentTime(task: Task, startTime: Long, endTime: Long) {
        if (task.id.startsWith("cal_")) return
        val duration = endTime - startTime
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    startTime = startTime,
                    endTime = endTime,
                    duration = if (duration > 0) duration else 0
                )
            )
        }
    }

    fun setSegmentCalendarStatus(task: Task, isSaved: Boolean) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    savedToCalendar = isSaved,
                    savedToCalendarAt = if (isSaved) System.currentTimeMillis() else null
                )
            )
        }
    }
    
    fun clearSession(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.softDeleteSession(groupId)
            _isLoading.value = false
        }
    }
    
    fun clearSegment(task: Task) {
        if (task.id.startsWith("cal_")) return
        viewModelScope.launch {
            repository.softDeleteTask(task.id)
        }
    }

    fun flattenSession(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val tasks = repository.getVisibleTasks().first().filter { it.groupId == groupId }
            val eligible = tasks.filter { !it.id.startsWith("cal_") && !it.savedToCalendar && !it.isRunning }
            
            if (eligible.size > 1) {
                val totalDuration = eligible.sumOf { it.duration }
                val lastEndedTask = eligible.filter { it.endTime != null }.maxByOrNull { it.endTime!! }
                val maxEndTime = lastEndedTask?.endTime ?: System.currentTimeMillis()
                val newStartTime = maxEndTime - totalDuration
                
                // We keep the lastEndedTask (or just the first eligible one) and update it
                val baseTask = lastEndedTask ?: eligible.first()
                
                // Update the base task to be the "flattened" version
                val flattenedTask = baseTask.copy(
                    startTime = newStartTime,
                    endTime = maxEndTime,
                    duration = totalDuration
                )
                repository.updateTask(flattenedTask)
                
                // Soft delete the other eligible tasks as they are now merged into baseTask
                eligible.filter { it.id != baseTask.id }.forEach { 
                    repository.softDeleteTask(it.id)
                }
            }
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        timerService = null
    }
}
