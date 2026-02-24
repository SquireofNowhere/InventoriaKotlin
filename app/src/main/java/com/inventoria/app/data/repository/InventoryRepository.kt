package com.inventoria.app.data.repository

import android.content.Context
import android.location.Geocoder
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory data operations using Room Database.
 * Provides a clean API for the rest of the app to interact with inventory data.
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    @ApplicationContext private val context: Context
) {
    // Current user location to be used for equipped items
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    
    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLocation.value = latitude to longitude
    }

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
     * Returns all storage items.
     */
    fun getStorageItems(): Flow<List<InventoryItem>> = inventoryDao.getStorageItems()
    
    /**
     * Returns items within a storage container.
     */
    fun getItemsByParent(parentId: Long): Flow<List<InventoryItem>> = inventoryDao.getItemsByParent(parentId)
    
    /**
     * Returns all items with resolved locations.
     */
    fun getAllItemsWithResolvedLocations(): Flow<List<InventoryItem>> {
        return combine(getAllItems(), _userLocation) { items, userLoc ->
            val itemMap = items.associateBy { it.id }
            val resolvedCache = mutableMapOf<Long, Pair<Double?, Double?>>()
            val resolvedAddressCache = mutableMapOf<Long, String>()

            fun getResolvedLocation(item: InventoryItem): Triple<Double?, Double?, String> {
                if (resolvedCache.containsKey(item.id)) {
                    return Triple(resolvedCache[item.id]?.first, resolvedCache[item.id]?.second, resolvedAddressCache[item.id] ?: item.location)
                }

                if (item.isEquipped && userLoc != null) {
                    val res = Triple(userLoc.first, userLoc.second, "With You")
                    resolvedCache[item.id] = res.first to res.second
                    resolvedAddressCache[item.id] = res.third
                    return res
                }

                if (item.parentId != null) {
                    val parent = itemMap[item.parentId]
                    if (parent != null) {
                        val (pLat, pLon, pAddr) = getResolvedLocation(parent)
                        val res = Triple(pLat, pLon, pAddr)
                        resolvedCache[item.id] = res.first to res.second
                        resolvedAddressCache[item.id] = res.third
                        return res
                    }
                }

                val res = Triple(item.latitude, item.longitude, item.location)
                resolvedCache[item.id] = res.first to res.second
                resolvedAddressCache[item.id] = res.third
                return res
            }

            items.map { item ->
                val (rLat, rLon, rAddr) = getResolvedLocation(item)
                item.copy(
                    latitude = rLat,
                    longitude = rLon,
                    location = rAddr
                )
            }
        }
    }
    
    /**
     * Inserts a new item into the database.
     */
    suspend fun insertItem(item: InventoryItem): Long {
        val finalItem = if (item.isEquipped) {
            item.copy(parentId = null)
        } else item
        
        return inventoryDao.insertItem(finalItem.copy(createdAt = Date(), updatedAt = Date()))
    }
    
    /**
     * Updates an existing item in the database with logic for equipped/storage status.
     */
    suspend fun updateItem(item: InventoryItem) {
        val currentItem = inventoryDao.getItemById(item.id)
        var updatedItem = item.copy(updatedAt = Date())

        if (currentItem != null) {
            // Logic: if being EQUIPPED now (wasn't before)
            if (updatedItem.isEquipped && !currentItem.isEquipped) {
                updatedItem = updatedItem.copy(parentId = null)
            }
            
            // Logic: if being UNEQUIPPED now (was before)
            if (!updatedItem.isEquipped && currentItem.isEquipped) {
                val userLoc = _userLocation.value
                if (userLoc != null) {
                    val address = reverseGeocode(userLoc.first, userLoc.second)
                    updatedItem = updatedItem.copy(
                        latitude = userLoc.first,
                        longitude = userLoc.second,
                        location = address
                    )
                }
            }
        }

        inventoryDao.updateItem(updatedItem)
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                listOfNotNull(
                    addr.subThoroughfare,
                    addr.thoroughfare,
                    addr.subLocality,
                    addr.locality,
                    addr.countryName
                ).joinToString(", ")
            } else {
                "$latitude, $longitude"
            }
        } catch (e: Exception) {
            "$latitude, $longitude"
        }
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
