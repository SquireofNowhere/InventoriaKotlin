package com.inventoria.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.util.Log
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory data operations using Room Database.
 * Provides a clean API for the rest of the app to interact with inventory data.
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val itemLinkDao: ItemLinkDao,
    private val syncRepository: FirebaseSyncRepository,
    private val authRepository: FirebaseAuthRepository,
    @ApplicationContext private val context: Context
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Current user location to be used for equipped items
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)

    // Ensure monotonic timestamps for rapid operations
    private val lastTimestamp = AtomicLong(0L)

    private fun getNextTimestamp(): Long {
        val now = System.currentTimeMillis()
        var last = lastTimestamp.get()
        while (true) {
            val next = if (now > last) now else last + 1
            if (lastTimestamp.compareAndSet(last, next)) {
                return next
            }
            last = lastTimestamp.get()
        }
    }

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
            val currentLoc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (currentLoc != null) return@withContext currentLoc.latitude to currentLoc.longitude
            
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
            inventoryDao.updateItem(item.copy(updatedAt = getNextTimestamp()))
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

    fun getAllItemsWithResolvedLocations(): Flow<List<InventoryItem>> {
        return combine(getAllItems(), getAllLinksFlow(), _userLocation) { items, links, userLoc ->
            val itemMap = items.associateBy { it.id }
            
            // Build connected groups of items from links
            val groups = mutableListOf<MutableSet<Long>>()
            val itemToGroupIdx = mutableMapOf<Long, Int>()
            
            links.forEach { link ->
                val a = link.followerId
                val b = link.leaderId
                val idxA = itemToGroupIdx[a]
                val idxB = itemToGroupIdx[b]
                
                when {
                    idxA != null && idxB != null -> {
                        if (idxA != idxB) {
                            groups[idxA].addAll(groups[idxB])
                            val bItems = groups[idxB].toList()
                            bItems.forEach { itemToGroupIdx[it] = idxA }
                            groups[idxB].clear()
                        }
                    }
                    idxA != null -> {
                        groups[idxA].add(b)
                        itemToGroupIdx[b] = idxA
                    }
                    idxB != null -> {
                        groups[idxB].add(a)
                        itemToGroupIdx[a] = idxB
                    }
                    else -> {
                        val newIdx = groups.size
                        groups.add(mutableSetOf(a, b))
                        itemToGroupIdx[a] = newIdx
                        itemToGroupIdx[b] = newIdx
                    }
                }
            }

            val resolvedCache = mutableMapOf<Long, Triple<Double?, Double?, String>>()

            fun getResolvedLocation(item: InventoryItem, visited: Set<Long> = emptySet()): Triple<Double?, Double?, String> {
                resolvedCache[item.id]?.let { return it }

                if (visited.contains(item.id)) {
                    Log.w("InventoryRepository", "Circular dependency detected for item ${item.id}")
                    return Triple(item.latitude, item.longitude, item.location)
                }
                val currentVisited = visited + item.id

                // 1. Equipped always wins for its group
                if (item.equipped && userLoc != null) {
                    val res = Triple(userLoc.first, userLoc.second, "With You")
                    resolvedCache[item.id] = res
                    return res
                }

                // 2. Physical Parent resolution
                if (item.parentId != null && item.parentId != item.id) {
                    val parent = itemMap[item.parentId]
                    if (parent != null) {
                        val res = getResolvedLocation(parent, currentVisited)
                        resolvedCache[item.id] = res
                        return res
                    }
                }

                // 3. Group Resolution (Mutual Following)
                val gIdx = itemToGroupIdx[item.id]
                if (gIdx != null && groups[gIdx].isNotEmpty()) {
                    val groupIds = groups[gIdx]
                    // An anchor is an item in the group that has an external location:
                    // - Either it's equipped
                    // - Or it has a physical parent that is NOT part of this linked group
                    val anchors = groupIds.mapNotNull { itemMap[it] }
                        .filter { (it.equipped || (it.parentId != null && !groupIds.contains(it.parentId))) && !visited.contains(it.id) }
                    
                    if (anchors.isNotEmpty()) {
                        val bestAnchor = anchors.maxByOrNull { it.updatedAt }!!
                        if (bestAnchor.id != item.id) {
                            val res = getResolvedLocation(bestAnchor, currentVisited)
                            resolvedCache[item.id] = res
                            return res
                        }
                    }
                }

                val res = Triple(item.latitude, item.longitude, item.location)
                resolvedCache[item.id] = res
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
    
    suspend fun insertItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        // Protection: cannot be own parent
        val finalItem = if (item.parentId == item.id) item.copy(parentId = null) else item
        
        val sanitizedItem = if (finalItem.equipped) {
            finalItem.copy(parentId = null)
        } else finalItem
        
        val currentTime = getNextTimestamp()
        inventoryDao.insertItem(sanitizedItem.copy(createdAt = currentTime, updatedAt = currentTime))
    }

    /**
     * Group movement logic: Find all items connected by links (mutual followers).
     */
    suspend fun getLinkedGroupIds(itemId: Long): Set<Long> = withContext(Dispatchers.IO) {
        val group = mutableSetOf<Long>()
        val queue = mutableListOf(itemId)
        val visited = mutableSetOf<Long>()

        while (queue.isNotEmpty()) {
            val currentId = queue.removeAt(0)
            if (visited.add(currentId)) {
                group.add(currentId)
                val leaders = itemLinkDao.getLeadersForItem(currentId).map { it.leaderId }
                val followers = itemLinkDao.getFollowersForItem(currentId).map { it.followerId }
                (leaders + followers).forEach { id ->
                    if (!visited.contains(id)) queue.add(id)
                }
            }
        }
        group
    }

    /**
     * Link an item to another item. Bidirectional following logic.
     */
    suspend fun addLink(followerId: Long, leaderId: Long) = withContext(Dispatchers.IO) {
        if (followerId == leaderId) return@withContext
        itemLinkDao.insertLink(ItemLink(followerId, leaderId))
        // Link change is a real data change, touch both items to trigger sync
        touchItem(followerId)
        touchItem(leaderId)
    }

    /**
     * Unlink an item from another.
     */
    suspend fun removeLink(followerId: Long, leaderId: Long) = withContext(Dispatchers.IO) {
        itemLinkDao.removeLink(followerId, leaderId)
        // Link change is a real data change
        touchItem(followerId)
    }

    /**
     * Helper to check if childId is physically nested inside potentialParentId (recursively)
     */
    private fun isPhysicalDescendant(childId: Long, potentialParentId: Long, itemMap: Map<Long, InventoryItem>): Boolean {
        if (childId == potentialParentId) return true
        var currentId: Long? = childId
        val visited = mutableSetOf<Long>()
        while (currentId != null && visited.add(currentId)) {
            val pid = itemMap[currentId]?.parentId
            if (pid == null) break
            if (pid == potentialParentId) return true
            currentId = pid
        }
        return false
    }

    private fun hasMeaningfulChanges(old: InventoryItem, new: InventoryItem): Boolean {
        return old.name != new.name ||
                old.quantity != new.quantity ||
                old.location != new.location ||
                old.latitude != new.latitude ||
                old.longitude != new.longitude ||
                old.price != new.price ||
                old.storage != new.storage ||
                old.parentId != new.parentId ||
                old.lastParentId != new.lastParentId ||
                old.equipped != new.equipped ||
                old.category != new.category ||
                old.tags != new.tags ||
                old.description != new.description ||
                old.imageUrl != new.imageUrl ||
                old.barcode != new.barcode ||
                old.sku != new.sku ||
                old.customFields != new.customFields
    }

    /**
     * Batch updates multiple items, optimized for performance and location fetching.
     * Modified to handle mutual followers automatically.
     */
    suspend fun updateItems(items: List<InventoryItem>, applyToFollowers: Boolean = true) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        
        val allCurrentItems = inventoryDao.getAllItems().first().associateBy { it.id }
        val currentTime = getNextTimestamp()

        // 1. Expand the list to include linked group members if necessary
        val itemsToProcess = if (applyToFollowers) {
            val expandedList = items.toMutableList()
            val processedIds = items.map { it.id }.toMutableSet()
            
            items.forEach { sourceItem ->
                val groupIds = getLinkedGroupIds(sourceItem.id)
                groupIds.forEach { gid ->
                    if (!processedIds.contains(gid)) {
                        allCurrentItems[gid]?.let { gItem ->
                            // Linked items inherit the movement state of the source
                            // BUT: If the linked item is physically inside the source item (or vice versa), 
                            // we must NOT change its parentId, or we'll eject it from its container!
                            val isSourceInsideG = isPhysicalDescendant(sourceItem.id, gid, allCurrentItems)
                            val isGInsideSource = isPhysicalDescendant(gid, sourceItem.id, allCurrentItems)
                            
                            if (!isSourceInsideG && !isGInsideSource && gid != sourceItem.parentId) {
                                expandedList.add(gItem.copy(
                                    parentId = sourceItem.parentId,
                                    equipped = sourceItem.equipped,
                                    location = sourceItem.location,
                                    latitude = sourceItem.latitude,
                                    longitude = sourceItem.longitude
                                ))
                                processedIds.add(gid)
                            }
                        }
                    }
                }
            }
            expandedList
        } else items

        // 2. Refresh location fix if any item is being unequipped or moved out of a container
        var needsFreshLocation = false
        for (item in itemsToProcess) {
            val current = allCurrentItems[item.id] ?: continue
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

        val updatedItems = itemsToProcess.mapNotNull { item ->
            val current = allCurrentItems[item.id] ?: return@mapNotNull null
            
            // Protection: cannot be own parent
            var updated = if (item.parentId == item.id) item.copy(parentId = null) else item

            // Inherit location logic (modifies the 'updated' object fields)
            if (updated.parentId != null) {
                val parentInBatch = itemsToProcess.find { it.id == updated.parentId }
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
                    val parent = allCurrentItems[updated.parentId!!]
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

            // Equipping Logic (Repack support)
            if (updated.equipped && !current.equipped) {
                if (current.parentId != null) {
                    val parentId = current.parentId!!
                    val isParentInBatchAndEquipped = itemsToProcess.any { it.id == parentId && it.equipped }
                    if (!isParentInBatchAndEquipped) {
                        updated = updated.copy(parentId = null, lastParentId = parentId)
                    }
                }
            }

            // ONLY apply update if there's a real difference compared to the current database state
            if (hasMeaningfulChanges(current, updated)) {
                updated.copy(updatedAt = currentTime)
            } else {
                null
            }
        }

        if (updatedItems.isNotEmpty()) {
            inventoryDao.updateItems(updatedItems)
        }
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
