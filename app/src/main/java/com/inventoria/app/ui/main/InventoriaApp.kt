package com.inventoria.app.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoria.app.ui.screens.collections.AddEditCollectionScreen
import com.inventoria.app.ui.screens.collections.CollectionDetailScreen
import com.inventoria.app.ui.screens.collections.CollectionsScreen
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.inventory.AddEditItemScreen
import com.inventoria.app.ui.screens.inventory.AddEditItemViewModel
import com.inventoria.app.ui.screens.inventory.InventoryListScreen
import com.inventoria.app.ui.screens.inventory.ItemDetailScreen
import com.inventoria.app.ui.screens.inventory.LocationPickerScreen
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.SettingsScreen
import com.inventoria.app.ui.screens.task.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object Collections : Screen("collections", "Collections", Icons.Default.Collections)
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

    val items = listOf(
        Screen.Dashboard,
        Screen.Inventory,
        Screen.Collections,
        Screen.Map,
        Screen.Tasks,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title, fontSize = 10.sp) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToAddItem = { navController.navigate("add_item") },
                        onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id?origin=${Screen.Dashboard.route}") }
                    )
                }
                
                composable(
                    route = "${Screen.Inventory.route}?fromDashboard={fromDashboard}&fromCollection={fromCollection}",
                    arguments = listOf(
                        navArgument("fromDashboard") { 
                            type = NavType.BoolType
                            defaultValue = false 
                        },
                        navArgument("fromCollection") {
                            type = NavType.LongType
                            defaultValue = 0L
                        }
                    )
                ) { backStackEntry ->
                    val fromDashboard = backStackEntry.arguments?.getBoolean("fromDashboard") == true
                    val fromCollectionId = backStackEntry.arguments?.getLong("fromCollection") ?: 0L
                    
                    InventoryListScreen(
                        onAddItem = { navController.navigate("add_item") },
                        onItemClick = { id -> 
                            if (fromCollectionId == 0L) {
                                navController.navigate("item_detail/$id?origin=${Screen.Inventory.route}")
                            }
                        },
                        onNavigateBack = if (fromDashboard || fromCollectionId != 0L) { { 
                            navController.popBackStack()
                        } } else null,
                        fromCollectionId = fromCollectionId
                    )
                }

                composable(Screen.Collections.route) {
                    CollectionsScreen(
                        onNavigateToCollectionDetail = { id ->
                            navController.navigate("collection/$id")
                        },
                        onNavigateToCreateCollection = {
                            navController.navigate("collection/create")
                        }
                    )
                }

                composable("collection/create") {
                    AddEditCollectionScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "collection/edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    AddEditCollectionScreen(
                        collectionId = id,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "collection/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    CollectionDetailScreen(
                        collectionId = id,
                        onNavigateBack = { navController.popBackStack() },
                        onEditCollection = { collectionId ->
                            navController.navigate("collection/edit/$collectionId")
                        },
                        onNavigateToAddItems = { collectionId ->
                            navController.navigate("${Screen.Inventory.route}?fromCollection=$collectionId")
                        },
                        onNavigateToItemDetail = { itemId ->
                            navController.navigate("item_detail/$itemId?origin=collection/$id")
                        }
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
                    val trackerViewModel: TaskTrackerViewModel = hiltViewModel()
                    TaskTrackerScreen(
                        viewModel = trackerViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToHistory = { navController.navigate("task_history") },
                        onNavigateToStats = { navController.navigate("productivity_stats") }
                    )
                }
                
                composable("task_history") {
                    TaskHistoryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("productivity_stats") {
                    ProductivityStatsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen()
                }

                composable(
                    route = "add_item?parentId={parentId}",
                    arguments = listOf(
                        navArgument("parentId") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { _ ->
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
                        onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id?origin=$origin") },
                        onAddItemInside = { parentId -> navController.navigate("add_item?parentId=$parentId") },
                        onNavigateToCollection = { id -> navController.navigate("collection/$id") }
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
                        LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    }
                }
            }
        }
    }
}
