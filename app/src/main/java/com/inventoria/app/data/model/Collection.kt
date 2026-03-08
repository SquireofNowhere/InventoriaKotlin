package com.inventoria.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.google.firebase.database.PropertyName

@Entity
data class InventoryCollection(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: Long = 0L,
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("description") @set:PropertyName("description") var description: String? = null,
    @get:PropertyName("icon") @set:PropertyName("icon") var icon: String? = null,
    @get:PropertyName("color") @set:PropertyName("color") var color: Int = 0,
    @get:PropertyName("tags") @set:PropertyName("tags") var tags: List<String> = emptyList(),
    @get:PropertyName("collectionType") @set:PropertyName("collectionType") var collectionType: InventoryCollectionType = InventoryCollectionType.OTHER,
    @get:PropertyName("requiresSameLocation") @set:PropertyName("requiresSameLocation") var requiresSameLocation: Boolean = false,
    @get:PropertyName("preferredContainerId") @set:PropertyName("preferredContainerId") var preferredContainerId: Long? = null,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
)

enum class InventoryCollectionType {
    TRAVEL_KIT, WORK_GEAR, OUTFIT, EMERGENCY, HOBBY, OTHER
}

@Entity(primaryKeys = ["collectionId", "itemId"])
data class InventoryCollectionItem(
    @get:PropertyName("collectionId") @set:PropertyName("collectionId") var collectionId: Long = 0L,
    @get:PropertyName("itemId") @set:PropertyName("itemId") var itemId: Long = 0L,
    @get:PropertyName("requiredQuantity") @set:PropertyName("requiredQuantity") var requiredQuantity: Int = 1,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String? = null,
    @get:PropertyName("sortOrder") @set:PropertyName("sortOrder") var sortOrder: Int = 0,
    @get:PropertyName("addedAt") @set:PropertyName("addedAt") var addedAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
)

data class InventoryCollectionWithItems(
    @Embedded val collection: InventoryCollection,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = InventoryCollectionItem::class,
            parentColumn = "collectionId",
            entityColumn = "itemId"
        )
    )
    val items: List<InventoryItem>,
    @Relation(
        parentColumn = "id",
        entityColumn = "collectionId"
    )
    val collectionItems: List<InventoryCollectionItem>
)

data class InventoryCollectionWithCount(
    @Embedded val collection: InventoryCollection,
    val itemCount: Int
)

data class InventoryCollectionReadiness(
    val collectionId: Long,
    val totalItems: Int,
    val availableItems: Int,
    val packedItems: Int,
    val equippedItems: Int
) {
    val readinessPercentage: Float = if (totalItems != 0) (availableItems.toFloat() / totalItems) * 100f else 100f
}
