package com.inventoria.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionItem
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.model.ScheduleBlock
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
        TaskType::class,
        ScheduleBlock::class
    ],
    version = 17,
    // Exported to app/schemas/ and committed. Room writes one JSON file per version, which is what
    // makes a migration reviewable in a diff and testable at all -- without it there is nothing to
    // compare a migration against, and a wrong ALTER TABLE only shows up as a crash on a real
    // device. Bumping the version below without adding a matching Migration in DatabaseModule now
    // fails at startup rather than silently wiping the database; see the comment there.
    //
    // The exported history starts at 15, because this flag was turned on at that version -- 12, 13
    // and 14 have migrations but no schema to check them against. 15 -> 16 (schedule blocks and
    // todo alarms) is the first bump that can actually be diffed; 16 -> 17 adds Todo.description
    // and ScheduleBlock.taskTypeId.
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
    abstract fun scheduleBlockDao(): ScheduleBlockDao

    companion object {
        const val DATABASE_NAME = "inventoria_database"
    }
}
