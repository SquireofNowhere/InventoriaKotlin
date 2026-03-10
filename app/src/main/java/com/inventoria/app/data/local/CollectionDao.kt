package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface CollectionDao {
    @Query("SELECT * FROM InventoryCollection")
    fun getAllCollections(): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM InventoryCollection")
    suspend fun getAllCollectionsList(): List<InventoryCollection>

    @Query("SELECT * FROM InventoryCollection WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): InventoryCollection?

    @Query("SELECT * FROM InventoryCollection WHERE collectionType = :type")
    fun getCollectionsByType(type: InventoryCollectionType): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM InventoryCollection WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')")
    fun searchCollections(query: String): Flow<List<InventoryCollection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: InventoryCollection): Long

    @Update
    suspend fun updateCollection(collection: InventoryCollection)

    @Delete
    suspend fun deleteCollection(collection: InventoryCollection)

    // Collection Item Relationships
    @Query("SELECT * FROM InventoryCollectionItem")
    fun getAllCollectionItemsFlow(): Flow<List<InventoryCollectionItem>>

    @Query("SELECT * FROM InventoryCollectionItem")
    suspend fun getAllCollectionItemsList(): List<InventoryCollectionItem>

    @Query("SELECT * FROM InventoryCollectionItem WHERE collectionId = :collectionId")
    suspend fun getItemsForCollection(collectionId: Long): List<InventoryCollectionItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(collectionItem: InventoryCollectionItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItems(collectionItems: List<InventoryCollectionItem>)

    @Delete
    suspend fun deleteCollectionItem(collectionItem: InventoryCollectionItem)

    @Query("DELETE FROM InventoryCollectionItem WHERE collectionId = :collectionId AND itemId = :itemId")
    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long)

    @Query("DELETE FROM InventoryCollectionItem")
    suspend fun deleteAllCollectionItems()

    // Advanced Joins
    @Transaction
    @Query("SELECT * FROM InventoryCollection WHERE id = :id")
    fun getCollectionWithItems(id: Long): Flow<InventoryCollectionWithItems?>

    @Query("SELECT c.*, COUNT(ci.itemId) as itemCount FROM InventoryCollection c LEFT JOIN InventoryCollectionItem ci ON c.id = ci.collectionId GROUP BY c.id")
    fun getCollectionsWithCounts(): Flow<List<InventoryCollectionWithCount>>

    @Query("SELECT * FROM InventoryCollection WHERE id IN (SELECT collectionId FROM InventoryCollectionItem WHERE itemId = :itemId)")
    fun getCollectionsForItem(itemId: Long): Flow<List<InventoryCollection>>

    @Transaction
    @Query("SELECT * FROM InventoryCollection WHERE id = :collectionId")
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
