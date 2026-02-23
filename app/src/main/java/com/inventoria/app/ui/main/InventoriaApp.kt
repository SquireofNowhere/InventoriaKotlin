package com.inventoria.app.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.inventory.*
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.SettingsScreen
import com.inventoria.app.ui.screens.task.TaskTrackerScreen
import org.osmdroid.util.GeoPoint

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object Map : Screen("map", "Map", Icons.Default.Map)
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Timer)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Inventory,
        Screen.Map,
        Screen.Tasks,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavItems.map { it.route }) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id") }
                )
            }
            
            composable(Screen.Inventory.route) {
                InventoryListScreen(
                    onAddItem = { navController.navigate("add_item") },
                    onItemClick = { id -> navController.navigate("item_detail/$id") }
                )
            }

            composable(Screen.Map.route) {
                InventoryMapScreen(
                    onItemClick = { id -> navController.navigate("item_detail/$id") }
                )
            }

            composable(Screen.Tasks.route) {
                TaskTrackerScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable("add_item") {
                AddEditItemScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { navController.navigate("location_picker") }
                )
            }

            composable("item_detail/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull() ?: return@composable
                ItemDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEditItem = { id -> navController.navigate("edit_item/$id") }
                )
            }

            composable("edit_item/{itemId}") {
                AddEditItemScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { navController.navigate("location_picker") }
                )
            }

            composable("location_picker") {
                LocationPickerScreen(
                    onLocationSelected = { geoPoint, address ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("selected_location", geoPoint)
                        navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", address)
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
