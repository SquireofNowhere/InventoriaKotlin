package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.Task

/**
 * Room database for Inventoria.
 * Version 7: Added Task entity for synchronized task tracking.
 */
@Database(
    entities = [InventoryItem::class, Task::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    
    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
