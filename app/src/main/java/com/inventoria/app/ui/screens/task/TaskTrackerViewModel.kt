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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// UI wrapper for running tasks to manage local timer state
data class RunningTaskUI(
    val task: Task,
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
    val runningTasks = mutableStateListOf<RunningTaskUI>()

    private val _completedTasks = MutableStateFlow<List<Task>>(emptyList())
    val completedTasks: StateFlow<List<Task>> = _completedTasks.asStateFlow()

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
                        runningTasks.find { it.task.id == id }?.elapsedTime?.value = elapsed
                    }
                }
            }
            
            // Sync current running tasks to service
            runningTasks.forEach { uiTask ->
                timerService?.startTask(uiTask.task.id, uiTask.task.startTime)
                timerService?.updateTaskName(uiTask.task.id, uiTask.task.name)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    init {
        // Observe running tasks from repository
        viewModelScope.launch {
            repository.getRunningTasks().collectLatest { tasks ->
                // Update local list while preserving timer jobs
                val currentIds = runningTasks.map { it.task.id }.toSet()
                val newIds = tasks.map { it.id }.toSet()

                // Remove tasks no longer running
                runningTasks.removeAll { it.task.id !in newIds }

                // Add or update tasks
                tasks.forEach { task ->
                    val existing = runningTasks.find { it.task.id == task.id }
                    if (existing == null) {
                        val uiTask = RunningTaskUI(task = task)
                        startLocalTimer(uiTask)
                        runningTasks.add(uiTask)
                        timerService?.startTask(task.id, task.startTime)
                        timerService?.updateTaskName(task.id, task.name)
                    } else if (existing.task != task) {
                        // Update the task data but keep the timer job
                        val index = runningTasks.indexOf(existing)
                        runningTasks[index] = existing.copy(task = task)
                    }
                }
                
                // Update counter based on existing task names
                tasks.forEach { task ->
                    if (task.name.startsWith("Task ")) {
                        val num = task.name.substringAfter("Task ").toIntOrNull()
                        if (num != null && num >= taskCounter) {
                            taskCounter = num + 1
                        }
                    }
                }
            }
        }

        // Observe completed tasks from repository
        viewModelScope.launch {
            repository.getCompletedTasks().collect { tasks ->
                _completedTasks.value = tasks
            }
        }

        Intent(context, TaskTimerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
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
        if (runningTasks.size >= 5) return

        val newTask = Task(
            id = UUID.randomUUID().toString(),
            name = "Task ${taskCounter++}",
            startTime = System.currentTimeMillis(),
            isRunning = true
        )
        
        viewModelScope.launch {
            repository.insertTask(newTask)
            
            // Start foreground service
            val intent = Intent(context, TaskTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopTask(uiTask: RunningTaskUI) {
        uiTask.timerJob?.cancel()
        timerService?.stopTask(uiTask.task.id)
        
        val completedTask = uiTask.task.copy(
            isRunning = false,
            endTime = System.currentTimeMillis(),
            duration = uiTask.elapsedTime.value
        )
        
        viewModelScope.launch {
            repository.updateTask(completedTask)
        }
    }
    
    fun updateTaskName(uiTask: RunningTaskUI, newName: String) {
        viewModelScope.launch {
            repository.updateTask(uiTask.task.copy(name = newName))
        }
    }

    fun updateTaskKind(uiTask: RunningTaskUI, newKind: TaskKind) {
        viewModelScope.launch {
            repository.updateTask(uiTask.task.copy(kind = newKind))
        }
    }

    fun updateCompletedTaskName(task: Task, newName: String) {
        viewModelScope.launch {
            repository.updateTask(task.copy(name = newName))
        }
    }

    fun updateCompletedTaskKind(task: Task, newKind: TaskKind) {
        viewModelScope.launch {
            repository.updateTask(task.copy(kind = newKind))
        }
    }

    fun setCompletedTaskCalendarStatus(task: Task, isSaved: Boolean) {
        viewModelScope.launch {
            repository.updateTask(task.copy(savedToCalendar = isSaved))
        }
    }
    
    fun clearCompletedTask(task: Task) {
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
