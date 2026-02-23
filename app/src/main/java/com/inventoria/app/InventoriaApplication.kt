package com.inventoria.app

import android.app.Application
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
        
        // OSMDroid configuration
        // The user agent should be a unique identifier for your app
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    }
}
