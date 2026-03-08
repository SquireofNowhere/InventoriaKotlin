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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private fun observeItems() {
        val allItemsFlow = repository.getAllItems()
        val allCollectionsFlow = collectionRepository.getAllCollections()
        val allLinksFlow = repository.getAllLinksFlow()
        
        val itemToCollectionsFlow = allCollectionsFlow.flatMapLatest { collections ->
            if (collections.isEmpty()) flowOf(emptyMap<Long, List<Long>>())
            else {
                val flows = collections.map { coll ->
                    collectionRepository.getItemsForCollection(coll.id).map { collItems -> coll.id to collItems.map { it.itemId } }
                }
                combine(flows) { pairs ->
                    val map = mutableMapOf<Long, MutableList<Long>>()
                    pairs.forEach { (collId, itemIds) ->
                        itemIds.forEach { itemId ->
                            map.getOrPut(itemId) { mutableListOf() }.add(collId)
                        }
                    }
                    map
                }
            }
        }

        val currentCollectionItemIdsFlow = _currentCollectionId.flatMapLatest { id ->
            if (id == null) flowOf(emptySet<Long>())
            else collectionRepository.getItemsForCollection(id).map { list -> list.map { it.itemId }.toSet() }
        }

        combine(
            allItemsFlow,
            _searchQuery,
            currentCollectionItemIdsFlow,
            settingsRepository.getInventorySortOption(),
            settingsRepository.getInventoryGroupOption(),
            itemToCollectionsFlow,
            allCollectionsFlow,
            settingsRepository.getHiddenCategories(),
            settingsRepository.getHiddenCollections(),
            settingsRepository.isHardFilterEnabled(),
            settingsRepository.isInvertFilterEnabled(),
            settingsRepository.getExpandedItemIds(),
            allLinksFlow,
            repository.getUserLocation()
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val allItems = args[0] as List<InventoryItem>
            val query = args[1] as String
            @Suppress("UNCHECKED_CAST")
            val currentCollItemIds = args[2] as Set<Long>
            val sortOptionStr = args[3] as String
            val groupOptionStr = args[4] as String
            @Suppress("UNCHECKED_CAST")
            val itemToCollections = args[5] as Map<Long, List<Long>>
            @Suppress("UNCHECKED_CAST")
            val allCollections = args[6] as List<InventoryCollection>
            @Suppress("UNCHECKED_CAST")
            val hiddenCategories = args[7] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val hiddenCollections = args[8] as Set<String>
            val isHardFilter = args[9] as Boolean
            val isInvertFilter = args[10] as Boolean
            @Suppress("UNCHECKED_CAST")
            val expandedItemIdsStrings = args[11] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val allLinks = args[12] as List<ItemLink>
            @Suppress("UNCHECKED_CAST")
            val userLoc = args[13] as Pair<Double, Double>?

            val sortOption = SortOption.valueOf(sortOptionStr)
            val groupOption = GroupOption.valueOf(groupOptionStr)
            val expandedItemIds = expandedItemIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()
            val hiddenCollectionIds = hiddenCollections.mapNotNull { it.toLongOrNull() }.toSet()

            val resolvedItems = resolveLocations(allItems, allLinks, userLoc)

            val filtered = filterAndSortItems(
                resolvedItems, query, _currentCollectionId.value, currentCollItemIds, hiddenCategories, 
                hiddenCollectionIds, itemToCollections, isHardFilter, isInvertFilter, sortOption
            )

            val grouped = groupItems(filtered, groupOption, allCollections, itemToCollections)

            InventoryUiState(
                items = allItems,
                filteredItems = filtered,
                matchedItemIds = filtered.map { it.id }.toSet(),
                expandedItemIds = expandedItemIds,
                allLinks = allLinks,
                linkedItemIds = allLinks.map { it.followerId }.toSet(),
                groupedItems = grouped,
                allCategories = allItems.mapNotNull { it.category }.distinct().sorted(),
                allCollections = allCollections,
                hiddenCategories = hiddenCategories,
                hiddenCollections = hiddenCollectionIds,
                isHardFilterEnabled = isHardFilter,
                isInvertFilterEnabled = isInvertFilter,
                collectionItemIds = currentCollItemIds,
                sortOption = sortOption,
                groupOption = groupOption,
                isFiltering = query.isNotEmpty() || hiddenCategories.isNotEmpty() || hiddenCollectionIds.isNotEmpty(),
                isLoading = false
            )
        }.onEach { 
            _uiState.value = it 
        }.catch { e ->
            Log.e("InventoryListViewModel", "Error observing items", e)
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }.launchIn(viewModelScope)
    }

    private fun resolveLocations(
        items: List<InventoryItem>,
        links: List<ItemLink>,
        userLoc: Pair<Double, Double>?
    ): List<InventoryItem> {
        val itemMap = items.associateBy { it.id }
        val followerToLeader = links.associate { it.followerId to it.leaderId }

        return items.map { item ->
            if (item.location.isNotEmpty()) return@map item
            
            var currentId = item.id
            val visited = mutableSetOf<Long>()
            var resolvedLocation = ""
            var resolvedLat: Double? = null
            var resolvedLon: Double? = null

            while (followerToLeader.containsKey(currentId) && currentId !in visited) {
                visited.add(currentId)
                val leaderId = followerToLeader[currentId] ?: break
                val leader = itemMap[leaderId] ?: break
                
                if (leader.location.isNotEmpty()) {
                    resolvedLocation = leader.location
                    resolvedLat = leader.latitude
                    resolvedLon = leader.longitude
                    break
                }
                currentId = leaderId
            }

            if (resolvedLocation.isNotEmpty()) {
                item.copy(location = resolvedLocation, latitude = resolvedLat, longitude = resolvedLon)
            } else {
                item
            }
        }
    }

    private fun filterAndSortItems(
        items: List<InventoryItem>,
        query: String,
        activeCollectionId: Long?,
        currentCollItemIds: Set<Long>,
        hiddenCategories: Set<String>,
        hiddenCollections: Set<Long>,
        itemToCollections: Map<Long, List<Long>>,
        isHardFilter: Boolean,
        isInvertFilter: Boolean,
        sortOption: SortOption
    ): List<InventoryItem> {
        var result = items

        if (activeCollectionId != null) {
            result = result.filter { it.id in currentCollItemIds }
        }

        if (query.isNotEmpty()) {
            result = result.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.location.contains(query, ignoreCase = true) ||
                it.category?.contains(query, ignoreCase = true) == true
            }
        }

        result = result.filter { item ->
            val categoryHidden = item.category in hiddenCategories
            val itemColls = itemToCollections[item.id] ?: emptyList()
            val collectionHidden = itemColls.any { it in hiddenCollections }
            
            val shouldHide = if (isHardFilter) categoryHidden || collectionHidden else categoryHidden && collectionHidden
            
            if (isInvertFilter) shouldHide else !shouldHide
        }

        return when (sortOption) {
            SortOption.NAME_ASC -> result.sortedBy { it.name }
            SortOption.NAME_DESC -> result.sortedByDescending { it.name }
            SortOption.DATE_DESC -> result.sortedByDescending { it.updatedAt }
            SortOption.QUANTITY_DESC -> result.sortedByDescending { it.quantity }
            SortOption.PRICE_DESC -> result.sortedByDescending { it.price ?: 0.0 }
        }
    }

    private fun groupItems(
        items: List<InventoryItem>,
        option: GroupOption,
        collections: List<InventoryCollection>,
        itemToCollections: Map<Long, List<Long>>
    ): List<Pair<String, List<InventoryItem>>> {
        return when (option) {
            GroupOption.NONE -> emptyList()
            GroupOption.CATEGORY -> {
                items.groupBy { it.category ?: "Uncategorized" }.toList().sortedBy { it.first }
            }
            GroupOption.LOCATION -> {
                items.groupBy { it.location.ifEmpty { "Unknown Location" } }.toList().sortedBy { it.first }
            }
            GroupOption.COLLECTION -> {
                val collMap = collections.associateBy { it.id }
                val groups = mutableMapOf<String, MutableList<InventoryItem>>()
                items.forEach { item ->
                    val itemColls = itemToCollections[item.id] ?: emptyList()
                    if (itemColls.isEmpty()) {
                        groups.getOrPut("No Collection") { mutableListOf() }.add(item)
                    } else {
                        itemColls.forEach { collId ->
                            val name = collMap[collId]?.name ?: "Unknown"
                            groups.getOrPut(name) { mutableListOf() }.add(item)
                        }
                    }
                }
                groups.toList().sortedBy { it.first }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setCollectionId(collectionId: Long?) {
        _currentCollectionId.value = collectionId
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
            val next = if (category in current) current - category else current + category
            settingsRepository.saveHiddenCategories(next)
        }
    }

    fun toggleCollectionVisibility(collectionId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.hiddenCollections
            val next = if (collectionId in current) current - collectionId else current + collectionId
            settingsRepository.saveHiddenCollections(next.map { it.toString() }.toSet())
        }
    }

    fun setHardFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHardFilterEnabled(enabled)
        }
    }

    fun setInvertFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setInvertFilterEnabled(enabled)
        }
    }

    fun toggleItemExpansion(itemId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.expandedItemIds
            val next = if (itemId in current) current - itemId else current + itemId
            settingsRepository.saveExpandedItems(next.map { it.toString() }.toSet())
        }
    }

    fun expandAll() {
        viewModelScope.launch {
            val allIds = _uiState.value.items.map { it.id.toString() }.toSet()
            settingsRepository.saveExpandedItems(allIds)
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
            val item = repository.getItemById(itemId) ?: return@launch
            if (item.equipped) {
                if (repack && item.lastParentId != null) {
                    repository.moveItem(itemId, item.lastParentId)
                }
                repository.updateItemEquippedStatus(itemId, false)
            } else {
                repository.updateItemEquippedStatus(itemId, true)
            }
        }
    }

    suspend fun getContainerName(id: Long): String? {
        return repository.getItemById(id)?.name
    }

    fun toggleItemInCollection(itemId: Long, collectionId: Long) {
        viewModelScope.launch {
            if (_uiState.value.collectionItemIds.contains(itemId)) {
                collectionRepository.removeItemFromCollection(collectionId, itemId)
            } else {
                collectionRepository.addItemToCollection(collectionId, itemId)
            }
        }
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        repository.updateUserLocation(latitude, longitude)
    }

    fun moveItem(itemId: Long, newParentId: Long?) {
        viewModelScope.launch {
            repository.moveItem(itemId, newParentId)
        }
    }

    fun linkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            try {
                repository.addLink(followerId, leaderId)
            } catch (e: Exception) {
                Log.e("InventoryListViewModel", "Failed to link items", e)
            }
        }
    }

    fun triggerManualSync() {
        syncRepository.triggerFullSync()
    }
}
