package com.inventoria.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.google.firebase.database.FirebaseDatabase
import com.inventoria.app.data.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class InventoriaApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Setup global crash handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("InventoriaApp", "CRITICAL CRASH in thread ${thread.name}", throwable)
            System.exit(1)
        }

        // Initialize OSMDroid
        OsmConfiguration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        
        try {
            // Initialize Firebase with persistence
            FirebaseDatabase.getInstance("https://inventoriaus-default-rtdb.firebaseio.com").setPersistenceEnabled(true)
            Log.d("InventoriaApp", "Firebase Database initialized with persistence.")
        } catch (e: Exception) {
            Log.e("InventoriaApp", "Firebase initialization failed", e)
        }

        scheduleSync()
    }

    private fun scheduleSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "inventoria_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
