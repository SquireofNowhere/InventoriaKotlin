package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionItem
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.Todo

@Database(
    entities = [
        InventoryItem::class,
        Task::class,
        InventoryCollection::class,
        InventoryCollectionItem::class,
        ItemLink::class,
        Todo::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao
    abstract fun itemLinkDao(): ItemLinkDao
    abstract fun todoDao(): TodoDao

    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
