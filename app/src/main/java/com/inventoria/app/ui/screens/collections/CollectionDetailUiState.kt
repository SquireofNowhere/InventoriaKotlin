package com.inventoria.app.ui.screens.collections

import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink

data class CollectionDetailUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val matchedItemIds: Set<Long> = emptySet(),
    val expandedItemIds: Set<Long> = emptySet(),
    val allLinks: List<ItemLink> = emptyList(),
    val linkedItemIds: Set<Long> = emptySet(),
    val collectionItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)
