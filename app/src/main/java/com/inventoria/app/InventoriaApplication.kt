package com.inventoria.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Inventoria
 * Enables Hilt dependency injection
 */
@HiltAndroidApp
class InventoriaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide services here
    }
}
