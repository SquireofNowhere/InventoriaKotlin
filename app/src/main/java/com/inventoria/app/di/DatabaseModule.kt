package com.inventoria.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.InventoryDatabase
import com.inventoria.app.data.local.ItemLinkDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.local.TaskTypeDao
import com.inventoria.app.data.local.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE InventoryItem ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE Task ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE InventoryCollection ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE InventoryCollectionItem ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ItemLink ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
        }
    }
    
    /**
     * Task Types (v13). Purely additive, so it gets a real migration rather than falling through
     * to fallbackToDestructiveMigration() below -- that fallback wipes the local database, which
     * is recoverable for signed-in users (data re-pulls from Firebase) but silently destroys
     * everything for local-only ones.
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS TaskType (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL,
                    isDirty INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE Task ADD COLUMN taskTypeId TEXT")
        }
    }

    /** Optional time-of-day on todo deadlines (v14). Additive, same reasoning as v13 above. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Todo ADD COLUMN deadlineMinuteOfDay INTEGER")
        }
    }

    /** Task Type on Todos (v15). Additive, same reasoning as v13 above. */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Todo ADD COLUMN taskTypeId TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideInventoryDatabase(
        @ApplicationContext context: Context
    ): InventoryDatabase {
        return Room.databaseBuilder(
            context,
            InventoryDatabase::class.java,
            InventoryDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_3_4, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: InventoryDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideCollectionDao(database: InventoryDatabase): CollectionDao {
        return database.collectionDao()
    }

    @Provides
    @Singleton
    fun provideItemLinkDao(database: InventoryDatabase): ItemLinkDao {
        return database.itemLinkDao()
    }

    @Provides
    @Singleton
    fun provideTodoDao(database: InventoryDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    @Singleton
    fun provideTaskTypeDao(database: InventoryDatabase): TaskTypeDao {
        return database.taskTypeDao()
    }
}
