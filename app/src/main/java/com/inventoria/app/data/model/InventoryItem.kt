package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.PropertyName

@Entity
data class InventoryItem(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: Long = 0L,
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("location") @set:PropertyName("location") var location: String = "",
    @get:PropertyName("latitude") @set:PropertyName("latitude") var latitude: Double? = null,
    @get:PropertyName("longitude") @set:PropertyName("longitude") var longitude: Double? = null,
    @get:PropertyName("price") @set:PropertyName("price") var price: Double? = null,
    @get:PropertyName("storage") @set:PropertyName("storage") var storage: Boolean = false,
    @get:PropertyName("parentId") @set:PropertyName("parentId") var parentId: Long? = null,
    @get:PropertyName("lastParentId") @set:PropertyName("lastParentId") var lastParentId: Long? = null,
    @get:PropertyName("equipped") @set:PropertyName("equipped") var equipped: Boolean = false,
    @get:PropertyName("customFields") @set:PropertyName("customFields") var customFields: Map<String, String> = emptyMap(),
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:PropertyName("category") @set:PropertyName("category") var category: String? = null,
    @get:PropertyName("tags") @set:PropertyName("tags") var tags: List<String> = emptyList(),
    @get:PropertyName("description") @set:PropertyName("description") var description: String? = null,
    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl") var imageUrl: String? = null,
    @get:PropertyName("barcode") @set:PropertyName("barcode") var barcode: String? = null,
    @get:PropertyName("sku") @set:PropertyName("sku") var sku: String? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
) {
    fun isInStock(): Boolean = quantity > 0
    
    fun getDisplayName(): String = "$name ($quantity)"
    
    fun getTotalValue(): Double? = price?.let { it * quantity }
}
