package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface CollectionDao {
    @Query("SELECT * FROM InventoryCollection WHERE isDirty = 1")
    fun getDirtyCollectionsFlow(): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM InventoryCollection WHERE isDirty = 1")
    suspend fun getDirtyCollectionsList(): List<InventoryCollection>

    @Query("UPDATE InventoryCollection SET isDirty = 0 WHERE id IN (:collectionIds)")
    suspend fun markCollectionsClean(collectionIds: List<Long>)

    @Query("SELECT * FROM InventoryCollection WHERE isDeleted = 0")
    fun getAllCollections(): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM InventoryCollection WHERE isDeleted = 0")
    suspend fun getAllCollectionsList(): List<InventoryCollection>

    // Unfiltered -- sync must push tombstones (isDeleted=1 rows) too, or a deletion never
    // reaches other devices/Firebase at all. See ErrorLog.md #33.
    @Query("SELECT * FROM InventoryCollection")
    suspend fun getAllCollectionsForSyncList(): List<InventoryCollection>

    // Unfiltered by design: used for sync merge-decision (comparing updatedAt against the
    // incoming cloud row), which needs the raw row -- tombstoned or not.
    @Query("SELECT * FROM InventoryCollection WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): InventoryCollection?

    @Query("SELECT * FROM InventoryCollection WHERE collectionType = :type AND isDeleted = 0")
    fun getCollectionsByType(type: InventoryCollectionType): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM InventoryCollection WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND isDeleted = 0")
    fun searchCollections(query: String): Flow<List<InventoryCollection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: InventoryCollection): Long

    @Update
    suspend fun updateCollection(collection: InventoryCollection)

    @Query("UPDATE InventoryCollection SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteCollection(id: Long, timestamp: Long)

    @Query("DELETE FROM InventoryCollection WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedCollections(threshold: Long)

    // Collection Item Relationships
    @Query("SELECT * FROM InventoryCollectionItem WHERE isDirty = 1")
    fun getDirtyCollectionItemsFlow(): Flow<List<InventoryCollectionItem>>

    @Query("SELECT * FROM InventoryCollectionItem WHERE isDirty = 1")
    suspend fun getDirtyCollectionItemsList(): List<InventoryCollectionItem>

    @Query("UPDATE InventoryCollectionItem SET isDirty = 0 WHERE collectionId = :collectionId AND itemId = :itemId")
    suspend fun markCollectionItemClean(collectionId: Long, itemId: Long)

    @Transaction
    suspend fun markCollectionItemsClean(items: List<InventoryCollectionItem>) {
        items.forEach { markCollectionItemClean(it.collectionId, it.itemId) }
    }

    @Query("SELECT * FROM InventoryCollectionItem WHERE isDeleted = 0")
    fun getAllCollectionItemsFlow(): Flow<List<InventoryCollectionItem>>

    // Unfiltered -- sync must push tombstones too, same reasoning as getAllCollectionsForSyncList.
    @Query("SELECT * FROM InventoryCollectionItem")
    suspend fun getAllCollectionItemsForSyncList(): List<InventoryCollectionItem>

    @Query("SELECT * FROM InventoryCollectionItem WHERE collectionId = :collectionId AND isDeleted = 0")
    suspend fun getItemsForCollection(collectionId: Long): List<InventoryCollectionItem>

    // Unfiltered by design: raw lookup used for sync merge-decision, same reasoning as getCollectionById.
    @Query("SELECT * FROM InventoryCollectionItem WHERE collectionId = :collectionId AND itemId = :itemId LIMIT 1")
    suspend fun getCollectionItem(collectionId: Long, itemId: Long): InventoryCollectionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(collectionItem: InventoryCollectionItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItems(collectionItems: List<InventoryCollectionItem>)

    @Query("UPDATE InventoryCollectionItem SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE collectionId = :collectionId AND itemId = :itemId")
    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long, timestamp: Long)

    @Query("DELETE FROM InventoryCollectionItem WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedCollectionItems(threshold: Long)

    // Advanced Joins
    @Transaction
    @Query("SELECT * FROM InventoryCollection WHERE id = :id AND isDeleted = 0")
    fun getCollectionWithItemsRaw(id: Long): Flow<InventoryCollectionWithItems?>

    // Room's @Relation/@Junction can't declaratively filter the junction table, so a
    // soft-deleted InventoryCollectionItem row would otherwise still show up here until it's
    // purged. Filtered here instead of at the SQL level.
    fun getCollectionWithItems(id: Long): Flow<InventoryCollectionWithItems?> =
        getCollectionWithItemsRaw(id).map { withItems ->
            withItems?.let { wi ->
                val liveCollectionItems = wi.collectionItems.filterNot { it.isDeleted }
                val liveItemIds = liveCollectionItems.map { it.itemId }.toSet()
                wi.copy(
                    items = wi.items.filter { it.id in liveItemIds },
                    collectionItems = liveCollectionItems
                )
            }
        }

    @Query("SELECT c.*, COUNT(ci.itemId) as itemCount FROM InventoryCollection c LEFT JOIN InventoryCollectionItem ci ON c.id = ci.collectionId AND ci.isDeleted = 0 WHERE c.isDeleted = 0 GROUP BY c.id")
    fun getCollectionsWithCounts(): Flow<List<InventoryCollectionWithCount>>

    @Query("SELECT * FROM InventoryCollection WHERE isDeleted = 0 AND id IN (SELECT collectionId FROM InventoryCollectionItem WHERE itemId = :itemId AND isDeleted = 0)")
    fun getCollectionsForItem(itemId: Long): Flow<List<InventoryCollection>>

    fun getCollectionReadiness(collectionId: Long): Flow<InventoryCollectionReadiness?> =
        getCollectionWithItems(collectionId).map { withItems ->
            withItems?.let {
                val total = it.collectionItems.size
                val items = it.items.associateBy { item -> item.id }

                var available = 0
                var packed = 0
                var equipped = 0

                it.collectionItems.forEach { ci ->
                    val item = items[ci.itemId]
                    if (item != null && item.quantity >= ci.requiredQuantity) {
                        available++
                        if (item.equipped) equipped++
                        if (item.parentId != null) packed++
                    }
                }

                InventoryCollectionReadiness(
                    collectionId = collectionId,
                    totalItems = total,
                    availableItems = available,
                    packedItems = packed,
                    equippedItems = equipped
                )
            }
        }
}
