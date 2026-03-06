package com.inventoria.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import com.inventoria.app.ui.screens.settings.SettingsViewModel
import com.inventoria.app.ui.theme.InventoriaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main activity that hosts the navigation graph
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val inventoryViewModel: InventoryListViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup lifecycle observer for background sync
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger sync when app comes to foreground
                Log.d("MainActivity", "App in foreground, triggering sync")
                inventoryViewModel.triggerManualSync()
            }
        }
        
        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            
            InventoriaTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InventoriaApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Trigger one last sync when leaving or terminating the app
        Log.d("MainActivity", "App leaving foreground, triggering final sync")
        inventoryViewModel.triggerManualSync()
    }
}
