package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
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
    val expandedItemIds: Set<Long> = emptySet(),
    val groupedItems: List<Pair<String, List<InventoryItem>>> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val allCollections: List<InventoryCollection> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val hiddenCollections: Set<Long> = emptySet(),
    val isHardFilterEnabled: Boolean = true,
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
    private val syncRepository: FirebaseSyncRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
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
                settingsRepository.inventorySortOption, 
                settingsRepository.inventoryGroupOption,
                itemToCollectionsFlow,
                allCollectionsFlow,
                settingsRepository.hiddenCategories,
                settingsRepository.hiddenCollections,
                settingsRepository.isHardFilterEnabled,
                settingsRepository.expandedItemIds
            ) { args: Array<Any?> ->
                val items = args[0] as List<InventoryItem>
                val query = args[1] as String
                val selectionIds = args[2] as Set<Long>
                
                val sortName = args[3] as? String
                val sort = SortOption.values().find { it.name == sortName } ?: SortOption.DATE_DESC
                
                val groupName = args[4] as? String
                val group = GroupOption.values().find { it.name == groupName } ?: GroupOption.NONE
                
                val itemCollections = args[5] as List<Pair<Long, List<Long>>>
                val allCollections = args[6] as List<InventoryCollection>
                val hiddenCats = args[7] as Set<String>
                val hiddenCollsString = args[8] as Set<String>
                val hiddenColls = hiddenCollsString.mapNotNull { it.toLongOrNull() }.toSet()
                val isHardFilter = args[9] as Boolean
                val expandedIdsStrings = args[10] as Set<String>
                val expandedIds = expandedIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()
                
                val isFiltering = query.isNotBlank() || hiddenCats.isNotEmpty() || hiddenColls.isNotEmpty()

                val allCategories = items.flatMap { item -> 
                    item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList() 
                }.distinct().sorted()

                // 1. Initial Match
                val matchedItems = items.filter { item ->
                    val matchesQuery = if (query.isBlank()) true else {
                        val itemCats = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Uncategorized")
                        item.name.contains(query, ignoreCase = true) || 
                        item.location.contains(query, ignoreCase = true) ||
                        item.description?.contains(query, ignoreCase = true) == true ||
                        itemCats.any { it.contains(query, ignoreCase = true) }
                    }
                    
                    val itemCats = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Uncategorized")
                    val itemCollIds = itemCollections.filter { it.second.contains(item.id) }.map { it.first }
                    
                    val isHiddenByCategory = if (isHardFilter) {
                        itemCats.any { hiddenCats.contains(it) }
                    } else {
                        itemCats.all { hiddenCats.contains(it) }
                    }
                    
                    val isHiddenByCollection = if (itemCollIds.isEmpty()) false else {
                        if (isHardFilter) {
                            itemCollIds.any { hiddenColls.contains(it) }
                        } else {
                            itemCollIds.all { hiddenColls.contains(it) }
                        }
                    }
                    
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

                // 3. Sorting
                val sortedItems = when (sort) {
                    SortOption.NAME_ASC -> finalItemsToShow.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> finalItemsToShow.sortedByDescending { it.name.lowercase() }
                    SortOption.DATE_DESC -> finalItemsToShow.sortedByDescending { it.updatedAt }
                    SortOption.QUANTITY_DESC -> finalItemsToShow.sortedByDescending { it.quantity }
                    SortOption.PRICE_DESC -> finalItemsToShow.sortedByDescending { it.price ?: 0.0 }
                }

                // 4. Grouping
                val grouped = when (group) {
                    GroupOption.NONE -> emptyList()
                    GroupOption.CATEGORY -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        val uncategorized = mutableListOf<InventoryItem>()
                        
                        matchedItems.forEach { item ->
                            val itemCategories = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                            if (itemCategories.isEmpty()) {
                                if (!hiddenCats.contains("Uncategorized")) {
                                    uncategorized.add(item)
                                }
                            } else {
                                itemCategories.forEach { cat ->
                                    if (!hiddenCats.contains(cat)) {
                                        result.getOrPut(cat) { mutableListOf() }.add(item)
                                    }
                                }
                            }
                        }
                        
                        val sortedGroups = result.toList().sortedBy { it.first }.toMutableList()
                        if (uncategorized.isNotEmpty()) {
                            sortedGroups.add("Uncategorized" to uncategorized)
                        }
                        sortedGroups.sortedWith(compareBy({ it.first == "Uncategorized" }, { it.first }))
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
                            if (!hiddenColls.contains(coll.id)) {
                                val itemIdsInColl = itemCollections.find { it.first == coll.id }?.second ?: emptyList()
                                val itemsInThisColl = matchedItems.filter { itemIdsInColl.contains(it.id) }
                                if (itemsInThisColl.isNotEmpty()) {
                                    result.getOrPut(coll.name) { mutableListOf() }.addAll(itemsInThisColl)
                                    itemsInThisColl.forEach { itemsInCollections.add(it.id) }
                                }
                            }
                        }
                        
                        val standalone = matchedItems.filter { !itemsInCollections.contains(it.id) }
                        val sortedGroups = result.toList().sortedBy { it.first }.toMutableList()
                        if (standalone.isNotEmpty() && !hiddenColls.contains(-1L)) { 
                            sortedGroups.add("Standalone Items" to standalone.toMutableList())
                        }
                        
                        sortedGroups.sortedWith(compareBy({ it.first == "Standalone Items" }, { it.first }))
                    }
                }

                InventoryUiState(
                    items = items,
                    filteredItems = sortedItems,
                    matchedItemIds = matchedItemIds,
                    expandedItemIds = expandedIds,
                    groupedItems = grouped,
                    allCategories = allCategories,
                    allCollections = allCollections,
                    hiddenCategories = hiddenCats,
                    hiddenCollections = hiddenColls,
                    isHardFilterEnabled = isHardFilter,
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
        viewModelScope.launch {
            settingsRepository.saveInventorySort(option.name)
        }
    }

    fun setGroupOption(option: GroupOption) {
        viewModelScope.launch {
            settingsRepository.saveInventoryGroup(option.name)
        }
    }

    fun toggleCategoryVisibility(category: String) {
        viewModelScope.launch {
            val current = _uiState.value.hiddenCategories
            val newSet = if (current.contains(category)) current - category else current + category
            settingsRepository.saveHiddenCategories(newSet)
        }
    }

    fun toggleCollectionVisibility(collectionId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.hiddenCollections
            val newSet = if (current.contains(collectionId)) current - collectionId else current + collectionId
            settingsRepository.saveHiddenCollections(newSet.map { it.toString() }.toSet())
        }
    }

    fun setHardFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHardFilterEnabled(enabled)
        }
    }

    fun toggleItemExpansion(itemId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.expandedItemIds
            val newSet = if (current.contains(itemId)) current - itemId else current + itemId
            settingsRepository.saveExpandedItems(newSet.map { it.toString() }.toSet())
        }
    }

    fun expandAll() {
        viewModelScope.launch {
            val allContainerIds = _uiState.value.items
                .filter { it.storage || _uiState.value.items.any { child -> child.parentId == it.id } }
                .map { it.id.toString() }
                .toSet()
            settingsRepository.saveExpandedItems(allContainerIds)
        }
    }

    fun collapseAll() {
        viewModelScope.launch {
            settingsRepository.saveExpandedItems(emptySet())
        }
    }

    fun clearFilters() {
        viewModelScope.launch {
            settingsRepository.saveHiddenCategories(emptySet())
            settingsRepository.saveHiddenCollections(emptySet())
            _searchQuery.value = ""
        }
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
