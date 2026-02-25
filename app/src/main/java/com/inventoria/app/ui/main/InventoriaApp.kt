package com.inventoria.app.ui.main

import android.util.Log
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.inventory.*
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.SettingsScreen
import com.inventoria.app.ui.screens.task.TaskTrackerScreen

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
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Inventory,
        Screen.Map,
        Screen.Tasks,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route
            val showBottomBar = bottomNavItems.any { item ->
                currentDestination?.hierarchy?.any {
                    it.route?.split("?")?.firstOrNull() == item.route
                } == true
            } || currentRoute?.startsWith("item_detail/") == true || currentRoute?.startsWith(Screen.Map.route) == true ||  currentRoute?.startsWith("edit_item/") == true

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val isSelected = when {
                            currentRoute?.startsWith("item_detail/") == true || currentRoute?.startsWith("edit_item/") == true -> {
                                val origin = navBackStackEntry?.arguments?.getString("origin")
                                screen.route == (origin ?: Screen.Dashboard.route)
                            }
                            currentRoute?.startsWith(Screen.Map.route) == true -> {
                                val lat = navBackStackEntry?.arguments?.getFloat("lat", -1f) ?: -1f
                                if (lat != -1f) {
                                    val origin = navBackStackEntry?.arguments?.getString("origin")
                                    screen.route == (origin ?: Screen.Dashboard.route)
                                } else {
                                    screen.route == Screen.Map.route
                                }
                            }
                            else -> currentDestination?.hierarchy?.any {
                                it.route?.split("?")?.firstOrNull() == screen.route
                            } == true
                        }

                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                val isRoot = currentRoute?.split("?")?.firstOrNull() == screen.route
                                Log.d("InventoriaNav", "Click on ${screen.route}. currentRoute: $currentRoute, isSelected: $isSelected, isRoot: $isRoot")

                                if (isSelected) {
                                    if (!isRoot) {
                                        Log.d("InventoriaNav", "Selected but not root, navigating to root of ${screen.route}")
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    } else {
                                        Log.d("InventoriaNav", "Already at root of selected tab, doing nothing")
                                    }
                                } else {
                                    Log.d("InventoriaNav", "Switching to tab ${screen.route}")
                                    navController.navigate(screen.route) {
                                        // Standard bottom nav behavior
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                        
                                        // Special case for Map to avoid crash
                                        val currentRouteOnClick = navController.currentBackStackEntry?.destination?.route
                                        if (currentRouteOnClick?.startsWith(Screen.Map.route) == true) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                        }
                                    }
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
                    onNavigateToInventory = { 
                        navController.navigate("${Screen.Inventory.route}?fromDashboard=true") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                                inclusive = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id?origin=${Screen.Dashboard.route}") }
                )
            }
            
            composable(
                route = "${Screen.Inventory.route}?fromDashboard={fromDashboard}",
                arguments = listOf(
                    navArgument("fromDashboard") { 
                        type = NavType.BoolType
                        defaultValue = false 
                    }
                )
            ) { backStackEntry ->
                val fromDashboard = backStackEntry.arguments?.getBoolean("fromDashboard") == true
                InventoryListScreen(
                    onAddItem = { navController.navigate("add_item") },
                    onItemClick = { id -> navController.navigate("item_detail/$id?origin=${Screen.Inventory.route}") },
                    onNavigateBack = if (fromDashboard) { { 
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } } else null
                )
            }

            composable(
                route = "${Screen.Map.route}?lat={lat}&lon={lon}&origin={origin}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.FloatType; defaultValue = -1f },
                    navArgument("lon") { type = NavType.FloatType; defaultValue = -1f },
                    navArgument("origin") { type = NavType.StringType; defaultValue = Screen.Dashboard.route }
                )
            ) { backStackEntry ->
                val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble()
                val lon = backStackEntry.arguments?.getFloat("lon")?.toDouble()
                val origin = backStackEntry.arguments?.getString("origin") ?: Screen.Dashboard.route
                InventoryMapScreen(
                    onItemClick = { id -> navController.navigate("item_detail/$id?origin=$origin") },
                    initialLocation = if (lat != null && lon != null && lat != -1.0 && lon != -1.0) Pair(lat, lon) else null
                )
            }

            composable(Screen.Tasks.route) {
                TaskTrackerScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable("add_item") {
                val viewModel: AddEditItemViewModel = hiltViewModel()
                AddEditItemScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { 
                        navController.navigate("location_picker/add_item") 
                    }
                )
            }

            composable(
                route = "item_detail/{itemId}?origin={origin}",
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType },
                    navArgument("origin") { type = NavType.StringType; defaultValue = Screen.Dashboard.route }
                )
            ) { backStackEntry ->
                val origin = backStackEntry.arguments?.getString("origin") ?: Screen.Dashboard.route
                ItemDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEditItem = { id -> navController.navigate("edit_item/$id?origin=$origin") },
                    onLocationClick = { lat, lon ->
                        navController.navigate("${Screen.Map.route}?lat=$lat&lon=$lon&origin=$origin") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id?origin=$origin") }
                )
            }

            composable(
                route = "edit_item/{itemId}?origin={origin}",
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType },
                     navArgument("origin") { type = NavType.StringType; defaultValue = Screen.Dashboard.route }
                )
            ) { backStackEntry ->
                val viewModel: AddEditItemViewModel = hiltViewModel()
                AddEditItemScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { 
                        val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                        navController.navigate("location_picker/edit_item_$itemId") 
                    }
                )
            }

            composable(
                route = "location_picker/{origin}",
                arguments = listOf(
                    navArgument("origin") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.previousBackStackEntry
                }
                
                if (parentEntry != null) {
                    val viewModel: AddEditItemViewModel = hiltViewModel(parentEntry)
                    LocationPickerScreen(
                        initialLocation = viewModel.currentLocationGeoPoint,
                        onLocationSelected = { geoPoint ->
                            viewModel.updateLocation(geoPoint)
                            navController.popBackStack()
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    // Fallback or navigate back if we lost the parent entry
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}
