package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption(val displayName: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Recently Updated"),
    QUANTITY_DESC("Highest Quantity"),
    PRICE_DESC("Highest Price")
}

enum class GroupOption(val displayName: String) {
    NONE("No Grouping"),
    CATEGORY("Category"),
    COLLECTION("Collection"),
    LOCATION("Location")
}

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val groupedItems: List<Pair<String, List<InventoryItem>>> = emptyList(),
    val collectionItemIds: Set<Long> = emptySet(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val groupOption: GroupOption = GroupOption.NONE,
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
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val _groupOption = MutableStateFlow(GroupOption.NONE)
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
            
            // Get mapping of items to collections for grouping
            val itemToCollectionsFlow = collectionRepository.getAllCollections().flatMapLatest { collections ->
                val flows = collections.map { coll ->
                    collectionRepository.getCollectionWithItems(coll.id).map { coll.name to (it?.items?.map { i -> i.id } ?: emptyList()) }
                }
                if (flows.isEmpty()) flowOf(emptyList<Pair<String, List<Long>>>())
                else combine(flows) { it.toList() }
            }

            val selectionModeCollectionItemsFlow = _currentCollectionId.flatMapLatest { id ->
                if (id != null) {
                    collectionRepository.getCollectionWithItems(id).map { it?.items?.map { item -> item.id }?.toSet() ?: emptySet() }
                } else {
                    flowOf(emptySet())
                }
            }

            combine(
                itemsFlow, 
                _searchQuery, 
                selectionModeCollectionItemsFlow, 
                _sortOption, 
                _groupOption,
                itemToCollectionsFlow
            ) { args: Array<Any?> ->
                val items = args[0] as List<InventoryItem>
                val query = args[1] as String
                val selectionIds = args[2] as Set<Long>
                val sort = args[3] as SortOption
                val group = args[4] as GroupOption
                val itemCollections = args[5] as List<Pair<String, List<Long>>>
                
                // 1. Filter
                var filtered = if (query.isBlank()) {
                    items
                } else {
                    items.filter { it.name.contains(query, ignoreCase = true) }
                }

                // 2. Sort
                filtered = when (sort) {
                    SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                    SortOption.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
                    SortOption.QUANTITY_DESC -> filtered.sortedByDescending { it.quantity }
                    SortOption.PRICE_DESC -> filtered.sortedByDescending { it.price ?: 0.0 }
                }

                // 3. Group
                val grouped = when (group) {
                    GroupOption.NONE -> emptyList()
                    GroupOption.CATEGORY -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        val uncategorized = mutableListOf<InventoryItem>()
                        
                        filtered.forEach { item ->
                            val itemCategories = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                            if (itemCategories.isNullOrEmpty()) {
                                uncategorized.add(item)
                            } else {
                                itemCategories.forEach { cat ->
                                    result.getOrPut(cat) { mutableListOf() }.add(item)
                                }
                            }
                        }
                        
                        val sorted = result.toList().toMutableList()
                        if (uncategorized.isNotEmpty()) {
                            sorted.add("Uncategorized" to uncategorized)
                        }
                        sorted.sortedWith(compareBy({ it.first == "Uncategorized" }, { it.first }))
                    }
                    GroupOption.LOCATION -> {
                        filtered.groupBy { it.location.ifBlank { "No Location" } }
                            .map { it.key to it.value }
                            .sortedWith(compareBy({ it.first != "With You" }, { it.first == "No Location" }, { it.first }))
                    }
                    GroupOption.COLLECTION -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        val itemsInCollections = mutableSetOf<Long>()
                        
                        itemCollections.forEach { (collName, itemIds) ->
                            val itemsInThisColl = filtered.filter { itemIds.contains(it.id) }
                            if (itemsInThisColl.isNotEmpty()) {
                                result.getOrPut(collName) { mutableListOf() }.addAll(itemsInThisColl)
                                itemsInThisColl.forEach { itemsInCollections.add(it.id) }
                            }
                        }
                        
                        val standalone = filtered.filter { !itemsInCollections.contains(it.id) }
                        if (standalone.isNotEmpty()) {
                            result["Standalone Items"] = standalone.toMutableList()
                        }
                        
                        result.toList().sortedWith(compareBy({ it.first == "Standalone Items" }, { it.first }))
                    }
                }

                InventoryUiState(
                    items = items,
                    filteredItems = filtered,
                    groupedItems = grouped,
                    collectionItemIds = selectionIds,
                    sortOption = sort,
                    groupOption = group,
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

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setGroupOption(option: GroupOption) {
        _groupOption.value = option
    }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            try {
                val item = repository.getItemById(itemId) ?: return@launch
                if (item.equipped) {
                    // Unequipping
                    if (repack && item.lastParentId != null) {
                        repository.updateItem(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null))
                    } else {
                        repository.updateItem(item.copy(equipped = false, lastParentId = null))
                    }
                } else {
                    // Equipping
                    repository.updateItem(item.copy(equipped = true))
                }
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to toggle equip", e)
            }
        }
    }

    suspend fun getContainerName(id: Long): String? {
        return repository.getItemById(id)?.name
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
                    lastParentId = null, // Clear history when manually moved
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
