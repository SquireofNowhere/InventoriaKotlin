package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM InventoryItem WHERE isDeleted = 0")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem ORDER BY updatedAt DESC")
    fun getAllItemsForSync(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE isDirty = 1")
    fun getDirtyItemsFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE isDirty = 1")
    suspend fun getDirtyItemsList(): List<InventoryItem>

    @Query("UPDATE InventoryItem SET isDirty = 0 WHERE id IN (:itemIds)")
    suspend fun markItemsClean(itemIds: List<Long>)

    @Query("SELECT * FROM InventoryItem")
    suspend fun getAllItemsForSyncList(): List<InventoryItem>

    @Query("SELECT * FROM InventoryItem WHERE isDeleted = 0")
    suspend fun getAllItemsList(): List<InventoryItem>

    @Query("SELECT * FROM InventoryItem WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): InventoryItem?

    @Query("SELECT * FROM InventoryItem WHERE id = :itemId LIMIT 1")
    fun getItemByIdFlow(itemId: Long): Flow<InventoryItem?>

    @Query("SELECT * FROM InventoryItem WHERE id IN (:itemIds)")
    suspend fun getItemsByIds(itemIds: List<Long>): List<InventoryItem>

    @Query("SELECT * FROM InventoryItem WHERE parentId = :parentId AND isDeleted = 0")
    fun getItemsByParent(parentId: Long): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE category = :category AND isDeleted = 0")
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE location = :location AND isDeleted = 0")
    fun getItemsByLocation(location: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE storage = 1 AND isDeleted = 0")
    fun getStorageItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem WHERE quantity <= 0 AND isDeleted = 0")
    fun getOutOfStockItems(): Flow<List<InventoryItem>>

    @Query("SELECT DISTINCT category FROM InventoryItem WHERE category IS NOT NULL AND isDeleted = 0")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT location FROM InventoryItem WHERE location != '' AND isDeleted = 0")
    fun getAllLocations(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM InventoryItem WHERE isDeleted = 0")
    fun getItemCount(): Flow<Int>

    @Query("SELECT SUM(price * quantity) FROM InventoryItem WHERE isDeleted = 0")
    fun getTotalValue(): Flow<Double?>

    @Query("SELECT * FROM InventoryItem WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%') AND isDeleted = 0")
    fun searchItems(query: String): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>)

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Update
    suspend fun updateItems(items: List<InventoryItem>)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("UPDATE InventoryItem SET isDeleted = 1, updatedAt = :updateTime, isDirty = 1 WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE InventoryItem SET quantity = :newQuantity, updatedAt = :updateTime, isDirty = 1 WHERE id = :itemId")
    suspend fun updateQuantity(itemId: Long, newQuantity: Int, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE InventoryItem SET quantity = quantity + :amount, updatedAt = :updateTime, isDirty = 1 WHERE id = :itemId")
    suspend fun incrementQuantity(itemId: Long, amount: Int, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE InventoryItem SET quantity = quantity - :amount, updatedAt = :updateTime, isDirty = 1 WHERE id = :itemId")
    suspend fun decrementQuantity(itemId: Long, amount: Int, updateTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM InventoryItem")
    suspend fun deleteAllItems()
}
