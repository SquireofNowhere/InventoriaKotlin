package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.*

/**
 * Room database for Inventoria.
 * Version 13: Added InventoryCollection and InventoryCollectionItem entities.
 * Version 14: Added lastParentId to InventoryItem.
 * Version 15: Added updatedAt to InventoryCollectionItem for sync resolution.
 * Version 16: Attempted linkedItemId in InventoryItem (reverted in 17).
 * Version 17: Added ItemLink entity for complex directed linking logic.
 */
@Database(
    entities = [
        InventoryItem::class, 
        Task::class,
        InventoryCollection::class,
        InventoryCollectionItem::class,
        ItemLink::class
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao
    abstract fun itemLinkDao(): ItemLinkDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
