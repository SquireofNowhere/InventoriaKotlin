package com.inventoria.app.di

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.local.*
import com.inventoria.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    @Singleton
    fun provideFirebaseAuthRepository(
        auth: FirebaseAuth,
        googleSignInClient: GoogleSignInClient,
        settingsRepository: SettingsRepository,
        firebaseDatabase: FirebaseDatabase,
        firebaseStorage: FirebaseStorage
    ): FirebaseAuthRepository {
        return FirebaseAuthRepository(auth, googleSignInClient, settingsRepository, firebaseDatabase, firebaseStorage)
    }

    @Provides
    @Singleton
    fun provideFirebaseSyncRepository(
        inventoryDao: InventoryDao,
        taskDao: TaskDao,
        collectionDao: CollectionDao,
        itemLinkDao: ItemLinkDao,
        firebaseDatabase: FirebaseDatabase,
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
        storageRepository: FirebaseStorageRepository,
        @ApplicationContext context: Context
    ): InventoryRepository {
        return InventoryRepository(inventoryDao, itemLinkDao, syncRepository, authRepository, storageRepository, context)
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
        storage: FirebaseStorage,
        authRepository: FirebaseAuthRepository
    ): FirebaseStorageRepository {
        return FirebaseStorageRepository(storage, authRepository)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideCalendarRepository(
        @ApplicationContext context: Context
    ): CalendarRepository {
        return CalendarRepository(context)
    }
}
