package com.inventoria.app.di

import android.content.Context
import androidx.room.Room
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.InventoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
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
            // Enabling destructive migration to "reset" the local database.
            // This resolves the "Migration from 2 to 5 not found" crash by 
            // rebuilding the database with the new schema (Long timestamps, isEquipped).
            // Data will be re-synced from Firebase if available.
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }
}
