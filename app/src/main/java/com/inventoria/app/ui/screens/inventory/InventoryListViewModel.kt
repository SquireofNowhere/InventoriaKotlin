
package com.inventoria.app.ui.screens.inventory

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
    val isLoading: Boolean = true
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
            combine(repository.getAllItems(), _searchQuery) { items, query ->
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
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }
}
