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
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())

    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus

    init {
        observeItems()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
            _selectedItemIds
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
            val activeTags = args[7] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val activeCollectionsStrings = args[8] as Set<String>
            val isHardFilter = args[9] as Boolean
            val isInvertFilter = args[10] as Boolean
            @Suppress("UNCHECKED_CAST")
            val expandedItemIdsStrings = args[11] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val allLinks = args[12] as List<ItemLink>
            @Suppress("UNCHECKED_CAST")
            val selectedIds = args[13] as Set<Long>

            val sortOption = SortOption.valueOf(sortOptionStr)
            val groupOption = GroupOption.valueOf(groupOptionStr)
            val expandedItemIds = expandedItemIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()
            val activeCollections = activeCollectionsStrings.mapNotNull { it.toLongOrNull() }.toSet()

            val resolvedItems = repository.resolveLocations(allItems, allLinks)

            // 1. Identify items that explicitly match filters
            val matchedItems = filterAndSortItems(
                resolvedItems, query, _currentCollectionId.value, currentCollItemIds, activeTags, 
                activeCollections, itemToCollections, isHardFilter, isInvertFilter, sortOption
            )
            val matchedItemIds = matchedItems.map { it.id }.toSet()
            val isFiltering = query.isNotEmpty() || activeTags.isNotEmpty() || activeCollections.isNotEmpty()

            val itemDepths = mutableMapOf<Long, Int>()
            val itemHasChildren = mutableMapOf<Long, Boolean>()
            val finalItems: List<InventoryItem>

            // 2. Build Hierarchy preserving matches and their ancestors
            if (groupOption == GroupOption.NONE) {
                val hierarchy = buildHierarchy(resolvedItems, matchedItemIds, expandedItemIds, sortOption, isFiltering)
                finalItems = hierarchy.items
                itemDepths.putAll(hierarchy.depths)
                itemHasChildren.putAll(hierarchy.hasChildren)
            } else {
                finalItems = matchedItems
            }

            val grouped = if (groupOption == GroupOption.NONE) emptyList() else groupItems(finalItems, groupOption, allCollections, itemToCollections)

            InventoryUiState(
                items = allItems,
                filteredItems = finalItems,
                matchedItemIds = matchedItemIds,
                expandedItemIds = expandedItemIds,
                itemDepths = itemDepths,
                itemHasChildren = itemHasChildren,
                selectedItemIds = selectedIds,
                allLinks = allLinks,
                linkedItemIds = allLinks.map { it.followerId }.toSet(),
                groupedItems = grouped,
                allCategories = allItems.flatMap { it.getParsedTags() }.distinct().sorted(),
                allCollections = allCollections,
                activeTags = activeTags,
                activeCollections = activeCollections,
                isHardFilterEnabled = isHardFilter,
                isInvertFilterEnabled = isInvertFilter,
                collectionItemIds = currentCollItemIds,
                sortOption = sortOption,
                groupOption = groupOption,
                isFiltering = isFiltering,
                isLoading = false
            )
        }.onEach { 
            _uiState.value = it 
        }.catch { e ->
            Log.e("InventoryListViewModel", "Error observing items", e)
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }.launchIn(viewModelScope)
    }

    private data class HierarchyResult(
        val items: List<InventoryItem>,
        val depths: Map<Long, Int>,
        val hasChildren: Map<Long, Boolean>
    )

    private fun buildHierarchy(
        allItems: List<InventoryItem>,
        matchedIds: Set<Long>,
        expandedIds: Set<Long>,
        sortOption: SortOption,
        isFiltering: Boolean
    ): HierarchyResult {
        val childrenMap = allItems.groupBy { it.parentId }
        val resultItems = mutableListOf<InventoryItem>()
        val depths = mutableMapOf<Long, Int>()
        val hasChildren = mutableMapOf<Long, Boolean>()

        // Helper to check if an item or any of its descendants match explicitly
        val memoMatch = mutableMapOf<Long, Boolean>()
        fun doesMatchOrHaveMatchingDescendant(itemId: Long): Boolean {
            if (memoMatch.containsKey(itemId)) return memoMatch[itemId]!!
            
            val matches = matchedIds.contains(itemId)
            val children = childrenMap[itemId] ?: emptyList()
            val descendantMatches = children.any { doesMatchOrHaveMatchingDescendant(it.id) }
            
            val result = matches || descendantMatches
            memoMatch[itemId] = result
            return result
        }

        fun addChildren(parentId: Long?, depth: Int, ancestorMatched: Boolean) {
            val children = childrenMap[parentId] ?: return
            
            // If filtering, include items that match, have matching descendants, OR if an ancestor matched
            val filteredChildren = if (isFiltering && !ancestorMatched) {
                children.filter { doesMatchOrHaveMatchingDescendant(it.id) }
            } else {
                children
            }

            val sortedChildren = when (sortOption) {
                SortOption.NAME_ASC -> filteredChildren.sortedBy { it.name }
                SortOption.NAME_DESC -> filteredChildren.sortedByDescending { it.name }
                SortOption.DATE_DESC -> filteredChildren.sortedByDescending { it.updatedAt }
                SortOption.QUANTITY_DESC -> filteredChildren.sortedByDescending { it.quantity }
                SortOption.PRICE_DESC -> filteredChildren.sortedByDescending { it.price ?: 0.0 }
            }

            for (child in sortedChildren) {
                resultItems.add(child)
                depths[child.id] = depth
                
                val childOrAncestorMatched = ancestorMatched || matchedIds.contains(child.id)
                
                val grandChildren = childrenMap[child.id]
                val visibleGrandChildren = if (isFiltering && !childOrAncestorMatched) {
                    grandChildren?.filter { doesMatchOrHaveMatchingDescendant(it.id) }
                } else {
                    grandChildren
                }
                
                hasChildren[child.id] = !visibleGrandChildren.isNullOrEmpty()
                
                // Expand if:
                // 1. User expanded it manually
                // 2. OR we are filtering and it has explicitly matching descendants (auto-reveal)
                val autoReveal = isFiltering && !ancestorMatched && !visibleGrandChildren.isNullOrEmpty()
                val shouldExpand = expandedIds.contains(child.id) || autoReveal
                
                if (shouldExpand) {
                    addChildren(child.id, depth + 1, childOrAncestorMatched)
                }
            }
        }

        addChildren(null, 0, false)
        return HierarchyResult(resultItems, depths, hasChildren)
    }

    private fun filterAndSortItems(
        items: List<InventoryItem>,
        query: String,
        activeCollectionId: Long?,
        currentCollItemIds: Set<Long>,
        selectedTags: Set<String>,
        selectedCollections: Set<Long>,
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

        // Pass-through filtering logic
        if (selectedTags.isNotEmpty() || selectedCollections.isNotEmpty()) {
            result = result.filter { item ->
                val itemTags = item.getParsedTags().map { it.lowercase() }
                val targetTags = selectedTags.map { it.lowercase() }
                
                val itemColls = itemToCollections[item.id] ?: emptyList()
                
                val tagMatched = if (targetTags.isEmpty()) true else {
                    if (isHardFilter) {
                        // ALL selected tags must be present
                        targetTags.all { it in itemTags }
                    } else {
                        // ANY of the selected tags must be present
                        targetTags.any { it in itemTags }
                    }
                }
                
                val collectionMatched = if (selectedCollections.isEmpty()) true else {
                    if (isHardFilter) {
                        // Must be in ALL selected collections
                        selectedCollections.all { it in itemColls }
                    } else {
                        // Must be in ANY of the selected collections
                        selectedCollections.any { it in itemColls }
                    }
                }
                
                // Logic: An item must satisfy both the tag requirement AND the collection requirement
                // if both filters are active.
                val matched = tagMatched && collectionMatched
                
                if (isInvertFilter) !matched else matched
            }
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
                items.groupBy { it.getDisplayLocation().ifEmpty { "Unknown Location" } }.toList().sortedBy { it.first }
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
            val current = _uiState.value.activeTags
            val next = if (category in current) current - category else current + category
            settingsRepository.saveHiddenCategories(next)
        }
    }

    fun toggleCollectionVisibility(collectionId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.activeCollections
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
            repository.setItemEquipped(itemId, !item.equipped, repack)
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

    fun toggleSelection(itemId: Long) {
        val current = _selectedItemIds.value
        _selectedItemIds.value = if (itemId in current) current - itemId else current + itemId
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            repository.deleteItemById(itemId)
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            val idsToDelete = _selectedItemIds.value
            idsToDelete.forEach { id ->
                repository.deleteItemById(id)
            }
            clearSelection()
        }
    }

    fun mergeSelectedItems(newName: String) {
        viewModelScope.launch {
            val selectedIds = _selectedItemIds.value
            if (selectedIds.size < 2) return@launch
            
            val selectedItems = _uiState.value.items.filter { it.id in selectedIds }
            if (selectedItems.isEmpty()) return@launch
            
            // Use the first item as the base for the merged item
            val baseItem = selectedItems.first()
            val totalQuantity = selectedItems.sumOf { it.quantity }
            
            // Merge custom fields
            val mergedCustomFields = mutableMapOf<String, String>()
            selectedItems.forEach { item ->
                mergedCustomFields.putAll(item.customFields)
            }
            
            // Concatenate descriptions if they exist
            val mergedDescription = selectedItems.mapNotNull { it.description }.distinct().joinToString("\n---\n")
            
            val mergedItem = baseItem.copy(
                name = newName,
                quantity = totalQuantity,
                description = if (mergedDescription.isBlank()) null else mergedDescription,
                customFields = mergedCustomFields,
                updatedAt = System.currentTimeMillis()
            )
            
            // Update the base item and delete the others
            repository.updateItem(mergedItem)
            selectedIds.filter { it != baseItem.id }.forEach { id ->
                repository.deleteItemById(id)
            }
            
            clearSelection()
        }
    }

    fun triggerManualSync() {
        syncRepository.triggerFullSync()
    }

    suspend fun syncOnAppOpen() {
        syncRepository.syncOnAppOpen()
    }
}
