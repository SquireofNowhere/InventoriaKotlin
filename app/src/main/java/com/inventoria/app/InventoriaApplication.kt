package com.inventoria.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.google.firebase.database.FirebaseDatabase
import com.inventoria.app.data.alarm.TodoAlarmScheduler
import com.inventoria.app.data.worker.SyncWorker
import com.inventoria.app.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class InventoriaApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var todoAlarmScheduler: TodoAlarmScheduler

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

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

        // Watches the Todo table for the life of the process and keeps AlarmManager matching it.
        // Also the whole reboot story: BootReceiver only brings the process up, and this is what
        // then re-arms every alarm the restart threw away.
        todoAlarmScheduler.start()

        // Same idea for the home-screen widgets: watch the tables, redraw whatever is placed.
        widgetRefresher.start()
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
