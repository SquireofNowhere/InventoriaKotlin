package com.inventoria.app.ui.screens.inventory

import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink

data class ItemDetailUiState(
    val item: InventoryItem? = null,
    val parentItem: InventoryItem? = null,
    val lastParentName: String? = null,
    val collections: List<InventoryCollection> = emptyList(),
    val availableContainers: List<InventoryItem> = emptyList(),
    val links: List<ItemLink> = emptyList(),
    val linkNames: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)
