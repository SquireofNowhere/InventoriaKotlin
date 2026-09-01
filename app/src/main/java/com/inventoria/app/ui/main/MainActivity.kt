package com.inventoria.app.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import com.inventoria.app.ui.screens.settings.SettingsViewModel
import com.inventoria.app.ui.theme.InventoriaTheme
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val inventoryViewModel: InventoryListViewModel by viewModels()

    /** A screen a home-screen widget asked for (WidgetNav.EXTRA_NAV_ROUTE), until InventoriaApp
     * has navigated there. Only read off a *fresh* launch intent: on a rotation the saved
     * NavController state already holds wherever the user went, and replaying the route would
     * yank them back. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingRoute.value = intent.getStringExtra(WidgetNav.EXTRA_NAV_ROUTE)
        }

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            val route by pendingRoute.collectAsState()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermissionState = rememberPermissionState(
                    Manifest.permission.POST_NOTIFICATIONS
                )
                LaunchedEffect(Unit) {
                    if (!notificationPermissionState.status.isGranted) {
                        notificationPermissionState.launchPermissionRequest()
                    }
                }
            }

            InventoriaTheme(darkTheme = isDarkMode) {
                InventoriaApp(
                    pendingRoute = route,
                    onRouteConsumed = { pendingRoute.value = null }
                )
            }
        }

        lifecycleScope.launch {
            Log.d("MainActivity", "Performing initial app-open sync")
            try {
                inventoryViewModel.syncOnAppOpen()
            } catch (e: Exception) {
                Log.e("MainActivity", "Initial app-open sync failed", e)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("MainActivity", "App in foreground, triggering background sync")
                inventoryViewModel.triggerManualSync()
            }
        }
    }

    /** SplashActivity relaunches this activity with CLEAR_TOP|SINGLE_TOP, so a widget tap while
     * the app is already running lands here rather than in a second onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(WidgetNav.EXTRA_NAV_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "App leaving foreground, triggering final sync")
        inventoryViewModel.triggerManualSync()
    }
}
