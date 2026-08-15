package com.inventoria.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TaskTypeRepository
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats
import com.inventoria.app.data.model.computeTaskTypeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskTypesViewModel @Inject constructor(
    private val taskTypeRepository: TaskTypeRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val taskTypes: StateFlow<List<TaskType>> = taskTypeRepository.getVisibleTaskTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Same single-pass aggregate the Tracker and Stats screens use -- see computeTaskTypeStats. */
    val stats: StateFlow<Map<String, TaskTypeStats>> =
        combine(taskTypes, taskRepository.getVisibleTasks()) { types, tasks ->
            computeTaskTypeStats(types, tasks)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addTaskType(name: String) {
        viewModelScope.launch { taskTypeRepository.addTaskType(name) }
    }

    fun renameTaskType(taskType: TaskType, newName: String) {
        viewModelScope.launch { taskTypeRepository.renameTaskType(taskType, newName) }
    }

    fun deleteTaskType(id: String) {
        viewModelScope.launch { taskTypeRepository.deleteTaskType(id) }
    }
}
