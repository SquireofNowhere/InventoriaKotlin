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

    // Safely retrieve itemId which might be stored as a String by Compose Navigation
    private val itemId: Long = savedStateHandle.get<String>("itemId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("itemId")
        ?: 0L

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
                    _uiState.value = ItemDetailUiState(item = item, isLoading = false)
                } else {
                    _uiState.value = ItemDetailUiState(isLoading = false, error = "Item not found")
                }
            }
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
