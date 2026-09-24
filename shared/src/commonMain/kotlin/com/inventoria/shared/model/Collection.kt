package com.inventoria.shared.model

import kotlinx.serialization.Serializable

/** users/$uid/collections, keyed by [id]. */
@Serializable
data class InventoryCollection(
    val id: Long = 0L,
    val name: String = "",
    val description: String? = null,
    val icon: String? = null,
    val color: Int = 0,
    val tags: List<String> = emptyList(),
    val collectionType: InventoryCollectionType = InventoryCollectionType.OTHER,
    val requiresSameLocation: Boolean = false,
    val preferredContainerId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

@Serializable
enum class InventoryCollectionType {
    TRAVEL_KIT, WORK_GEAR, OUTFIT, EMERGENCY, HOBBY, OTHER
}

/** users/$uid/collection_items, keyed "${collectionId}_${itemId}". */
@Serializable
data class InventoryCollectionItem(
    val collectionId: Long = 0L,
    val itemId: Long = 0L,
    val requiredQuantity: Int = 1,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class InventoryCollectionReadiness(
    val collectionId: Long,
    val totalItems: Int,
    val availableItems: Int,
    val packedItems: Int,
    val equippedItems: Int
) {
    val readinessPercentage: Float =
        if (totalItems != 0) (availableItems.toFloat() / totalItems) * 100f else 100f
}
