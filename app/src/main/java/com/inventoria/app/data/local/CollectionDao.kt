package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<InventoryCollection>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): InventoryCollection?

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :id")
    fun getCollectionWithItems(id: Long): Flow<InventoryCollectionWithItems?>

    @Query("""
        SELECT c.*, COUNT(ci.item_id) as itemCount 
        FROM collections c 
        LEFT JOIN collection_items ci ON c.id = ci.collection_id 
        GROUP BY c.id 
        ORDER BY c.name ASC
    """)
    fun getCollectionsWithCounts(): Flow<List<InventoryCollectionWithCount>>

    @Query("SELECT * FROM collections WHERE collection_type = :type ORDER BY name ASC")
    fun getCollectionsByType(type: InventoryCollectionType): Flow<List<InventoryCollection>>

    @Query("""
        SELECT * FROM collections 
        WHERE name LIKE '%' || :query || '%' 
        OR tags LIKE '%' || :query || '%'
    """)
    fun searchCollections(query: String): Flow<List<InventoryCollection>>

    @Upsert
    suspend fun insertCollection(collection: InventoryCollection): Long

    @Update
    suspend fun updateCollection(collection: InventoryCollection)

    @Delete
    suspend fun deleteCollection(collection: InventoryCollection)

    @Upsert
    suspend fun insertCollectionItem(collectionItem: InventoryCollectionItem)

    @Delete
    suspend fun deleteCollectionItem(collectionItem: InventoryCollectionItem)

    @Query("DELETE FROM collection_items WHERE collection_id = :collectionId AND item_id = :itemId")
    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long)

    @Query("SELECT * FROM collection_items WHERE collection_id = :collectionId")
    suspend fun getItemsForCollection(collectionId: Long): List<InventoryCollectionItem>

    @Query("""
        SELECT 
            :collectionId as collection_id,
            COUNT(ci.item_id) as total_items,
            SUM(CASE WHEN i.quantity >= ci.required_quantity THEN 1 ELSE 0 END) as available_items,
            SUM(CASE WHEN i.parent_id IS NOT NULL THEN 1 ELSE 0 END) as packed_items,
            SUM(CASE WHEN i.equipped = 1 THEN 1 ELSE 0 END) as equipped_items
        FROM collection_items ci
        JOIN inventory_items i ON ci.item_id = i.id
        WHERE ci.collection_id = :collectionId
    """)
    fun getCollectionReadiness(collectionId: Long): Flow<InventoryCollectionReadiness>

    @Query("""
        SELECT c.* FROM collections c
        JOIN collection_items ci ON c.id = ci.collection_id
        WHERE ci.item_id = :itemId
    """)
    fun getCollectionsForItem(itemId: Long): Flow<List<InventoryCollection>>
}
