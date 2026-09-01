package com.inventoria.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Everything the Today screen needs that isn't a todo: the task list behind the 24-hour timeline,
 * the top bar's refresh, the focus preference that orders the screen, and -- under Inventory
 * focus only -- the three numbers on the summary card.
 *
 * The inventory flows are cheap COUNT/SUM queries (the same ones InventoryHubViewModel serves)
 * and WhileSubscribed, so they only run while the Inventory branch of the screen is actually
 * collecting them. That keeps faith with why this class replaced the old inventory dashboard
 * ViewModel: that one resolved a display location for every item in the database to reach the
 * single task list Today wanted. Nothing like that belongs on the start destination.
 *
 * Deliberately does NOT touch TaskTrackerViewModel: that binds TaskTimerService in its init and
 * renders the post-session check-in dialog from TaskTrackerScreen only. Pulling it in here would
 * open a second service binding and orphan any check-in it triggered.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    taskRepository: TaskRepository,
    inventoryRepository: InventoryRepository,
    collectionRepository: CollectionRepository,
    settingsRepository: SettingsRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    /** Feeds LinearProductivityChart, which does its own filtering down to the current day. */
    val tasks: StateFlow<List<Task>> = taskRepository.getVisibleTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
