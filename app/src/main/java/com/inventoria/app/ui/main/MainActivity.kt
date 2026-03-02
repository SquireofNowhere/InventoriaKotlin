package com.inventoria.app.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.inventoria.app.ui.screens.settings.SettingsViewModel
import com.inventoria.app.ui.theme.InventoriaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity that hosts the navigation graph
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val settingsViewModel: SettingsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
}
