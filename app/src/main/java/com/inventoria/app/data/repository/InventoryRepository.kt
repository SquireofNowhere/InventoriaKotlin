package com.inventoria.app.data.repository

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.ItemLinkDao
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val itemLinkDao: ItemLinkDao,
    private val syncRepository: FirebaseSyncRepository,
    private val authRepository: FirebaseAuthRepository,
    private val storageRepository: FirebaseStorageRepository,
    @ApplicationContext private val context: Context
) {
    val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            try {
                authRepository.getOrCreateUserId()
                syncRepository.startSync()
                syncRepository.syncOnAppOpen()
                Log.d("InventoryRepository", "Firebase sync initialized successfully")
            } catch (e: Exception) {
                Log.e("InventoryRepository", "Failed to initialize Firebase sync", e)
            }
        }

        repositoryScope.launch {
            getFreshLocation()?.let {
                _userLocation.value = it
            }
        }
    }

    fun uploadImagesInBackground(
        itemId: Long,
        pendingUris: List<android.net.Uri>,
        profileUri: android.net.Uri?,
        existingUrls: List<String>,
        currentProfileUrl: String?
    ) {
        repositoryScope.launch {
            val finalImageUrls = existingUrls.toMutableList()
            var finalProfilePictureUrl = currentProfileUrl

            pendingUris.forEach { uri ->
                val result = storageRepository.uploadItemImage(uri)
                if (result.isSuccess) {
                    val url = result.getOrNull()
                    if (url != null) {
                        finalImageUrls.add(url)
                        if (uri == profileUri) {
                            finalProfilePictureUrl = url
                        }
                    }
                }
            }

            // Update item with new URLs
            getItemById(itemId)?.let { item ->
                updateItem(item.copy(
                    imageUrls = finalImageUrls,
                    profilePictureUrl = finalProfilePictureUrl ?: item.profilePictureUrl ?: finalImageUrls.firstOrNull()
                ))
            }
        }
    }

    private fun getNextTimestamp(): Long {
        val now = System.currentTimeMillis()
        while (true) {
            val last = lastTimestamp.get()
            val next = if (now > last) now else last + 1
            if (lastTimestamp.compareAndSet(last, next)) return next
        }
    }

    suspend fun getFreshLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: fusedLocationClient.lastLocation.await()
            location?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error fetching fresh location", e)
            null
        }
    }

    fun getAllItems(): Flow<List<InventoryItem>> = inventoryDao.getAllItems()

    fun getAllItemsWithResolvedLocations(): Flow<List<InventoryItem>> = combine(
        inventoryDao.getAllItems(),
        itemLinkDao.getAllLinksFlow()
    ) { items, links ->
        resolveLocations(items, links)
    }

    fun resolveLocations(items: List<InventoryItem>, links: List<ItemLink>): List<InventoryItem> {
        val itemMap = items.associateBy { it.id }
        val followerToLeader = links.associate { it.followerId to it.leaderId }

        return items.map { item ->
            // Check parent hierarchy for location if current item location is empty
            var resolvedLocation = item.location
            var resolvedLat = item.latitude
            var resolvedLon = item.longitude

            // 1. Handle Container Hierarchy
            if (resolvedLocation.isEmpty() && item.parentId != null) {
                val directParent = itemMap[item.parentId]
                if (directParent != null) {
                    resolvedLocation = "inside \"${directParent.name}\""
                    
                    // Always try to resolve lat/lon from the hierarchy if not set on the item
                    var currentParentId = item.parentId
                    val visitedParents = mutableSetOf<Long>()
                    while (currentParentId != null && currentParentId !in visitedParents) {
                        visitedParents.add(currentParentId)
                        val parent = itemMap[currentParentId] ?: break
                        if (resolvedLat == null) resolvedLat = parent.latitude
                        if (resolvedLon == null) resolvedLon = parent.longitude
                        if (resolvedLat != null && resolvedLon != null) break
                        currentParentId = parent.parentId
                    }
                }
            }

            // 2. Handle Inheritance from links (if still empty, for followers)
            if (resolvedLocation.isEmpty()) {
                var currentId = item.id
                val visitedLinks = mutableSetOf<Long>()

                while (followerToLeader.containsKey(currentId) && currentId !in visitedLinks) {
                    visitedLinks.add(currentId)
                    val leaderId = followerToLeader[currentId] ?: break
                    val leader = itemMap[leaderId] ?: break
                    
                    if (leader.location.isNotEmpty()) {
                        resolvedLocation = leader.location
                        resolvedLat = leader.latitude
                        resolvedLon = leader.longitude
                        break
                    }
                    currentId = leaderId
                }
            }

            if (resolvedLocation.isNotEmpty()) {
                item.copy(location = resolvedLocation, latitude = resolvedLat, longitude = resolvedLon)
            } else {
                item
            }
        }
    }

    suspend fun getAllItemsList(): List<InventoryItem> = inventoryDao.getAllItemsList()

    fun getUserLocation(): Flow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    suspend fun getItemById(id: Long): InventoryItem? = withContext(Dispatchers.IO) {
        inventoryDao.getItemById(id)
    }

    fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = inventoryDao.getItemByIdFlow(id)

    suspend fun insertItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        val currentTime = getNextTimestamp()
        val itemToInsert = item.copy(createdAt = currentTime, updatedAt = currentTime, isDirty = true)
        inventoryDao.insertItem(itemToInsert)
    }

    suspend fun updateItem(item: InventoryItem) = withContext(Dispatchers.IO) {
        val currentTime = getNextTimestamp()
        inventoryDao.updateItem(item.copy(updatedAt = currentTime, isDirty = true))
    }

    suspend fun updateItems(items: List<InventoryItem>) = withContext(Dispatchers.IO) {
        val currentTime = getNextTimestamp()
        inventoryDao.updateItems(items.map { it.copy(updatedAt = currentTime, isDirty = true) })
    }

    suspend fun deleteItemById(id: Long) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItemById(id, getNextTimestamp())
    }

    suspend fun updateQuantity(id: Long, newQuantity: Int) = withContext(Dispatchers.IO) {
        inventoryDao.updateQuantity(id, newQuantity, getNextTimestamp())
    }

    suspend fun setItemEquipped(id: Long, equipped: Boolean, repack: Boolean = false) = withContext(Dispatchers.IO) {
        setItemsEquipped(listOf(id), equipped, repack)
    }

    /**
     * Updates the equipped status for multiple items.
     * When unequipping, items not returned to a container are marked with the current street address.
     */
    suspend fun setItemsEquipped(ids: List<Long>, equipped: Boolean, repack: Boolean = false) = withContext(Dispatchers.IO) {
        val items = ids.mapNotNull { inventoryDao.getItemById(it) }
        if (items.isEmpty()) return@withContext
        
        val currentTime = getNextTimestamp()
        var currentGpsLocation: Pair<Double, Double>? = null
        var streetAddress: String? = null
        
        if (!equipped) {
            currentGpsLocation = getFreshLocation()
            currentGpsLocation?.let { (lat, lon) ->
                streetAddress = reverseGeocode(lat, lon)
            }
        }

        val updatedItems = items.map { item ->
            if (equipped) {
                item.copy(
                    equipped = true,
                    parentId = null,
                    // BUG 2 FIX: lastParentId should be exactly where it was before equipping. 
                    // If it was at root, it's null.
                    lastParentId = item.parentId,
                    updatedAt = currentTime,
                    isDirty = true
                )
            } else {
                val newParentId = if (repack) item.lastParentId else null
                var latitude = item.latitude
                var longitude = item.longitude
                var location = item.location
                
                if (newParentId == null) {
                    currentGpsLocation?.let { (lat, lon) ->
                        latitude = lat
                        longitude = lon
                        location = streetAddress ?: "Dropped at current location"
                    } ?: run {
                        location = "Dropped (location unknown)"
                    }
                } else {
                    // If repacking, clear explicit location so it resolves from container
                    location = ""
                }

                item.copy(
                    equipped = false,
                    parentId = newParentId,
                    latitude = latitude,
                    longitude = longitude,
                    location = location,
                    updatedAt = currentTime,
                    isDirty = true
                )
            }
        }
        
        inventoryDao.updateItems(updatedItems)
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (addresses.isNullOrEmpty()) {
                "Dropped at (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)})"
            } else {
                val addr = addresses[0]
                val streetNumber = addr.subThoroughfare ?: ""
                val streetName = addr.thoroughfare ?: ""
                val neighborhood = addr.subLocality ?: addr.locality ?: ""
                
                val streetLine = if (streetNumber.isNotEmpty() && streetName.isNotEmpty()) {
                    "$streetNumber $streetName"
                } else {
                    streetName.ifEmpty { streetNumber }
                }
                
                if (streetLine.isNotEmpty() && neighborhood.isNotEmpty()) {
                    "$streetLine, $neighborhood"
                } else if (streetLine.isNotEmpty()) {
                    streetLine
                } else {
                    neighborhood.ifEmpty { "Dropped at (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)})" }
                }
            }
        } catch (e: Exception) {
            "Dropped at (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)})"
        }
    }

    suspend fun updateItemEquippedStatus(id: Long, equipped: Boolean) = withContext(Dispatchers.IO) {
        setItemEquipped(id, equipped, false)
    }

    suspend fun moveItem(id: Long, newParentId: Long?) = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(id) ?: return@withContext
        val currentTime = getNextTimestamp()

        val links = itemLinkDao.getAllLinksList()
        val adjList = mutableMapOf<Long, MutableList<Long>>()
        links.forEach { link ->
            adjList.getOrPut(link.leaderId) { mutableListOf() }.add(link.followerId)
            adjList.getOrPut(link.followerId) { mutableListOf() }.add(link.leaderId)
        }

        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(id)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (visited.add(curr)) {
                adjList[curr]?.let { queue.addAll(it) }
            }
        }

        val itemsToUpdate = visited.mapNotNull { inventoryDao.getItemById(it) }
        if (itemsToUpdate.isNotEmpty()) {
            // BUG 1 FIX: When moving to root (newParentId == null), we must fetch fresh location
            var streetAddress: String? = null
            var currentGps: Pair<Double, Double>? = null
            
            if (newParentId == null) {
                currentGps = getFreshLocation()
                currentGps?.let { (lat, lon) ->
                    streetAddress = reverseGeocode(lat, lon)
                }
            }

            inventoryDao.updateItems(itemsToUpdate.map { 
                it.copy(
                    parentId = newParentId,
                    // BUG 2 FIX: When moving to root, we clear lastParentId because root is its new permanent home.
                    lastParentId = if (newParentId == null) null else it.lastParentId,
                    location = if (newParentId != null) "" else (streetAddress ?: "Dropped at current location"),
                    latitude = if (newParentId == null) (currentGps?.first ?: it.latitude) else it.latitude,
                    longitude = if (newParentId == null) (currentGps?.second ?: it.longitude) else it.longitude,
                    updatedAt = currentTime,
                    isDirty = true
                )
            })
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
    fun getAllLinksFlow(): Flow<List<ItemLink>> = itemLinkDao.getAllLinksFlow()
    fun getLinksForItemFlow(itemId: Long): Flow<List<ItemLink>> = itemLinkDao.getLinksForItemFlow(itemId)

    suspend fun addLink(followerId: Long, leaderId: Long) = withContext(Dispatchers.IO) {
        if (followerId == leaderId) return@withContext
        itemLinkDao.insertLink(ItemLink(followerId, leaderId, isDirty = true))
        
        val itemsToTouch = listOfNotNull(
            inventoryDao.getItemById(followerId),
            inventoryDao.getItemById(leaderId)
        )
        if (itemsToTouch.isNotEmpty()) {
            val currentTime = getNextTimestamp()
            inventoryDao.updateItems(itemsToTouch.map { it.copy(updatedAt = currentTime, isDirty = true) })
        }
    }

    suspend fun removeLink(followerId: Long, leaderId: Long) = withContext(Dispatchers.IO) {
        itemLinkDao.removeLink(followerId, leaderId)
        touchItem(followerId)
    }

    suspend fun touchItem(id: Long) {
        inventoryDao.getItemById(id)?.let {
            inventoryDao.updateItem(it.copy(updatedAt = getNextTimestamp(), isDirty = true))
        }
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLocation.value = latitude to longitude
    }
}
