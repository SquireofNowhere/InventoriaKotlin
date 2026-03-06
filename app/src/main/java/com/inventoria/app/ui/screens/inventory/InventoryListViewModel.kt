package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.data.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val allLinks: List<ItemLink> = emptyList(),
    val linkedItemIds: Set<Long> = emptySet(),
    val groupedItems: List<Pair<String, List<InventoryItem>>> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val allCollections: List<InventoryCollection> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val hiddenCollections: Set<Long> = emptySet(),
    val isHardFilterEnabled: Boolean = true,
    val isInvertFilterEnabled: Boolean = false,
    val collectionItemIds: Set<Long> = emptySet(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val groupOption: GroupOption = GroupOption.NONE,
    val isFiltering: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryListViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val collectionRepository: CollectionRepository,
    val syncRepository: FirebaseSyncRepository,
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
            val allLinksFlow = repository.getAllLinksFlow()

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

            @Suppress("UNCHECKED_CAST")
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
                settingsRepository.isInvertFilterEnabled,
                settingsRepository.expandedItemIds,
                allLinksFlow
            ) { args: Array<Any?> ->
                val items = args[0] as List<InventoryItem>
                val query = args[1] as String
                val selectionIds = args[2] as Set<Long>
                val sortName = args[3] as? String
                val sort = SortOption.entries.find { it.name == sortName } ?: SortOption.DATE_DESC
                val groupName = args[4] as? String
                val group = GroupOption.entries.find { it.name == groupName } ?: GroupOption.NONE
                val itemCollections = args[5] as List<Pair<Long, List<Long>>>
                val allCollections = args[6] as List<InventoryCollection>
                val selectedCats = args[7] as Set<String>
                val selectedCollsString = args[8] as Set<String>
                val selectedColls = selectedCollsString.mapNotNull { it.toLongOrNull() }.toSet()
                val isHardFilter = args[9] as Boolean
                val isInvertFilter = args[10] as Boolean
                val expandedIdsStrings = args[11] as Set<String>
                val expandedIds = expandedIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()
                val allLinks = args[12] as List<ItemLink>
                
                val linkedItemIds = allLinks.flatMap { listOf(it.followerId, it.leaderId) }.toSet()
                val isFiltering = query.isNotBlank()
                val allCategories = items.flatMap { item -> item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList() }.distinct().sorted()

                val matchedItems = items.filter { item ->
                    val itemCats = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Uncategorized")
                    val matchesQuery = if (query.isBlank()) true else {
                        item.name.contains(query, ignoreCase = true) || item.location.contains(query, ignoreCase = true) ||
                        item.description?.contains(query, ignoreCase = true) == true || itemCats.any { it.contains(query, ignoreCase = true) }
                    }
                    val itemCollIds = itemCollections.filter { it.second.contains(item.id) }.map { it.first }
                    
                    val matchesCategory = if (selectedCats.isEmpty()) !isInvertFilter else {
                        val hasSelectedCat = if (isHardFilter) itemCats.any { selectedCats.contains(it) } else itemCats.all { selectedCats.contains(it) }
                        if (isInvertFilter) hasSelectedCat else !hasSelectedCat
                    }

                    val matchesCollection = if (selectedColls.isEmpty()) !isInvertFilter else {
                        val inSelectedColl = if (itemCollIds.isEmpty()) false else {
                            if (isHardFilter) itemCollIds.any { selectedColls.contains(it) } else itemCollIds.all { selectedColls.contains(it) }
                        }
                        if (isInvertFilter) inSelectedColl else !inSelectedColl
                    }

                    // Combined Logic:
                    // If either filter type is used, they act as broad exclusions/inclusions.
                    // To stay consistent with standard filter behavior:
                    // If no categories are selected AND no collections are selected, show everything.
                    val hasAnyFilterSelection = selectedCats.isNotEmpty() || selectedColls.isNotEmpty()
                    
                    val passesVisibility = if (!hasAnyFilterSelection) true else {
                        // If Invert is ON: Item must pass Category OR Collection (Union of inclusions)
                        // If Invert is OFF: Item must pass Category AND Collection (Intersection of exclusions)
                        if (isInvertFilter) {
                            (selectedCats.isNotEmpty() && matchesCategory) || (selectedColls.isNotEmpty() && matchesCollection)
                        } else {
                            matchesCategory && matchesCollection
                        }
                    }

                    matchesQuery && passesVisibility
                }
                val matchedItemIds = matchedItems.map { it.id }.toSet()

                val itemsToShowIds = mutableSetOf<Long>()
                val itemMap = items.associateBy { it.id }
                matchedItems.forEach { matchedItem ->
                    itemsToShowIds.add(matchedItem.id)
                    var parentId = matchedItem.parentId
                    while (parentId != null) {
                        if (itemsToShowIds.add(parentId)) parentId = itemMap[parentId]?.parentId else parentId = null
                    }
                }
                val finalItemsToShow = items.filter { itemsToShowIds.contains(it.id) }

                val sortedItems = when (sort) {
                    SortOption.NAME_ASC -> finalItemsToShow.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> finalItemsToShow.sortedByDescending { it.name.lowercase() }
                    SortOption.DATE_DESC -> finalItemsToShow.sortedByDescending { it.updatedAt }
                    SortOption.QUANTITY_DESC -> finalItemsToShow.sortedByDescending { it.quantity }
                    SortOption.PRICE_DESC -> finalItemsToShow.sortedByDescending { it.price ?: 0.0 }
                }

                val grouped = when (group) {
                    GroupOption.NONE -> emptyList()
                    GroupOption.CATEGORY -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>()
                        matchedItems.forEach { item ->
                            val cats = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("Uncategorized")
                            cats.forEach { cat -> 
                                if (selectedCats.isEmpty() || (isInvertFilter && selectedCats.contains(cat)) || (!isInvertFilter && !selectedCats.contains(cat))) {
                                    result.getOrPut(cat) { mutableListOf() }.add(item)
                                }
                            }
                        }
                        result.toList().sortedWith(compareBy({ it.first == "Uncategorized" }, { it.first }))
                    }
                    GroupOption.LOCATION -> matchedItems.groupBy { it.location.ifBlank { "No Location" } }.toList().sortedWith(compareBy({ it.first != "With You" }, { it.first == "No Location" }, { it.first }))
                    GroupOption.COLLECTION -> {
                        val result = mutableMapOf<String, MutableList<InventoryItem>>().apply { put("Standalone Items", mutableListOf()) }
                        val itemsInColl = mutableSetOf<Long>()
                        allCollections.forEach { coll ->
                            if (selectedColls.isEmpty() || (isInvertFilter && selectedColls.contains(coll.id)) || (!isInvertFilter && !selectedColls.contains(coll.id))) {
                                val itemIds = itemCollections.find { it.first == coll.id }?.second ?: emptyList()
                                val itemsHere = matchedItems.filter { itemIds.contains(it.id) }
                                if (itemsHere.isNotEmpty()) {
                                    result.getOrPut(coll.name) { mutableListOf() }.addAll(itemsHere)
                                    itemsHere.forEach { itemsInColl.add(it.id) }
                                }
                            }
                        }
                        matchedItems.filter { !itemsInColl.contains(it.id) }.forEach { result.getValue("Standalone Items").add(it) }
                        result.filter { it.value.isNotEmpty() }.toList().sortedWith(compareBy({ it.first == "Standalone Items" }, { it.first }))
                    }
                }

                InventoryUiState(
                    items = items, filteredItems = sortedItems, matchedItemIds = matchedItemIds, expandedItemIds = expandedIds,
                    groupedItems = grouped, allCategories = allCategories, allCollections = allCollections,
                    hiddenCategories = selectedCats, hiddenCollections = selectedColls, 
                    isHardFilterEnabled = isHardFilter, isInvertFilterEnabled = isInvertFilter,
                    collectionItemIds = selectionIds, sortOption = sort, groupOption = group, isFiltering = isFiltering,
                    searchQuery = query, isLoading = false, allLinks = allLinks, linkedItemIds = linkedItemIds
                )
            }
            .catch { e -> Log.e("InventoryListViewModel", "Error observing items", e); _uiState.value = InventoryUiState(isLoading = false, error = e.message) }
            .collect { newState -> _uiState.value = newState }
        }
    }

    fun triggerManualSync() {
        syncRepository.triggerFullSync()
    }

    fun search(query: String) { _searchQuery.value = query }
    fun setSortOption(option: SortOption) { viewModelScope.launch { settingsRepository.saveInventorySort(option.name) } }
    fun setGroupOption(option: GroupOption) { viewModelScope.launch { settingsRepository.saveInventoryGroup(option.name) } }
    fun toggleCategoryVisibility(category: String) { viewModelScope.launch { settingsRepository.saveHiddenCategories(if (_uiState.value.hiddenCategories.contains(category)) _uiState.value.hiddenCategories - category else _uiState.value.hiddenCategories + category) } }
    fun toggleCollectionVisibility(collectionId: Long) { viewModelScope.launch { settingsRepository.saveHiddenCollections(_uiState.value.hiddenCollections.let { if (it.contains(collectionId)) it - collectionId else it + collectionId }.map { it.toString() }.toSet()) } }
    fun setHardFilterEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setHardFilterEnabled(enabled) } }
    fun setInvertFilterEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepository.setInvertFilterEnabled(enabled) } }
    fun toggleItemExpansion(itemId: Long) { viewModelScope.launch { settingsRepository.saveExpandedItems(_uiState.value.expandedItemIds.let { if (it.contains(itemId)) it - itemId else it + itemId }.map { it.toString() }.toSet()) } }
    fun expandAll() { viewModelScope.launch { settingsRepository.saveExpandedItems(_uiState.value.items.filter { it.storage || _uiState.value.items.any { child -> child.parentId == it.id } }.map { it.id.toString() }.toSet()) } }
    fun collapseAll() { viewModelScope.launch { settingsRepository.saveExpandedItems(emptySet()) } }
    fun clearFilters() { viewModelScope.launch { settingsRepository.saveHiddenCategories(emptySet()); settingsRepository.saveHiddenCollections(emptySet()); _searchQuery.value = "" } }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            try {
                val item = repository.getItemById(itemId) ?: return@launch
                if (item.equipped) {
                    if (repack && item.lastParentId != null) {
                        repository.updateItems(listOf(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null)))
                    } else {
                        repository.updateItems(listOf(item.copy(equipped = false, lastParentId = null)))
                    }
                } else {
                    repository.updateItems(listOf(item.copy(equipped = true)))
                }
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to toggle equip", e)
            }
        }
    }

    suspend fun getContainerName(id: Long): String? = repository.getItemById(id)?.name

    fun toggleItemInCollection(itemId: Long, collectionId: Long) {
        viewModelScope.launch { collectionRepository.let { if (_uiState.value.collectionItemIds.contains(itemId)) it.removeItemFromCollection(collectionId, itemId) else it.addItemToCollection(collectionId, itemId) } }
    }

    fun updateUserLocation(latitude: Double, longitude: Double) { viewModelScope.launch { repository.updateUserLocation(latitude, longitude) } }

    fun moveItem(itemId: Long, newParentId: Long?) {
        viewModelScope.launch {
            try {
                val allItems = _uiState.value.items
                val itemToMove = repository.getItemById(itemId) ?: return@launch
                
                if (itemId == newParentId) return@launch
                if (itemToMove.parentId == newParentId) return@launch

                if (newParentId != null) {
                    val targetItem = repository.getItemById(newParentId)
                    if (targetItem != null) {
                        if (targetItem.storage) {
                            repository.updateItems(listOf(itemToMove.copy(parentId = newParentId, equipped = false, lastParentId = null)), applyToFollowers = true)
                        } else {
                            repository.addLink(followerId = itemId, leaderId = newParentId)
                            return@launch
                        }
                    }
                } else {
                    // Moving to ROOT
                    repository.updateItems(listOf(itemToMove.copy(parentId = null)), applyToFollowers = true)
                }
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to move/link item", e)
            }
        }
    }

    fun linkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            try {
                repository.addLink(followerId = followerId, leaderId = leaderId)
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to link items", e)
            }
        }
    }

    fun unlinkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            try {
                repository.removeLink(followerId = followerId, leaderId = leaderId)
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to unlink items", e)
            }
        }
    }
}
