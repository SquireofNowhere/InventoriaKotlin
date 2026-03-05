package com.inventoria.app.data.model

import androidx.room.*
import com.google.firebase.database.PropertyName

enum class InventoryCollectionType {
    TRAVEL_KIT,
    WORK_GEAR,
    OUTFIT,
    EMERGENCY,
    HOBBY,
    OTHER
}

@Entity(tableName = "collections")
data class InventoryCollection(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: Long = 0,
    
    @ColumnInfo(name = "name")
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    
    @ColumnInfo(name = "description")
    @get:PropertyName("description")
    @set:PropertyName("description")
    var description: String? = null,
    
    @ColumnInfo(name = "icon")
    @get:PropertyName("icon")
    @set:PropertyName("icon")
    var icon: String? = null,
    
    @ColumnInfo(name = "color")
    @get:PropertyName("color")
    @set:PropertyName("color")
    var color: Int = 0,
    
    @ColumnInfo(name = "tags")
    @get:PropertyName("tags")
    @set:PropertyName("tags")
    var tags: List<String> = emptyList(),
    
    @ColumnInfo(name = "collection_type")
    @get:PropertyName("collectionType")
    @set:PropertyName("collectionType")
    var collectionType: InventoryCollectionType = InventoryCollectionType.OTHER,
    
    @ColumnInfo(name = "requires_same_location")
    @get:PropertyName("requiresSameLocation")
    @set:PropertyName("requiresSameLocation")
    var requiresSameLocation: Boolean = false,
    
    @ColumnInfo(name = "preferred_container_id")
    @get:PropertyName("preferredContainerId")
    @set:PropertyName("preferredContainerId")
    var preferredContainerId: Long? = null,
    
    @ColumnInfo(name = "created_at")
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collection_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = InventoryCollection::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryItem::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("item_id")]
)
data class InventoryCollectionItem(
    @ColumnInfo(name = "collection_id")
    @get:PropertyName("collectionId")
    @set:PropertyName("collectionId")
    var collectionId: Long = 0,
    
    @ColumnInfo(name = "item_id")
    @get:PropertyName("itemId")
    @set:PropertyName("itemId")
    var itemId: Long = 0,
    
    @ColumnInfo(name = "required_quantity")
    @get:PropertyName("requiredQuantity")
    @set:PropertyName("requiredQuantity")
    var requiredQuantity: Int = 1,
    
    @ColumnInfo(name = "notes")
    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String? = null,
    
    @ColumnInfo(name = "sort_order")
    @get:PropertyName("sortOrder")
    @set:PropertyName("sortOrder")
    var sortOrder: Int = 0,
    
    @ColumnInfo(name = "added_at")
    @get:PropertyName("addedAt")
    @set:PropertyName("addedAt")
    var addedAt: Long = System.currentTimeMillis()
)

data class InventoryCollectionWithCount(
    @Embedded val collection: InventoryCollection,
    @ColumnInfo(name = "itemCount")
    val itemCount: Int
)

data class InventoryCollectionWithItems(
    @Embedded val collection: InventoryCollection,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = InventoryCollectionItem::class,
            parentColumn = "collection_id",
            entityColumn = "item_id"
        )
    )
    val items: List<InventoryItem>,
    @Relation(
        parentColumn = "id",
        entityColumn = "collection_id"
    )
    val collectionItems: List<InventoryCollectionItem>
)

data class InventoryCollectionReadiness(
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,
    @ColumnInfo(name = "total_items")
    val totalItems: Int,
    @ColumnInfo(name = "available_items")
    val availableItems: Int,
    @ColumnInfo(name = "packed_items")
    val packedItems: Int,
    @ColumnInfo(name = "equipped_items")
    val equippedItems: Int
) {
    @Ignore
    val readinessPercentage: Float = if (totalItems == 0) 100f else (availableItems.toFloat() / totalItems) * 100f
}

sealed class PackResult {
    data class Success(val message: String, val packedItems: List<Long> = emptyList()) : PackResult()
    data class ValidationFailed(val errors: List<String>) : PackResult()
    data class Error(val message: String) : PackResult()
}
