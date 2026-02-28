
package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeItems()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItemsWithResolvedLocations()
                .combine(_searchQuery) { items, query ->
                    val filtered = if (query.isBlank()) {
                        items
                    } else {
                        items.filter { it.name.contains(query, ignoreCase = true) }
                    }
                    InventoryUiState(
                        items = items,
                        filteredItems = filtered,
                        isLoading = false
                    )
                }
                .catch { e ->
                    Log.e("InventoryListViewModel", "Error observing items", e)
                    _uiState.value = InventoryUiState(isLoading = false, error = e.message)
                }
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                repository.updateUserLocation(latitude, longitude)
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Error updating location", e)
            }
        }
    }
}
