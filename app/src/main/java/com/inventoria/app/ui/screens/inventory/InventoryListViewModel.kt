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
    val matchedItemIds: Set<Long> = emptySet(),
    val groupedItems: List<Pair<String, List<InventoryItem>>> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val allCollections: List<InventoryCollection> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val hiddenCollections: Set<Long> = emptySet(),
    val collectionItemIds: Set<Long> = emptySet(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val groupOption: GroupOption = GroupOption.NONE,
    val isFiltering: Boolean = false,
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
    private val _hiddenCategories = MutableStateFlow(setOf<String>())
    private val _hiddenCollections = MutableStateFlow(setOf<Long>())
    private val _currentCollectionId = MutableStateFlow<Long?>(null)
    
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
            val allCollectionsFlow = collectionRepository.getAllCollections()
            
            val itemToCollectionsFlow = allCollectionsFlow.flatMapLatest { collections ->
                val flows = collections.map { coll ->
                    collectionRepository.getCollectionWithItems(coll.id).map { coll.id to (it?.items?.map { i -> i.id } ?: emptyList()) }
                }
                if (flows.isEmpty()) flowOf(emptyList<Pair<Long, List<Long>>>())
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
                itemToCollectionsFlow,
                allCollectionsFlow,
                _hiddenCategories,
                _hiddenCollections
            ) { args: Array<Any?> ->
                val items = args[0] as List<InventoryItem>
                val query = args[1] as String
                val selectionIds = args[2] as Set<Long>
                val sort = args[3] as SortOption
                val group = args[4] as GroupOption
                val itemCollections = args[5] as List<Pair<Long, List<Long>>>
                val allCollections = args[6] as List<InventoryCollection>
                val hiddenCats = args[7] as Set<String>
                val hiddenColls = args[8] as Set<Long>
                
                val isFiltering = query.isNotBlank() || hiddenCats.isNotEmpty() || hiddenColls.isNotEmpty()

                val allCategories = items.flatMap { item -> 
                    item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList() 
                }.distinct().sorted()

                // 1. Initial Match
                val matchedItems = items.filter { item ->
                    val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
                    
                    val itemCats = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Uncategorized")
                    val isHiddenByCategory = itemCats.all { hiddenCats.contains(it) } // Hidden only if ALL its categories are hidden
                    
                    val itemCollIds = itemCollections.filter { it.second.contains(item.id) }.map { it.first }
                    val isHiddenByCollection = itemCollIds.isNotEmpty() && itemCollIds.all { hiddenColls.contains(it) }
                    
                    matchesQuery && !isHiddenByCategory && !isHiddenByCollection
                }
                val matchedItemIds = matchedItems.map { it.id }.toSet()

                // 2. Ancestor preservation for tree structure
                val itemsToShowIds = mutableSetOf<Long>()
                val itemMap = items.associateBy { it.id }
                
                matchedItems.forEach { matchedItem ->
                    itemsToShowIds.add(matchedItem.id)
                    var parentId = matchedItem.parentId
                    while (parentId != null) {
                        if (itemsToShowIds.add(parentId)) {
                            parentId = itemMap[parentId]?.parentId
                        } else {
                            parentId = null
                        }
                    }
                }

                val finalItemsToShow = items.filter { itemsToShowIds.contains(it.id) }

                // 3. Sorting (applied globally but UI handles sibling sorting)
                val sortedItems = when (sort) {
                    SortOption.NAME_ASC -> finalItemsToShow.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> finalItemsToShow.sortedByDescending { it.name.lowercase() }
                    SortOption.DATE_DESC -> finalItemsToShow.sortedByDescending { it.updatedAt }
                    SortOption.QUANTITY_DESC -> finalItemsToShow.sortedByDescending { it.quantity }
                    SortOption.PRICE_DESC -> finalItemsToShow.sortedByDescending { it.price ?: 0.0 }
                }

                // 4. Grouping (Grouped view is always flat for clarity)
                val grouped = when (group) {
                    GroupOption.NONE -> emptyList()
                    GroupOption.CATEGORY -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        val uncategorized = mutableListOf<InventoryItem>()
                        
                        // Use matchedItems for grouping to avoid showing ancestor "noise" in categories
                        matchedItems.forEach { item ->
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
                        matchedItems.groupBy { it.location.ifBlank { "No Location" } }
                            .map { it.key to it.value }
                            .sortedWith(compareBy({ it.first != "With You" }, { it.first == "No Location" }, { it.first }))
                    }
                    GroupOption.COLLECTION -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        val itemsInCollections = mutableSetOf<Long>()
                        
                        allCollections.forEach { coll ->
                            val itemIdsInColl = itemCollections.find { it.first == coll.id }?.second ?: emptyList()
                            val itemsInThisColl = matchedItems.filter { itemIdsInColl.contains(it.id) }
                            if (itemsInThisColl.isNotEmpty()) {
                                result.getOrPut(coll.name) { mutableListOf() }.addAll(itemsInThisColl)
                                itemsInThisColl.forEach { itemsInCollections.add(it.id) }
                            }
                        }
                        
                        val standalone = matchedItems.filter { !itemsInCollections.contains(it.id) }
                        if (standalone.isNotEmpty()) {
                            result["Standalone Items"] = standalone.toMutableList()
                        }
                        
                        result.toList().sortedWith(compareBy({ it.first == "Standalone Items" }, { it.first }))
                    }
                }

                InventoryUiState(
                    items = items,
                    filteredItems = sortedItems,
                    matchedItemIds = matchedItemIds,
                    groupedItems = grouped,
                    allCategories = allCategories,
                    allCollections = allCollections,
                    hiddenCategories = hiddenCats,
                    hiddenCollections = hiddenColls,
                    collectionItemIds = selectionIds,
                    sortOption = sort,
                    groupOption = group,
                    isFiltering = isFiltering,
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

    fun toggleCategoryVisibility(category: String) {
        val current = _hiddenCategories.value
        _hiddenCategories.value = if (current.contains(category)) current - category else current + category
    }

    fun toggleCollectionVisibility(collectionId: Long) {
        val current = _hiddenCollections.value
        _hiddenCollections.value = if (current.contains(collectionId)) current - collectionId else current + collectionId
    }

    fun clearFilters() {
        _hiddenCategories.value = emptySet()
        _hiddenCollections.value = emptySet()
        _searchQuery.value = ""
    }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            try {
                val item = repository.getItemById(itemId) ?: return@launch
                if (item.equipped) {
                    if (repack && item.lastParentId != null) {
                        repository.updateItem(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null))
                    } else {
                        repository.updateItem(item.copy(equipped = false, lastParentId = null))
                    }
                } else {
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
                } else {
                    collectionRepository.addItemToCollection(collectionId, itemId)
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
                
                if (itemId == newParentId) return@launch
                
                val oldParentId = itemToMove.parentId

                val updatedItem = itemToMove.copy(
                    parentId = newParentId,
                    equipped = if (newParentId != null) false else itemToMove.equipped,
                    lastParentId = null,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateItem(updatedItem)

                if (newParentId != null) {
                    allItems.find { it.id == newParentId }?.let { target ->
                        if (!target.storage) {
                            repository.updateItem(target.copy(storage = true, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }

                if (oldParentId != null) {
                    val remainingChildren = allItems.filter { it.parentId == oldParentId && it.id != itemId }
                    if (remainingChildren.isEmpty()) {
                        allItems.find { it.id == oldParentId }?.let { oldParent ->
                            repository.updateItem(oldParent.copy(storage = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to move item", e)
            }
        }
    }
}
