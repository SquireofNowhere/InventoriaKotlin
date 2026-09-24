package com.inventoria.shared.model

import kotlinx.serialization.Serializable

/**
 * One node under users/$uid/items, keyed by [id]. Field names match what the Android app's
 * Firebase mapper writes, so both clients read and write the same rows.
 */
@Serializable
data class InventoryItem(
    val id: Long = 0L,
    val name: String = "",
    val quantity: Int = 0,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val price: Double? = null,
    val storage: Boolean = false,
    val parentId: Long? = null,
    val lastParentId: Long? = null,
    val equipped: Boolean = false,
    val customFields: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val category: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val profilePictureUrl: String? = null,
    val barcode: String? = null,
    val sku: String? = null,
    val isDeleted: Boolean = false
) {
    fun isInStock(): Boolean = quantity > 0

    fun getTotalValue(): Double? = price?.let { it * quantity }

    fun getPrimaryImage(): String? = profilePictureUrl ?: imageUrls.firstOrNull()

    fun getDisplayLocation(): String = if (equipped) "Equipped (On Person)" else location

    fun getParsedTags(): List<String> =
        category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}

/** users/$uid/item_links, keyed "${followerId}_${leaderId}". */
@Serializable
data class ItemLink(
    val followerId: Long = 0L,
    val leaderId: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)
