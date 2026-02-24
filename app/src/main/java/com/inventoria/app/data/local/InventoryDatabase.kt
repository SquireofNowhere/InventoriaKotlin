package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryItem

/**
 * Room database for Inventoria.
 * Version incremented to 5 due to adding isEquipped field to InventoryItem.
 */
@Database(
    entities = [InventoryItem::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
