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
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            combine(
                repository.getItemCount(),
                repository.getTotalValue(),
                repository.getLowStockItems(),
                repository.getOutOfStockItems(),
                repository.getAllItems(),
                repository.getAllCategories()
            ) { args: Array<*> -> // Use Array access for 6+ flows
                DashboardUiState(
                    totalItems = args[0] as Int,
                    totalValue = (args[1] as? Double) ?: 0.0,
                    lowStockCount = (args[2] as List<*>).size,
                    outOfStockCount = (args[3] as List<*>).size,
                    recentItems = (args[4] as List<InventoryItem>).take(5),
                    categories = args[5] as List<String>,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadDashboardData()
    }
}
