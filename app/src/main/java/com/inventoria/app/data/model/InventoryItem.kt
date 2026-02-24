package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inventoria.app.data.local.Converters
import java.util.Date

/**
 * Represents an inventory item in the database
 */
@Entity(tableName = "inventory_items")
@TypeConverters(Converters::class)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val quantity: Int,
    val location: String, // Readable address
    val latitude: Double? = null, // Raw latitude
    val longitude: Double? = null, // Raw longitude
    val price: Double? = null,
    
    // Additional fields stored as key-value pairs
    val customFields: Map<String, String> = emptyMap(),
    
    // Metadata
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    
    // Categories and tags
    val category: String? = null,
    val tags: List<String> = emptyList(),
    
    // Optional fields
    val description: String? = null,
    val imageUrl: String? = null,
    val barcode: String? = null,
    val sku: String? = null
) {
    /**
     * Check if the item is currently in stock
     */
    fun isInStock(): Boolean = quantity > 0
    
    /**
     * Get display name with quantity
     */
    fun getDisplayName(): String = "$name ($quantity)"
    
    /**
     * Calculate total value if price is available
     */
    fun getTotalValue(): Double? = price?.let { it * quantity }
}

/**
 * UI state for inventory items
 */
data class InventoryItemUiState(
    val item: InventoryItem,
    val isSelected: Boolean = false,
    val isExpanded: Boolean = false
)

/**
 * Data class for creating/updating inventory items
 */
data class InventoryItemInput(
    val name: String,
    val quantity: Int,
    val location: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val price: Double? = null,
    val customFields: Map<String, String> = emptyMap(),
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null
) {
    fun toInventoryItem(id: Long = 0): InventoryItem {
        return InventoryItem(
            id = id,
            name = name,
            quantity = quantity,
            location = location,
            latitude = latitude,
            longitude = longitude,
            price = price,
            customFields = customFields,
            category = category,
            tags = tags,
            description = description
        )
    }
}
