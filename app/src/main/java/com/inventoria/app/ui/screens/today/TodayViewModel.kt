package com.inventoria.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.repository.FirebaseSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Everything the Today screen needs that isn't a todo. That's deliberately almost nothing: the
 * todo half comes from TodoViewModel (hosted alongside this one), so all this owns is the task
 * list behind the 24-hour timeline plus the top bar's refresh.
 *
 * Deliberately not a descendant of the inventory dashboard ViewModel this screen replaced: that
 * one combined five inventory flows -- including one resolving a display location for every item
 * in the database -- to reach the single task list Today actually wants. Wrong thing to run on the
 * start destination.
 *
 * Deliberately does NOT touch TaskTrackerViewModel: that binds TaskTimerService in its init and
 * renders the post-session check-in dialog from TaskTrackerScreen only. Pulling it in here would
 * open a second service binding and orphan any check-in it triggered.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    taskRepository: TaskRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    /** Feeds LinearProductivityChart, which does its own filtering down to the current day. */
    val tasks: StateFlow<List<Task>> = taskRepository.getVisibleTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        syncRepository.triggerFullSync()
    }
}
