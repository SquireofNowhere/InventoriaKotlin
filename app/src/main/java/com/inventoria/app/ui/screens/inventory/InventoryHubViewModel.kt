package com.inventoria.app.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The two headline numbers above the Items segment. These used to sit on the old inventory-first
 * dashboard; they moved here with the app's focus shift, which is also what keeps the Settings
 * "Show Total Value" toggle wired to something real.
 */
@HiltViewModel
class InventoryHubViewModel @Inject constructor(
    repository: InventoryRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val totalItems: StateFlow<Int> = repository.getItemCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalValue: StateFlow<Double> = repository.getTotalValue()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val showTotalValue: StateFlow<Boolean> = settingsRepository.getShowValueOnDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
