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

    private val itemId: Long = checkNotNull(savedStateHandle["itemId"])

    init {
        loadItem()
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
            repository.deleteItemById(itemId)
            onDeleted()
        }
    }
}
