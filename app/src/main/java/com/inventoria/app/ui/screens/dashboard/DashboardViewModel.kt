package com.inventoria.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val outOfStockCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showTotalValue: Boolean = true // New field to control visibility
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository // Injected settings
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        observeDashboardData()
    }

    private fun observeDashboardData() {
        // Combine repository data with the settings flow
        combine(
            repository.getItemCount(),
            repository.getTotalValue(),
            repository.getOutOfStockItems(),
            repository.getAllItems(),
            repository.getAllCategories(),
            settingsRepository.showValueOnDashboard // Observe the setting
        ) { args ->
            val totalItems = args[0] as? Int ?: 0
            val totalValue = args[1] as? Double ?: 0.0
            val outOfStockItems = args[2] as? List<*> ?: emptyList<Any>()
            val allItems = args[3] as? List<InventoryItem> ?: emptyList()
            val categories = args[4] as? List<String> ?: emptyList()
            val showValue = args[5] as? Boolean ?: true

            DashboardUiState(
                totalItems = totalItems,
                totalValue = totalValue,
                outOfStockCount = outOfStockItems.size,
                recentItems = allItems.take(5),
                categories = categories,
                isLoading = false,
                showTotalValue = showValue // Update the state
            )
        }
        .onEach { state ->
            _uiState.value = state
        }
        .launchIn(viewModelScope)
    }
    
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
    }
}
