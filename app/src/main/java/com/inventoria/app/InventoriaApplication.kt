package com.inventoria.app

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

/**
 * Application class for Inventoria
 * Enables Hilt dependency injection
 */
@HiltAndroidApp
class InventoriaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 1. Setup global crash logging to catch silent startup crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("InventoriaApp", "CRITICAL CRASH in thread ${thread.name}", throwable)
            // Still allow the app to crash after logging
            System.exit(1)
        }

        // 2. OSMDroid configuration
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        // 3. Unified Firebase Initialization
        try {
            // Updated to the new project URL from google-services.json
            val url = "https://inventoriaus-default-rtdb.firebaseio.com"
            // Set persistence on the specific instance we use everywhere
            FirebaseDatabase.getInstance(url).setPersistenceEnabled(true)
            Log.d("InventoriaApp", "Firebase Database initialized with persistence for new project.")
        } catch (e: Exception) {
            Log.e("InventoriaApp", "Firebase initialization failed", e)
        }
    }
}
