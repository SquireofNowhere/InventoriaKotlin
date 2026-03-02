package com.inventoria.app.ui.screens.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: InventoryItem? = null,
    val parentItem: InventoryItem? = null,
    val availableContainers: List<InventoryItem> = emptyList(),
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
            loadAvailableContainers()
            viewModelScope.launch {
                repository.touchItem(itemId)
            }
        } else {
            _uiState.value = ItemDetailUiState(isLoading = false, error = "Invalid Item ID")
        }
    }

    private fun loadItem() {
        viewModelScope.launch {
            repository.getItemByIdFlow(itemId).collect { item ->
                if (item != null) {
                    var parentItem: InventoryItem? = null
                    val pId = item.parentId
                    if (pId != null) {
                        parentItem = repository.getItemById(pId)
                    }
                    _uiState.update { it.copy(
                        item = item, 
                        parentItem = parentItem,
                        isLoading = false
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Item not found") }
                }
            }
        }
    }

    private fun loadAvailableContainers() {
        viewModelScope.launch {
            // Updated: Use getAllItems instead of getStorageItems to allow ANY item to be a target
            repository.getAllItems().collect { allItems ->
                // Filter out the item itself and any of its nested children to prevent circularity
                val childrenIds = mutableSetOf<Long>()
                fun findChildren(parentId: Long) {
                    allItems.filter { it.parentId == parentId }.forEach {
                        childrenIds.add(it.id)
                        findChildren(it.id)
                    }
                }
                findChildren(itemId)

                val validTargets = allItems.filter { it.id != itemId && !childrenIds.contains(it.id) }
                _uiState.update { it.copy(availableContainers = validTargets) }
            }
        }
    }

    fun moveToContainer(containerId: Long?) {
        viewModelScope.launch {
            val currentItem = _uiState.value.item ?: return@launch
            val updatedItem = currentItem.copy(
                parentId = containerId,
                // If moving into a container, it's no longer "equipped"
                equipped = if (containerId != null) false else currentItem.equipped,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateItem(updatedItem)
            
            // Auto-upgrade target to storage if it isn't already
            if (containerId != null) {
                repository.getItemById(containerId)?.let { target ->
                    if (!target.storage) {
                        repository.updateItem(target.copy(storage = true, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    fun toggleEquip() {
        viewModelScope.launch {
            val currentItem = _uiState.value.item ?: return@launch
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
