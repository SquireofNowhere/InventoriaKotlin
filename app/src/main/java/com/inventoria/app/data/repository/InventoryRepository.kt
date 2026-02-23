package com.inventoria.app.data.repository

import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory data operations using Room Database.
 * Provides a clean API for the rest of the app to interact with inventory data.
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao
) {
    /**
     * Returns a Flow of all inventory items, ordered by creation date descending.
     */
    fun getAllItems(): Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    
    /**
     * Retrieves a single item by its ID.
     */
    suspend fun getItemById(id: Long): InventoryItem? = inventoryDao.getItemById(id)
    
    /**
     * Returns a Flow of a single item by its ID for real-time updates.
     */
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = inventoryDao.getItemByIdFlow(id)
    
    /**
     * Searches items by name, location, or description.
     */
    fun searchItems(query: String): Flow<List<InventoryItem>> = inventoryDao.searchItems(query)
    
    /**
     * Returns items belonging to a specific category.
     */
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>> = inventoryDao.getItemsByCategory(category)
    
    /**
     * Returns items with zero quantity.
     */
    fun getOutOfStockItems(): Flow<List<InventoryItem>> = inventoryDao.getOutOfStockItems()
    
    /**
     * Returns a list of all unique categories used in the inventory.
     */
    fun getAllCategories(): Flow<List<String>> = inventoryDao.getAllCategories()
    
    /**
     * Returns the total number of items in the inventory.
     */
    fun getItemCount(): Flow<Int> = inventoryDao.getItemCount()
    
    /**
     * Calculates the total value of all items (price * quantity).
     */
    fun getTotalValue(): Flow<Double?> = inventoryDao.getTotalValue()
    
    /**
     * Inserts a new item into the database.
     */
    suspend fun insertItem(item: InventoryItem): Long {
        return inventoryDao.insertItem(item.copy(createdAt = Date(), updatedAt = Date()))
    }
    
    /**
     * Updates an existing item in the database.
     */
    suspend fun updateItem(item: InventoryItem) {
        inventoryDao.updateItem(item.copy(updatedAt = Date()))
    }
    
    /**
     * Deletes an item by its ID.
     */
    suspend fun deleteItemById(id: Long) {
        inventoryDao.deleteItemById(id)
    }

    /**
     * Updates only the quantity of an item.
     */
    suspend fun updateQuantity(id: Long, newQuantity: Int) {
        inventoryDao.updateQuantity(id, newQuantity)
    }
}
