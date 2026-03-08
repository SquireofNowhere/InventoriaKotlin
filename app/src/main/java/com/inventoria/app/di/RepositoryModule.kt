package com.inventoria.app.di

import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.local.*
import com.inventoria.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    @Singleton
    fun provideFirebaseAuthRepository(
        auth: com.google.firebase.auth.FirebaseAuth
    ): FirebaseAuthRepository {
        return FirebaseAuthRepository(auth)
    }

    @Provides
    @Singleton
    fun provideFirebaseSyncRepository(
        inventoryDao: InventoryDao,
        taskDao: TaskDao,
        collectionDao: CollectionDao,
        itemLinkDao: ItemLinkDao,
        firebaseDatabase: com.google.firebase.database.FirebaseDatabase,
        authRepository: FirebaseAuthRepository,
        settingsRepository: SettingsRepository
    ): FirebaseSyncRepository {
        return FirebaseSyncRepository(
            inventoryDao, taskDao, collectionDao, itemLinkDao, 
            firebaseDatabase, authRepository, settingsRepository
        )
    }

    @Provides
    @Singleton
    fun provideInventoryRepository(
        inventoryDao: InventoryDao,
        itemLinkDao: ItemLinkDao,
        syncRepository: FirebaseSyncRepository,
        authRepository: FirebaseAuthRepository,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): InventoryRepository {
        return InventoryRepository(inventoryDao, itemLinkDao, syncRepository, authRepository, context)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao): TaskRepository {
        return TaskRepository(taskDao)
    }

    @Provides
    @Singleton
    fun provideCollectionRepository(
        collectionDao: CollectionDao,
        inventoryDao: InventoryDao
    ): CollectionRepository {
        return CollectionRepository(collectionDao, inventoryDao)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorageRepository(
        storage: com.google.firebase.storage.FirebaseStorage,
        authRepository: FirebaseAuthRepository
    ): FirebaseStorageRepository {
        return FirebaseStorageRepository(storage, authRepository)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideCalendarRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): CalendarRepository {
        return CalendarRepository(context)
    }
}
