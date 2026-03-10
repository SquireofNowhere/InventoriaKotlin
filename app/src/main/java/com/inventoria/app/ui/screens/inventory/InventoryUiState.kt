package com.inventoria.app.ui.screens.inventory

import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val matchedItemIds: Set<Long> = emptySet(),
    val expandedItemIds: Set<Long> = emptySet(),
    val itemDepths: Map<Long, Int> = emptyMap(),
    val itemHasChildren: Map<Long, Boolean> = emptyMap(),
    val selectedItemIds: Set<Long> = emptySet(),
    val allLinks: List<ItemLink> = emptyList(),
    val linkedItemIds: Set<Long> = emptySet(),
    val groupedItems: List<Pair<String, List<InventoryItem>>> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val allCollections: List<InventoryCollection> = emptyList(),
    // Active filters (Tags and Collections)
    val activeTags: Set<String> = emptySet(),
    val activeCollections: Set<Long> = emptySet(),
    val isHardFilterEnabled: Boolean = true,
    val isInvertFilterEnabled: Boolean = false,
    val collectionItemIds: Set<Long> = emptySet(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val groupOption: GroupOption = GroupOption.NONE,
    val isFiltering: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
