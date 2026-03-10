package com.inventoria.app.data.repository

import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    private val inventoryDao: InventoryDao
) {
    fun getAllCollections(): Flow<List<InventoryCollection>> = collectionDao.getAllCollections()
    
    suspend fun getAllCollectionsList(): List<InventoryCollection> = collectionDao.getAllCollectionsList()

    fun getCollectionsWithCounts(): Flow<List<InventoryCollectionWithCount>> = collectionDao.getCollectionsWithCounts()
    
    fun getCollectionWithItems(id: Long): Flow<InventoryCollectionWithItems?> = collectionDao.getCollectionWithItems(id)
    
    fun getItemsForCollection(collectionId: Long): Flow<List<InventoryCollectionItem>> = collectionDao.getAllCollectionItemsFlow().map { items ->
        items.filter { it.collectionId == collectionId }
    }

    fun getCollectionsForItem(itemId: Long): Flow<List<InventoryCollection>> = collectionDao.getCollectionsForItem(itemId)

    fun getCollectionReadiness(collectionId: Long): Flow<InventoryCollectionReadiness?> = collectionDao.getCollectionReadiness(collectionId)

    suspend fun createCollection(collection: InventoryCollection): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(collection)
    }

    suspend fun updateCollection(collection: InventoryCollection) = withContext(Dispatchers.IO) {
        collectionDao.updateCollection(collection.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCollection(collectionId: Long) = withContext(Dispatchers.IO) {
        collectionDao.getCollectionById(collectionId)?.let {
            collectionDao.deleteCollection(it)
        }
    }

    suspend fun addItemToCollection(collectionId: Long, itemId: Long, requiredQuantity: Int = 1, notes: String? = null) = withContext(Dispatchers.IO) {
        collectionDao.insertCollectionItem(InventoryCollectionItem(collectionId, itemId, requiredQuantity, notes))
    }

    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long) = withContext(Dispatchers.IO) {
        collectionDao.removeItemFromCollection(collectionId, itemId)
    }

    suspend fun packCollectionIntoContainer(collectionId: Long, containerId: Long): PackResult = withContext(Dispatchers.IO) {
        try {
            val itemsInColl = getItemsForCollection(collectionId).first()
            val itemsToUpdate = mutableListOf<InventoryItem>()
            val errors = mutableListOf<String>()

            itemsInColl.forEach { ci ->
                val item = inventoryDao.getItemById(ci.itemId)
                if (item != null) {
                    itemsToUpdate.add(item.copy(parentId = containerId, equipped = false, updatedAt = System.currentTimeMillis()))
                } else {
                    errors.add("Item ${ci.itemId} not found")
                }
            }
            
            inventoryDao.updateItems(itemsToUpdate)
            if (errors.isEmpty()) PackResult.Success("All items packed into container")
            else PackResult.Partial("Packed with some errors", errors)
        } catch (e: Exception) {
            PackResult.Error(e.message ?: "Unknown error during packing")
        }
    }

    suspend fun unpackCollection(collectionId: Long): PackResult = withContext(Dispatchers.IO) {
        try {
            val itemsInColl = getItemsForCollection(collectionId).first()
            val itemsToUpdate = mutableListOf<InventoryItem>()
            
            itemsInColl.forEach { ci ->
                val item = inventoryDao.getItemById(ci.itemId)
                if (item != null) {
                    itemsToUpdate.add(item.copy(parentId = null, updatedAt = System.currentTimeMillis()))
                }
            }
            inventoryDao.updateItems(itemsToUpdate)
            PackResult.Success("Collection items unpacked")
        } catch (e: Exception) {
            PackResult.Error(e.message ?: "Unknown error during unpacking")
        }
    }

    suspend fun equipCollection(collectionId: Long): PackResult = withContext(Dispatchers.IO) {
        try {
            val itemsInColl = getItemsForCollection(collectionId).first()
            val itemsToUpdate = mutableListOf<InventoryItem>()
            
            itemsInColl.forEach { ci ->
                val item = inventoryDao.getItemById(ci.itemId)
                if (item != null) {
                    itemsToUpdate.add(item.copy(equipped = true, parentId = null, updatedAt = System.currentTimeMillis()))
                }
            }
            inventoryDao.updateItems(itemsToUpdate)
            PackResult.Success("Collection equipped")
        } catch (e: Exception) {
            PackResult.Error(e.message ?: "Unknown error during equipping")
        }
    }

    suspend fun unequipCollection(collectionId: Long, repack: Boolean = false): PackResult = withContext(Dispatchers.IO) {
        try {
            val itemsInColl = getItemsForCollection(collectionId).first()
            val itemsToUpdate = mutableListOf<InventoryItem>()
            
            itemsInColl.forEach { ci ->
                val item = inventoryDao.getItemById(ci.itemId)
                if (item != null && item.equipped) {
                    val newParentId = if (repack) item.lastParentId else null
                    itemsToUpdate.add(item.copy(equipped = false, parentId = newParentId, updatedAt = System.currentTimeMillis()))
                }
            }
            inventoryDao.updateItems(itemsToUpdate)
            PackResult.Success("Collection unequipped")
        } catch (e: Exception) {
            PackResult.Error(e.message ?: "Unknown error during unequipping")
        }
    }
}
