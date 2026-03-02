package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryItem

/**
 * Room database for Inventoria.
 * Version 6: Updated schema with cloud-safe field names (storage, equipped).
 */
@Database(
    entities = [InventoryItem::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
