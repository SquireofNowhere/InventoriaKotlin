package com.inventoria.app.di

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.inventoria.app.BuildConfig
import com.inventoria.app.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class FirebaseModule {

    companion object {
        private const val TAG = "FirebaseModule"
        private const val DEFAULT_DB_URL = "https://inventoriaus-default-rtdb.firebaseio.com"
        // Try the newer .firebasestorage.app format if .appspot.com is 404ing
        private const val FALLBACK_STORAGE_BUCKET = "inventoriaus.firebasestorage.app"
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return try {
            if (BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()) {
                FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
            } else {
                // Try default instance from google-services.json
                FirebaseDatabase.getInstance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Default FirebaseDatabase failed, using fallback: $DEFAULT_DB_URL")
            FirebaseDatabase.getInstance(DEFAULT_DB_URL)
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return try {
            if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) {
                val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET
                val storageUrl = if (bucket.startsWith("gs://")) bucket else "gs://$bucket"
                FirebaseStorage.getInstance(storageUrl)
            } else {
                // IMPORTANT: Prefer default getInstance() which uses google-services.json
                // This is the most reliable way to get the correct bucket.
                FirebaseStorage.getInstance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Default FirebaseStorage failed, using fallback: $FALLBACK_STORAGE_BUCKET")
            FirebaseStorage.getInstance("gs://$FALLBACK_STORAGE_BUCKET")
        }
    }

    @Provides
    @Singleton
    fun provideGoogleSignInOptions(): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        
        if (BuildConfig.DEFAULT_WEB_CLIENT_ID.isNotBlank()) {
            builder.requestIdToken(BuildConfig.DEFAULT_WEB_CLIENT_ID)
        } else {
            Log.e(TAG, "CRITICAL: DEFAULT_WEB_CLIENT_ID is missing!")
        }
        
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGoogleSignInClient(
        @ApplicationContext context: Context,
        gso: GoogleSignInOptions
    ): GoogleSignInClient {
        return GoogleSignIn.getClient(context, gso)
    }
}
