package com.inventoria.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        // Return the regional instance. 
        // Initial setup (like persistence) is handled in InventoriaApplication.kt
        val url = "https://inventoria-18b97-default-rtdb.europe-west1.firebasedatabase.app/"
        return FirebaseDatabase.getInstance(url)
    }
}
