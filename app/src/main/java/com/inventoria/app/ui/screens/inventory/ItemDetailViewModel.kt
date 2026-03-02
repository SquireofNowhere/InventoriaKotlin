package com.inventoria.app.ui.screens.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: InventoryItem? = null,
    val parentItem: InventoryItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    // Robust itemId retrieval handling both Long and String from navigation
    private val itemId: Long = run {
        val rawId = savedStateHandle.get<Any>("itemId")
        when (rawId) {
            is Long -> rawId
            is String -> rawId.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    init {
        if (itemId != 0L) {
            loadItem()
        } else {
            _uiState.value = ItemDetailUiState(isLoading = false, error = "Invalid Item ID")
        }
    }

    private fun loadItem() {
        viewModelScope.launch {
            repository.getItemByIdFlow(itemId).collect { item ->
                if (item != null) {
                    var parentItem: InventoryItem? = null
                    // Fix: Use local variable to allow smart cast since parentId is mutable (var)
                    val pId = item.parentId
                    if (pId != null) {
                        parentItem = repository.getItemById(pId)
                    }
                    _uiState.value = ItemDetailUiState(
                        item = item, 
                        parentItem = parentItem,
                        isLoading = false
                    )
                } else {
                    _uiState.value = ItemDetailUiState(isLoading = false, error = "Item not found")
                }
            }
        }
    }

    fun toggleEquip() {
        viewModelScope.launch {
            val currentItem = _uiState.value.item ?: return@launch
            // Updated to use renamed 'equipped' field
            val updatedItem = currentItem.copy(equipped = !currentItem.equipped)
            repository.updateItem(updatedItem)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (itemId != 0L) {
                repository.deleteItemById(itemId)
            }
            onDeleted()
        }
    }
}
