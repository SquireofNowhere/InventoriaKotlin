package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for inventory items
 */
@Dao
interface InventoryDao {
    
    // Query all items
    @Query("SELECT * FROM inventory_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<InventoryItem>>
    
    // Query by ID
    @Query("SELECT * FROM inventory_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): InventoryItem?
    
    // Query by ID as Flow
    @Query("SELECT * FROM inventory_items WHERE id = :itemId")
    fun getItemByIdFlow(itemId: Long): Flow<InventoryItem?>
    
    // Search items
    @Query("""
        SELECT * FROM inventory_items 
        WHERE name LIKE '%' || :query || '%' 
        OR location LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchItems(query: String): Flow<List<InventoryItem>>
    
    // Filter by category
    @Query("SELECT * FROM inventory_items WHERE category = :category ORDER BY name ASC")
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>>
    
    // Get items by storage container
    @Query("SELECT * FROM inventory_items WHERE parentId = :parentId ORDER BY name ASC")
    fun getItemsByParent(parentId: Long): Flow<List<InventoryItem>>
    
    // Get all storage containers - Updated column name
    @Query("SELECT * FROM inventory_items WHERE storage = 1 ORDER BY name ASC")
    fun getStorageItems(): Flow<List<InventoryItem>>
    
    // Get out of stock items
    @Query("SELECT * FROM inventory_items WHERE quantity = 0 ORDER BY name ASC")
    fun getOutOfStockItems(): Flow<List<InventoryItem>>
    
    // Get items by location
    @Query("SELECT * FROM inventory_items WHERE location = :location ORDER BY name ASC")
    fun getItemsByLocation(location: String): Flow<List<InventoryItem>>
    
    // Get all categories
    @Query("SELECT DISTINCT category FROM inventory_items WHERE category IS NOT NULL ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
    
    // Get all locations
    @Query("SELECT DISTINCT location FROM inventory_items ORDER BY location ASC")
    fun getAllLocations(): Flow<List<String>>
    
    // Get total item count
    @Query("SELECT COUNT(*) FROM inventory_items")
    fun getItemCount(): Flow<Int>
    
    // Get total value
    @Query("SELECT SUM(price * quantity) FROM inventory_items WHERE price IS NOT NULL")
    fun getTotalValue(): Flow<Double?>
    
    // Insert item
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long
    
    // Insert multiple items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>)
    
    // Update item
    @Update
    suspend fun updateItem(item: InventoryItem)
    
    // Delete item
    @Delete
    suspend fun deleteItem(item: InventoryItem)
    
    // Delete by ID
    @Query("DELETE FROM inventory_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)
    
    // Delete all items
    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllItems()
    
    // Update quantity
    @Query("UPDATE inventory_items SET quantity = :newQuantity, updatedAt = :updateTime WHERE id = :itemId")
    suspend fun updateQuantity(itemId: Long, newQuantity: Int, updateTime: Long = System.currentTimeMillis())
    
    // Increment quantity
    @Query("UPDATE inventory_items SET quantity = quantity + :amount, updatedAt = :updateTime WHERE id = :itemId")
    suspend fun incrementQuantity(itemId: Long, amount: Int, updateTime: Long = System.currentTimeMillis())
    
    // Decrement quantity
    @Query("UPDATE inventory_items SET quantity = quantity - :amount, updatedAt = :updateTime WHERE id = :itemId")
    suspend fun decrementQuantity(itemId: Long, amount: Int, updateTime: Long = System.currentTimeMillis())
}
