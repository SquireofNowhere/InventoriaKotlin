package com.inventoria.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalItems: Int = 0,
    val totalValue: Double = 0.0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val recentItems: List<InventoryItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: InventoryRepository
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
            repository.getLowStockItems(),
            repository.getOutOfStockItems(),
            repository.getAllItems(),
            repository.getAllCategories()
        ) { args ->
            val totalItems = args[0] as? Int ?: 0
            val totalValue = args[1] as? Double ?: 0.0
            val lowStockItems = args[2] as? List<*> ?: emptyList<Any>()
            val outOfStockItems = args[3] as? List<*> ?: emptyList<Any>()
            val allItems = args[4] as? List<*> ?: emptyList<Any>()
            val categories = args[5] as? List<*> ?: emptyList<Any>()

            DashboardUiState(
                totalItems = totalItems,
                totalValue = totalValue,
                lowStockCount = lowStockItems.size,
                outOfStockCount = outOfStockItems.size,
                recentItems = allItems.filterIsInstance<InventoryItem>().take(5),
                categories = categories.filterIsInstance<String>(),
                isLoading = false
            )
        }
        .onEach { state ->
            _uiState.value = state
        }
        .launchIn(viewModelScope)
    }
    
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        // Since we are observing flows, they will update automatically if the repository changes.
        // But if we want to "force" a refresh from a source that isn't reactive, we'd do it here.
    }
}
