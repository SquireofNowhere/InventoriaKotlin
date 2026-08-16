package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionItem
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.Todo

@Database(
    entities = [
        InventoryItem::class,
        Task::class,
        InventoryCollection::class,
        InventoryCollectionItem::class,
        ItemLink::class,
        Todo::class,
        TaskType::class
    ],
    version = 15,
    // Exported to app/schemas/ and committed. Room writes one JSON file per version, which is what
    // makes a migration reviewable in a diff and testable at all -- without it there is nothing to
    // compare a migration against, and a wrong ALTER TABLE only shows up as a crash on a real
    // device. Bumping the version below without adding a matching Migration in DatabaseModule now
    // fails at startup rather than silently wiping the database; see the comment there.
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao
    abstract fun itemLinkDao(): ItemLinkDao
    abstract fun todoDao(): TodoDao
    abstract fun taskTypeDao(): TaskTypeDao

    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
