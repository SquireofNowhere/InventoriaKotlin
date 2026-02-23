package com.inventoria.app.ui.screens.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class TaskKind(val displayName: String, val color: Color) {
    FREE_TIME("Free Time", Color.White),
    BIG_WASTE("Big Waste", Color(0xFFDC143C)), // Crimson
    SMALL_WASTE("Small Waste", Color.Red),
    NEUTRAL_WAITING("Neutral/Waiting", Color.Gray),
    SMALL_PRODUCTIVE("Small Productive", Color(0xFF006400)), // Dark Green
    BIG_PRODUCTIVE("Big Productive", Color.Green)
}

// Represents a completed task
data class TrackedTask(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val kind: TaskKind = TaskKind.NEUTRAL_WAITING,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val savedToCalendar: Boolean = false
)

// Represents a task that is currently being timed
data class RunningTask(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    var kind: TaskKind = TaskKind.NEUTRAL_WAITING,
    val startTime: Long = System.currentTimeMillis(),
    val elapsedTime: MutableStateFlow<Long> = MutableStateFlow(0L),
    var timerJob: Job? = null,
    var isEditingName: Boolean = false
)

@HiltViewModel
class TaskTrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository
) : ViewModel() {

    private var taskCounter = 1
    val runningTasks = mutableStateListOf<RunningTask>()

    private val _completedTasks = MutableStateFlow<List<TrackedTask>>(emptyList())
    val completedTasks: StateFlow<List<TrackedTask>> = _completedTasks.asStateFlow()

    private var timerService: TaskTimerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TaskTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            
            // Sync service updates to UI
            viewModelScope.launch {
                timerService?.taskUpdates?.collect { updates ->
                    updates.forEach { (id, elapsed) ->
                        runningTasks.find { it.id == id }?.elapsedTime?.value = elapsed
                    }
                }
            }
            
            // After binding, make sure the service knows about loaded tasks and their names
            runningTasks.forEach { task ->
                timerService?.startTask(task.id, task.startTime)
                timerService?.updateTaskName(task.id, task.name)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    init {
        // Load persisted running tasks
        val persistedRunningTasks = repository.loadRunningTasks()
        persistedRunningTasks.forEach { serializable ->
            val task = RunningTask(
                id = serializable.id,
                name = serializable.name,
                kind = serializable.kind ?: TaskKind.NEUTRAL_WAITING,
                startTime = serializable.startTime
            )
            startLocalTimer(task)
            runningTasks.add(task)
            
            // Extract the counter from the last task name if it follows the pattern
            if (task.name.startsWith("Task ")) {
                val num = task.name.substringAfter("Task ").toIntOrNull()
                if (num != null && num >= taskCounter) {
                    taskCounter = num + 1
                }
            }
        }

        // Load persisted completed tasks (only those not saved to calendar)
        _completedTasks.value = repository.loadCompletedTasks()

        Intent(context, TaskTimerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startLocalTimer(task: RunningTask) {
        task.timerJob?.cancel()
        task.timerJob = viewModelScope.launch {
            while (isActive) {
                task.elapsedTime.value = System.currentTimeMillis() - task.startTime
                delay(100)
            }
        }
    }

    fun addNewTask() {
        if (runningTasks.size >= 5) return

        val newTask = RunningTask(name = "Task ${taskCounter++}")
        
        // Start foreground service if not already running
        val intent = Intent(context, TaskTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        // Register task with service for background tracking
        timerService?.startTask(newTask.id, newTask.startTime)
        timerService?.updateTaskName(newTask.id, newTask.name)

        startLocalTimer(newTask)
        runningTasks.add(newTask)
        repository.saveRunningTasks(runningTasks)
    }

    fun stopTask(task: RunningTask) {
        task.timerJob?.cancel()
        timerService?.stopTask(task.id)
        runningTasks.remove(task)
        repository.saveRunningTasks(runningTasks)

        val newCompletedTask = TrackedTask(
            name = task.name,
            kind = task.kind,
            startTime = task.startTime,
            endTime = System.currentTimeMillis(),
            duration = task.elapsedTime.value
        )

        val updatedList = _completedTasks.value.toMutableList().apply { add(0, newCompletedTask) }
        _completedTasks.value = updatedList
        repository.saveCompletedTasks(updatedList)
    }
    
    fun updateTaskName(task: RunningTask, newName: String) {
        val index = runningTasks.indexOf(task)
        if (index != -1) {
            runningTasks[index] = runningTasks[index].copy(name = newName, isEditingName = false)
            timerService?.updateTaskName(task.id, newName)
            repository.saveRunningTasks(runningTasks)
        }
    }

    fun updateTaskKind(task: RunningTask, newKind: TaskKind) {
        val index = runningTasks.indexOf(task)
        if (index != -1) {
            runningTasks[index] = runningTasks[index].copy(kind = newKind)
            repository.saveRunningTasks(runningTasks)
        }
    }

    fun updateCompletedTaskName(task: TrackedTask, newName: String) {
        val updatedList = _completedTasks.value.map {
            if (it.id == task.id) it.copy(name = newName) else it
        }
        _completedTasks.value = updatedList
        repository.saveCompletedTasks(updatedList)
    }

    fun updateCompletedTaskKind(task: TrackedTask, newKind: TaskKind) {
        val updatedList = _completedTasks.value.map {
            if (it.id == task.id) it.copy(kind = newKind) else it
        }
        _completedTasks.value = updatedList
        repository.saveCompletedTasks(updatedList)
    }

    fun setCompletedTaskCalendarStatus(task: TrackedTask, isSaved: Boolean) {
        val updatedList = _completedTasks.value.map {
            if (it.id == task.id) it.copy(savedToCalendar = isSaved) else it
        }
        _completedTasks.value = updatedList
        repository.saveCompletedTasks(updatedList)
    }
    
    fun clearCompletedTask(task: TrackedTask) {
        val updatedList = _completedTasks.value.toMutableList().apply { remove(task) }
        _completedTasks.value = updatedList
        repository.saveCompletedTasks(updatedList)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
