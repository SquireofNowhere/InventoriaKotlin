package com.inventoria.app.data.repository

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val syncRepository: FirebaseSyncRepository,
    private val authRepository: FirebaseAuthRepository,
    @ApplicationContext private val context: Context
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current user location to be used for equipped items
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    
    init {
        // Initialize Firebase Sync
        repositoryScope.launch {
            try {
                authRepository.getOrCreateUserId()
                syncRepository.startSync()
                Log.d("InventoryRepository", "Firebase sync initialized successfully")
            } catch (e: Exception) {
                Log.e("InventoryRepository", "Failed to initialize Firebase sync", e)
            }
        }
    }

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
    suspend fun getItemById(id: Long): InventoryItem? = withContext(Dispatchers.IO) {
        inventoryDao.getItemById(id)
    }
    
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
            val resolvedCache = mutableMapOf<Long, Triple<Double?, Double?, String>>()

            fun getResolvedLocation(item: InventoryItem, visited: Set<Long> = emptySet()): Triple<Double?, Double?, String> {
                resolvedCache[item.id]?.let { return it }

                if (visited.contains(item.id)) {
                    Log.w("InventoryRepository", "Circular dependency detected for item ${item.id}")
                    return Triple(item.latitude, item.longitude, item.location)
                }

                val currentVisited = visited + item.id

                val result = when {
                    item.equipped && userLoc != null -> {
                        Triple(userLoc.first, userLoc.second, "With You")
                    }
                    item.parentId != null -> {
                        val parent = itemMap[item.parentId]
                        if (parent != null) {
                            getResolvedLocation(parent, currentVisited)
                        } else {
                            Triple(item.latitude, item.longitude, item.location)
                        }
                    }
                    else -> {
                        Triple(item.latitude, item.longitude, item.location)
                    }
                }

                resolvedCache[item.id] = result
                return result
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
    suspend fun insertItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        val finalItem = if (item.equipped) {
            item.copy(parentId = null)
        } else item
        
        val currentTime = System.currentTimeMillis()
        inventoryDao.insertItem(finalItem.copy(createdAt = currentTime, updatedAt = currentTime))
    }
    
    /**
     * Updates an existing item in the database with logic for equipped/storage status.
     */
    suspend fun updateItem(item: InventoryItem) = withContext(Dispatchers.IO) {
        val currentItem = inventoryDao.getItemById(item.id) ?: return@withContext
        var updatedItem = item.copy(updatedAt = System.currentTimeMillis())

        // Logic: if being EQUIPPED now (wasn't before)
        if (updatedItem.equipped && !currentItem.equipped) {
            updatedItem = updatedItem.copy(parentId = null)
        }
        
        // Logic: if being UNEQUIPPED now (was before)
        if (!updatedItem.equipped && currentItem.equipped) {
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
    suspend fun deleteItemById(id: Long) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItemById(id)
    }

    /**
     * Updates only the quantity of an item.
     */
    suspend fun updateQuantity(id: Long, newQuantity: Int) = withContext(Dispatchers.IO) {
        inventoryDao.updateQuantity(id, newQuantity)
    }
}
