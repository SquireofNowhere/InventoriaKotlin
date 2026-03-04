package com.inventoria.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
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
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

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
        
        // Get initial location fix
        repositoryScope.launch {
            val loc = getFreshLocation()
            if (loc != null) {
                _userLocation.value = loc
            }
        }
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLocation.value = latitude to longitude
    }

    /**
     * Attempts to get a fresh, accurate location fix.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            // Prefer current accurate location
            val currentLoc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (currentLoc != null) return@withContext currentLoc.latitude to currentLoc.longitude
            
            // Fallback to last known
            val lastLoc = fusedLocationClient.lastLocation.await()
            if (lastLoc != null) return@withContext lastLoc.latitude to lastLoc.longitude
            
            null
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error fetching fresh location", e)
            null
        }
    }

    fun getAllItems(): Flow<List<InventoryItem>> = inventoryDao.getAllItems()
    
    suspend fun getItemById(id: Long): InventoryItem? = withContext(Dispatchers.IO) {
        inventoryDao.getItemById(id)
    }
    
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = inventoryDao.getItemByIdFlow(id)
    
    suspend fun touchItem(id: Long) = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(id)
        if (item != null) {
            inventoryDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun searchItems(query: String): Flow<List<InventoryItem>> = inventoryDao.searchItems(query)
    
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>> = inventoryDao.getItemsByCategory(category)
    
    fun getOutOfStockItems(): Flow<List<InventoryItem>> = inventoryDao.getOutOfStockItems()
    
    fun getAllCategories(): Flow<List<String>> = inventoryDao.getAllCategories()
    
    fun getItemCount(): Flow<Int> = inventoryDao.getItemCount()
    
    fun getTotalValue(): Flow<Double?> = inventoryDao.getTotalValue()
    
    fun getStorageItems(): Flow<List<InventoryItem>> = inventoryDao.getStorageItems()
    
    fun getItemsByParent(parentId: Long): Flow<List<InventoryItem>> = inventoryDao.getItemsByParent(parentId)
    
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
    
    suspend fun insertItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        val finalItem = if (item.equipped) {
            item.copy(parentId = null)
        } else item
        
        val currentTime = System.currentTimeMillis()
        inventoryDao.insertItem(finalItem.copy(createdAt = currentTime, updatedAt = currentTime))
    }
    
    /**
     * Batch updates multiple items, optimized for performance and location fetching.
     */
    suspend fun updateItems(items: List<InventoryItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        
        val currentItems = inventoryDao.getItemsByIds(items.map { it.id }).associateBy { it.id }
        val currentTime = System.currentTimeMillis()

        // Determine if we need a fresh location fix for this batch
        var needsFreshLocation = false
        for (item in items) {
            val current = currentItems[item.id] ?: continue
            val isUnequipping = !item.equipped && current.equipped
            val isMovingToOpenSpace = item.parentId == null && current.parentId != null && !item.equipped
            if (isUnequipping || isMovingToOpenSpace) {
                needsFreshLocation = true
                break
            }
        }

        var batchLocation = _userLocation.value
        var batchAddress: String? = null

        if (needsFreshLocation) {
            val freshLoc = getFreshLocation()
            if (freshLoc != null) {
                batchLocation = freshLoc
                _userLocation.value = freshLoc
                batchAddress = reverseGeocode(freshLoc.first, freshLoc.second)
            }
        }

        val updatedItems = items.mapNotNull { item ->
            val current = currentItems[item.id] ?: return@mapNotNull null
            var updated = item.copy(updatedAt = currentTime)

            // 1. Handle Equipping Logic: Take out of container if parent is not also being equipped
            if (updated.equipped && !current.equipped) {
                if (current.parentId != null) {
                    val parentId = current.parentId!!
                    // Check if parent is also in this batch and being equipped
                    val isParentInBatchAndEquipped = items.any { it.id == parentId && it.equipped }
                    
                    if (!isParentInBatchAndEquipped) {
                        // Parent is NOT being equipped. Take item out and remember where it was.
                        updated = updated.copy(
                            parentId = null,
                            lastParentId = parentId
                        )
                    }
                }
            }

            // 2. Handle Unequipping Logic
            if (!updated.equipped && current.equipped) {
                // If the item has a parentId now, it's being put back (choice made by caller)
                // If it doesn't have a parentId, it's being left loose.
            }

            // 3. Inherit location logic
            if (updated.parentId != null) {
                // Check if parent is in this batch for location data
                val parentInBatch = items.find { it.id == updated.parentId }
                if (parentInBatch != null) {
                    if (parentInBatch.equipped && batchLocation != null) {
                        updated = updated.copy(
                            latitude = batchLocation.first,
                            longitude = batchLocation.second,
                            location = batchAddress ?: reverseGeocode(batchLocation.first, batchLocation.second)
                        )
                    } else {
                        updated = updated.copy(
                            latitude = parentInBatch.latitude,
                            longitude = parentInBatch.longitude,
                            location = parentInBatch.location
                        )
                    }
                } else {
                    // Check DB for parent
                    val parent = inventoryDao.getItemById(updated.parentId!!)
                    if (parent != null) {
                        if (parent.equipped && batchLocation != null) {
                            updated = updated.copy(
                                latitude = batchLocation.first,
                                longitude = batchLocation.second,
                                location = batchAddress ?: reverseGeocode(batchLocation.first, batchLocation.second)
                            )
                        } else {
                            updated = updated.copy(
                                latitude = parent.latitude,
                                longitude = parent.longitude,
                                location = parent.location
                            )
                        }
                    }
                }
            } else {
                // Not in a container
                val isUnequipping = !updated.equipped && current.equipped
                val isMovingToOpenSpace = updated.parentId == null && current.parentId != null && !updated.equipped
                
                if (!updated.equipped && (isUnequipping || isMovingToOpenSpace)) {
                    if (batchLocation != null) {
                        updated = updated.copy(
                            latitude = batchLocation.first,
                            longitude = batchLocation.second,
                            location = batchAddress ?: reverseGeocode(batchLocation.first, batchLocation.second)
                        )
                    }
                }
            }
            
            updated
        }

        inventoryDao.updateItems(updatedItems)
    }

    suspend fun updateItem(item: InventoryItem) = updateItems(listOf(item))

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
    
    suspend fun deleteItemById(id: Long) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItemById(id)
    }

    suspend fun updateQuantity(id: Long, newQuantity: Int) = withContext(Dispatchers.IO) {
        inventoryDao.updateQuantity(id, newQuantity)
    }
}
