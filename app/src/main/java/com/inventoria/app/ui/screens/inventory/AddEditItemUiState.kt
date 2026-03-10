package com.inventoria.app.ui.screens.inventory

import com.inventoria.app.data.model.InventoryItem
import org.osmdroid.util.GeoPoint

data class CustomField(
    val key: String,
    val value: String
)

data class AddEditItemUiState(
    val geoPoint: GeoPoint? = null,
    val address: String = "",
    val isResolvingAddress: Boolean = false,
    val storageItems: List<InventoryItem> = emptyList(),
    val isUploadingImage: Boolean = false,
    val isLoading: Boolean = false
)
