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
            // Removed fallbackToDestructiveMigration to prevent data loss on schema changes.
            // Future schema changes will now require explicit Migrations.
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }
}
