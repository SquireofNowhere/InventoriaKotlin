package com.inventoria.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val url = "https://inventoriaus-default-rtdb.firebaseio.com"
        return FirebaseDatabase.getInstance(url)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        val bucketUrl = "gs://inventoriaus.firebasestorage.app"
        return FirebaseStorage.getInstance(bucketUrl)
    }
}
