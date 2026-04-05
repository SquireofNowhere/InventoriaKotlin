package com.inventoria.app.data.repository

import android.content.Context
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

    private fun resolveLocations(items: List<InventoryItem>, links: List<ItemLink>): List<InventoryItem> {
        val itemMap = items.associateBy { it.id }
        val followerToLeader = links.associate { it.followerId to it.leaderId }

        return items.map { item ->
            if (item.location.isNotEmpty()) return@map item
            
            var currentId = item.id
            val visited = mutableSetOf<Long>()
            var resolvedLocation = ""
            var resolvedLat: Double? = null
            var resolvedLon: Double? = null

            while (followerToLeader.containsKey(currentId) && currentId !in visited) {
                visited.add(currentId)
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
     * When unequipping, items not returned to a container are marked with the current GPS location.
     */
    suspend fun setItemsEquipped(ids: List<Long>, equipped: Boolean, repack: Boolean = false) = withContext(Dispatchers.IO) {
        val items = ids.mapNotNull { inventoryDao.getItemById(it) }
        if (items.isEmpty()) return@withContext
        
        val currentTime = getNextTimestamp()
        var currentGpsLocation: Pair<Double, Double>? = null
        
        if (!equipped) {
            // Only fetch GPS if we are unequipping items that might be left at root
            currentGpsLocation = getFreshLocation()
        }

        val updatedItems = items.map { item ->
            if (equipped) {
                item.copy(
                    equipped = true,
                    parentId = null,
                    lastParentId = item.parentId,
                    updatedAt = currentTime,
                    isDirty = true
                )
            } else {
                val newParentId = if (repack) item.lastParentId else item.parentId
                var latitude = item.latitude
                var longitude = item.longitude
                
                if (newParentId == null) {
                    currentGpsLocation?.let { (lat, lon) ->
                        latitude = lat
                        longitude = lon
                    }
                }

                item.copy(
                    equipped = false,
                    parentId = newParentId,
                    latitude = latitude,
                    longitude = longitude,
                    updatedAt = currentTime,
                    isDirty = true
                )
            }
        }
        
        inventoryDao.updateItems(updatedItems)
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
            inventoryDao.updateItems(itemsToUpdate.map { 
                it.copy(
                    parentId = newParentId,
                    lastParentId = if (newParentId == null) it.parentId else it.lastParentId,
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
