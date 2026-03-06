package com.inventoria.app.ui.screens.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
class TaskTrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository
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
    
    val personalScore = _completedSessions.map { sessions ->
        sessions.flatten().filter { it.kind.category == TaskCategory.PERSONAL }.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialScore = _completedSessions.map { sessions ->
        sessions.flatten().filter { it.kind.category == TaskCategory.SOCIAL }.sumOf { it.score }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            repository.getAllTasks().collect { allTasks ->
                val groups = allTasks.groupBy { it.groupId }
                
                // Active Sessions: At least one segment has isSessionActive = true
                val active = groups.filter { (_, segments) -> segments.any { it.isSessionActive } }
                    .map { (groupId, segments) ->
                        val runningSegment = segments.find { it.isRunning }
                        val activeUI = runningSegment?.let { segment ->
                            val existing = _activeSessions.value.find { it.groupId == groupId }?.activeSegment
                            if (existing != null && existing.task.id == segment.id) {
                                // Keep existing UI wrapper to preserve timer job
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
                            segments = segments.filter { !it.isRunning }.sortedByDescending { it.endTime },
                            isExpanded = existingSession?.isExpanded ?: MutableStateFlow(false),
                            activeSegment = activeUI
                        )
                    }
                _activeSessions.value = active

                // Completed Sessions: All segments have isSessionActive = false
                val completed = groups.filter { (_, segments) -> segments.all { !it.isSessionActive } }
                    .values.toList().sortedByDescending { it.maxOfOrNull { t -> t.endTime ?: 0L } }
                _completedSessions.value = completed
                
                // Update counter based on highest task number
                allTasks.forEach { task ->
                    if (task.name.startsWith("Task ")) {
                        val num = task.name.substringAfter("Task ").toIntOrNull()
                        if (num != null && num >= taskCounter) {
                            taskCounter = num + 1
                        }
                    }
                }
                
                checkAndCleanupSavedTasks(allTasks)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                checkAndCleanupSavedTasks(emptyList()) // Will use repo or state inside
                delay(60000)
            }
        }

        Intent(context, TaskTimerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private suspend fun checkAndCleanupSavedTasks(tasks: List<Task>) {
        val now = System.currentTimeMillis()
        val twentyFourHours = 24 * 60 * 60 * 1000L
        
        tasks.forEach { task ->
            if (task.savedToCalendar && task.savedToCalendarAt != null) {
                if (now - task.savedToCalendarAt!! >= twentyFourHours) {
                    repository.deleteTask(task)
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
                // Pause: End current running segment
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
                // Resume: Create a new segment for the session
                val firstSegment = session.segments.lastOrNull() ?: session.activeSegment?.task 
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
            
            // Handle the active segment if it exists
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
            // Perform bulk update in Room to ensure atomicity and single Flow emission
            repository.updateSessionName(groupId, newName)
            _isLoading.value = false
        }
    }

    fun updateSessionKind(groupId: String, newKind: TaskKind) {
        viewModelScope.launch {
            _isLoading.value = true
            // Perform bulk update in Room to ensure atomicity and single Flow emission
            repository.updateSessionKind(groupId, newKind)
            _isLoading.value = false
        }
    }

    // Explicitly adding these for TaskDetailDialog compatibility
    fun updateCompletedTaskName(task: Task, newName: String) {
        viewModelScope.launch {
            repository.updateTask(task.copy(name = newName, isNameCustom = true))
        }
    }

    fun updateCompletedTaskKind(task: Task, newKind: TaskKind) {
        viewModelScope.launch {
            repository.updateTask(task.copy(kind = newKind, isKindCustom = true))
        }
    }

    fun updateSegment(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun updateSegmentTime(task: Task, startTime: Long, endTime: Long) {
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
            // Batch delete the session to trigger only one sync
            repository.deleteSession(groupId)
            _isLoading.value = false
        }
    }
    
    fun clearSegment(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
