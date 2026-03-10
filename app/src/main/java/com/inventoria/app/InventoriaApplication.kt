package com.inventoria.app

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class InventoriaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Setup global crash handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("InventoriaApp", "CRITICAL CRASH in thread ${thread.name}", throwable)
            System.exit(1)
        }

        // Initialize OSMDroid
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        
        try {
            // Initialize Firebase with persistence
            FirebaseDatabase.getInstance("https://inventoriaus-default-rtdb.firebaseio.com").setPersistenceEnabled(true)
            Log.d("InventoriaApp", "Firebase Database initialized with persistence.")
        } catch (e: Exception) {
            Log.e("InventoriaApp", "Firebase initialization failed", e)
        }
    }
}
