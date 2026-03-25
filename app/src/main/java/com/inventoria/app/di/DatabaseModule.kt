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
            .addMigrations(MIGRATION_3_4)
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
}
