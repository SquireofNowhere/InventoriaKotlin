package com.inventoria.app.data.repository

import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    private val inventoryDao: InventoryDao,
    private val inventoryRepository: InventoryRepository
) {
    fun getAllCollections(): Flow<List<InventoryCollection>> = collectionDao.getAllCollections()
    
    fun getCollectionsWithCounts(): Flow<List<InventoryCollectionWithCount>> = collectionDao.getCollectionsWithCounts()
    
    fun getCollectionWithItems(id: Long): Flow<InventoryCollectionWithItems?> = collectionDao.getCollectionWithItems(id)
    
    fun getCollectionReadiness(id: Long): Flow<InventoryCollectionReadiness> = collectionDao.getCollectionReadiness(id)
    
    fun searchCollections(query: String): Flow<List<InventoryCollection>> = collectionDao.searchCollections(query)
    
    fun getCollectionsByType(type: InventoryCollectionType): Flow<List<InventoryCollection>> = collectionDao.getCollectionsByType(type)

    fun getCollectionsForItem(itemId: Long): Flow<List<InventoryCollection>> = collectionDao.getCollectionsForItem(itemId)

    suspend fun createCollection(collection: InventoryCollection): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(collection)
    }

    suspend fun updateCollection(collection: InventoryCollection) = withContext(Dispatchers.IO) {
        collectionDao.updateCollection(collection.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCollection(collectionId: Long) = withContext(Dispatchers.IO) {
        val collection = collectionDao.getCollectionById(collectionId)
        if (collection != null) {
            collectionDao.deleteCollection(collection)
        }
    }

    suspend fun addItemToCollection(collectionId: Long, itemId: Long, requiredQuantity: Int = 1, notes: String? = null) = withContext(Dispatchers.IO) {
        collectionDao.insertCollectionItem(InventoryCollectionItem(collectionId, itemId, requiredQuantity, notes))
    }

    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long) = withContext(Dispatchers.IO) {
        collectionDao.removeItemFromCollection(collectionId, itemId)
    }

    suspend fun packCollectionIntoContainer(collectionId: Long, containerId: Long): PackResult = withContext(Dispatchers.IO) {
        val collectionItems = collectionDao.getItemsForCollection(collectionId)
        val container = inventoryDao.getItemById(containerId)
        
        if (container == null || !container.storage) {
            return@withContext PackResult.Error("Target is not a valid container")
        }

        val errors = mutableListOf<String>()
        val itemsToUpdate = mutableListOf<InventoryItem>()

        for (ci in collectionItems) {
            // Prevent packing the container into itself
            if (ci.itemId == containerId) continue

            val item = inventoryDao.getItemById(ci.itemId)
            if (item == null) {
                errors.add("Item not found: ID ${ci.itemId}")
                continue
            }
            if (item.quantity < ci.requiredQuantity) {
                errors.add("Insufficient quantity for ${item.name}: has ${item.quantity}, needs ${ci.requiredQuantity}")
            }
            itemsToUpdate.add(item.copy(parentId = containerId, equipped = false, lastParentId = null))
        }

        if (errors.isNotEmpty()) {
            return@withContext PackResult.ValidationFailed(errors)
        }

        inventoryRepository.updateItems(itemsToUpdate)
        PackResult.Success("Successfully packed into ${container.name}", itemsToUpdate.map { it.id })
    }

    suspend fun unpackCollection(collectionId: Long): PackResult = withContext(Dispatchers.IO) {
        val collectionItems = collectionDao.getItemsForCollection(collectionId)
        val itemsToUpdate = mutableListOf<InventoryItem>()
        
        for (ci in collectionItems) {
            val item = inventoryDao.getItemById(ci.itemId)
            if (item != null && item.parentId != null) {
                itemsToUpdate.add(item.copy(parentId = null, lastParentId = null))
            }
        }
        
        inventoryRepository.updateItems(itemsToUpdate)
        PackResult.Success("Collection unpacked")
    }

    suspend fun equipCollection(collectionId: Long): PackResult = withContext(Dispatchers.IO) {
        val collectionItems = collectionDao.getItemsForCollection(collectionId)
        val itemsToUpdate = mutableListOf<InventoryItem>()
        val errors = mutableListOf<String>()
        
        for (ci in collectionItems) {
            val item = inventoryDao.getItemById(ci.itemId)
            if (item == null) {
                errors.add("Item not found: ID ${ci.itemId}")
                continue
            }
            itemsToUpdate.add(item.copy(equipped = true))
        }

        inventoryRepository.updateItems(itemsToUpdate)

        if (errors.isNotEmpty()) {
            return@withContext PackResult.Error("Some items could not be equipped: ${errors.joinToString()}")
        }
        PackResult.Success("Collection equipped")
    }

    suspend fun unequipCollection(collectionId: Long, repack: Boolean = false): PackResult = withContext(Dispatchers.IO) {
        val collectionItems = collectionDao.getItemsForCollection(collectionId)
        val itemsToUpdate = mutableListOf<InventoryItem>()
        
        for (ci in collectionItems) {
            val item = inventoryDao.getItemById(ci.itemId)
            if (item != null && item.equipped) {
                if (repack && item.lastParentId != null) {
                    itemsToUpdate.add(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null))
                } else {
                    itemsToUpdate.add(item.copy(equipped = false, lastParentId = null))
                }
            }
        }
        
        inventoryRepository.updateItems(itemsToUpdate)
        PackResult.Success(if (repack) "Collection unequipped and repacked" else "Collection unequipped")
    }
}
