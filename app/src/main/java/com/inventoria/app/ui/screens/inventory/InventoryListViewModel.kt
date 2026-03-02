package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val collectionItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val collectionRepository: CollectionRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _currentCollectionId = MutableStateFlow<Long?>(null)
    
    // Expose sync status for UI components
    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus

    init {
        observeItems()
    }

    fun setCollectionId(collectionId: Long) {
        _currentCollectionId.value = collectionId
    }

    private fun observeItems() {
        viewModelScope.launch {
            val itemsFlow = repository.getAllItemsWithResolvedLocations()
            val collectionItemsFlow = _currentCollectionId.flatMapLatest { id ->
                if (id != null) {
                    collectionRepository.getCollectionWithItems(id).map { it?.items?.map { item -> item.id }?.toSet() ?: emptySet() }
                } else {
                    flowOf(emptySet())
                }
            }

            combine(itemsFlow, _searchQuery, collectionItemsFlow) { items, query, collectionIds ->
                val filtered = if (query.isBlank()) {
                    items
                } else {
                    items.filter { it.name.contains(query, ignoreCase = true) }
                }
                InventoryUiState(
                    items = items,
                    filteredItems = filtered,
                    collectionItemIds = collectionIds,
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

    fun toggleItemInCollection(itemId: Long, collectionId: Long) {
        viewModelScope.launch {
            try {
                val isInCollection = _uiState.value.collectionItemIds.contains(itemId)
                if (isInCollection) {
                    collectionRepository.removeItemFromCollection(collectionId, itemId)
                    Log.d("InventoryListViewModel", "Removed item $itemId from collection $collectionId")
                } else {
                    collectionRepository.addItemToCollection(collectionId, itemId)
                    Log.d("InventoryListViewModel", "Added item $itemId to collection $collectionId")
                }
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to toggle item in collection", e)
            }
        }
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

    fun moveItem(itemId: Long, newParentId: Long?) {
        viewModelScope.launch {
            try {
                val allItems = _uiState.value.items
                val itemToMove = allItems.find { it.id == itemId } ?: return@launch
                
                // 1. Prevent moving an item into itself
                if (itemId == newParentId) return@launch
                
                val oldParentId = itemToMove.parentId

                // 2. Update the item being moved
                val updatedItem = itemToMove.copy(
                    parentId = newParentId,
                    // If moved into a container, it shouldn't be "equipped" anymore
                    equipped = if (newParentId != null) false else itemToMove.equipped,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateItem(updatedItem)

                // 3. Auto-Container: If dropped into a target, make target a container
                if (newParentId != null) {
                    allItems.find { it.id == newParentId }?.let { target ->
                        if (!target.storage) {
                            repository.updateItem(target.copy(storage = true, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }

                // 4. Auto-Revert: If old parent is now empty, it stops being a container
                if (oldParentId != null) {
                    val remainingChildren = allItems.filter { it.parentId == oldParentId && it.id != itemId }
                    if (remainingChildren.isEmpty()) {
                        allItems.find { it.id == oldParentId }?.let { oldParent ->
                            repository.updateItem(oldParent.copy(storage = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }

                Log.d("InventoryListViewModel", "Moved item $itemId. New parent: $newParentId, Old parent: $oldParentId")
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to move item", e)
            }
        }
    }
}
