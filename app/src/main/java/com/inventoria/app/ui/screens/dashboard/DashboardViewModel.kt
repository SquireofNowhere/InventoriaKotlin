package com.inventoria.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            settingsRepository.getShowValueOnDashboard()
        ) { array ->
            val itemCount = array[0] as Int
            val totalVal = array[1] as Double?
            val outOfStock = array[2] as List<*>
            val allItems = array[3] as List<*>
            val categories = array[4] as List<*>
            val showValue = array[5] as Boolean

            DashboardUiState(
                totalItems = itemCount,
                totalValue = totalVal ?: 0.0,
                outOfStockCount = outOfStock.size,
                recentItems = allItems.filterIsInstance<com.inventoria.app.data.model.InventoryItem>().take(5),
                categories = categories.filterIsInstance<String>(),
                isLoading = false,
                showTotalValue = showValue
            )
        }.onEach { state ->
            _uiState.value = state
        }.catch { e ->
            _uiState.value = _uiState.value.copy(isLoading = false)
        }.launchIn(viewModelScope)
    }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            if (item.equipped) {
                if (repack && item.lastParentId != null) {
                    repository.moveItem(itemId, item.lastParentId)
                }
                repository.updateItemEquippedStatus(itemId, false)
            } else {
                repository.updateItemEquippedStatus(itemId, true)
            }
        }
    }

    suspend fun getContainerName(id: Long): String? {
        return repository.getItemById(id)?.name
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        syncRepository.triggerFullSync()
    }
}
