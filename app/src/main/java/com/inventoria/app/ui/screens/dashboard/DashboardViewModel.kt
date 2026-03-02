package com.inventoria.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val outOfStockCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showTotalValue: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        observeDashboardData()
    }

    private fun observeDashboardData() {
        combine(
            repository.getItemCount(),
            repository.getTotalValue(),
            repository.getOutOfStockItems(),
            repository.getAllItemsWithResolvedLocations(),
            repository.getAllCategories(),
            settingsRepository.showValueOnDashboard
        ) { args: Array<Any?> ->
            val itemCount = args[0] as? Int ?: 0
            val totalVal = args[1] as? Double
            @Suppress("UNCHECKED_CAST")
            val outOfStock = args[2] as? List<InventoryItem> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val allItems = args[3] as? List<InventoryItem> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val categories = args[4] as? List<String> ?: emptyList()
            val showValue = args[5] as? Boolean ?: true

            DashboardUiState(
                totalItems = itemCount,
                totalValue = totalVal ?: 0.0,
                outOfStockCount = outOfStock.size,
                recentItems = allItems.take(5),
                categories = categories,
                isLoading = false,
                showTotalValue = showValue
            )
        }
        .distinctUntilChanged()
        .onEach { state ->
            _uiState.value = state
        }
        .catch { e ->
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
        .launchIn(viewModelScope)
    }
    
    fun toggleEquip(itemId: Long) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId)
            if (item != null) {
                repository.updateItem(item.copy(equipped = !item.equipped))
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        syncRepository.triggerFullSync()
    }
}
