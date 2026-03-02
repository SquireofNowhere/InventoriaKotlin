package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.*

/**
 * Room database for Inventoria.
 * Version 13: Added InventoryCollection and InventoryCollectionItem entities.
 */
@Database(
    entities = [
        InventoryItem::class, 
        Task::class,
        InventoryCollection::class,
        InventoryCollectionItem::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
