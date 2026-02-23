package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryItem

/**
 * Room database for Inventoria.
 * Version incremented to 2 due to schema change (removed minimumQuantity and isLowStock).
 */
@Database(
    entities = [InventoryItem::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
