package com.inventoria.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import com.inventoria.app.ui.screens.settings.SettingsViewModel
import com.inventoria.app.ui.theme.InventoriaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val inventoryViewModel: InventoryListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("MainActivity", "App in foreground, triggering sync")
                inventoryViewModel.triggerManualSync()
            }
        }

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            
            InventoriaTheme(darkTheme = isDarkMode) {
                InventoriaApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "App leaving foreground, triggering final sync")
        inventoryViewModel.triggerManualSync()
    }
}
