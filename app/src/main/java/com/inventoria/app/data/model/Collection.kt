package com.inventoria.app.data.model

import androidx.room.*

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
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "icon")
    val icon: String? = null,
    @ColumnInfo(name = "color")
    val color: Int,
    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),
    @ColumnInfo(name = "collection_type")
    val collectionType: InventoryCollectionType = InventoryCollectionType.OTHER,
    @ColumnInfo(name = "requires_same_location")
    val requiresSameLocation: Boolean = false,
    @ColumnInfo(name = "preferred_container_id")
    val preferredContainerId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
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
    val collectionId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "required_quantity")
    val requiredQuantity: Int = 1,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
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
