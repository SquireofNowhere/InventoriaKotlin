package com.inventoria.app.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoria.app.ui.screens.collections.*
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.dashboard.DashboardViewModel
import com.inventoria.app.ui.screens.inventory.*
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.*
import com.inventoria.app.ui.screens.task.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object Collections : Screen("collections", "Collections", Icons.Default.Collections)
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Timer)
    object Map : Screen("map", "Map", Icons.Default.Map)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaApp() {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isWideScreen = screenWidth >= 600
    val alwaysShowLabels = screenWidth >= 450
    
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Dashboard,
        Screen.Inventory,
        Screen.Collections,
        Screen.Map,
        Screen.Tasks,
        Screen.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showNavigation = screens.any { it.route == currentDestination?.route?.split("?")?.first() }

    Row(Modifier.fillMaxSize()) {
        if (isWideScreen && showNavigation) {
            NavigationRail {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { 
                        it.route?.split("?")?.first() == screen.route 
                    } == true
                    NavigationRailItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { 
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
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

        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!isWideScreen && showNavigation) {
                    NavigationBar {
                        screens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { 
                                it.route?.split("?")?.first() == screen.route 
                            } == true
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { 
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    ) 
                                },
                                selected = selected,
                                alwaysShowLabel = alwaysShowLabels,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
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
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToItemDetail = { id -> navController.navigate("item_detail/$id") }
                )
            }

            composable(
                route = Screen.Inventory.route + "?fromCollection={fromCollection}",
                arguments = listOf(
                    navArgument("fromCollection") { type = NavType.LongType; defaultValue = 0L }
                )
            ) { backStackEntry ->
                val fromCollectionId = backStackEntry.arguments?.getLong("fromCollection") ?: 0L
                val viewModel: InventoryListViewModel = hiltViewModel()
                InventoryListScreen(
                    viewModel = viewModel,
                    fromCollectionId = fromCollectionId,
                    onAddItem = { navController.navigate("add_item") },
                    onItemClick = { id -> navController.navigate("item_detail/$id") },
                    onEditItem = { id -> navController.navigate("edit_item/$id") },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Collections.route) {
                val viewModel: CollectionsViewModel = hiltViewModel()
                CollectionsScreen(
                    viewModel = viewModel,
                    onNavigateToCollectionDetail = { id -> navController.navigate("collection/$id") },
                    onNavigateToCreateCollection = { navController.navigate("collection/create") }
                )
            }

            composable("collection/create") {
                val viewModel: AddEditCollectionViewModel = hiltViewModel()
                AddEditCollectionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "collection/edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                val viewModel: AddEditCollectionViewModel = hiltViewModel()
                AddEditCollectionScreen(
                    collectionId = id,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "collection/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                val viewModel: CollectionDetailViewModel = hiltViewModel()
                CollectionDetailScreen(
                    collectionId = id,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditCollection = { navController.navigate("collection/edit/$it") },
                    onNavigateToAddItems = { navController.navigate(Screen.Inventory.route + "?fromCollection=$it") },
                    onNavigateToItemDetail = { navController.navigate("item_detail/$it") }
                )
            }

            composable(
                route = Screen.Map.route + "?lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.FloatType; defaultValue = -1f },
                    navArgument("lon") { type = NavType.FloatType; defaultValue = -1f }
                )
            ) { backStackEntry ->
                val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble()?.takeIf { it != -1.0 }
                val lon = backStackEntry.arguments?.getFloat("lon")?.toDouble()?.takeIf { it != -1.0 }
                val initialLocation = if (lat != null && lon != null) lat to lon else null
                val viewModel: InventoryListViewModel = hiltViewModel()
                InventoryMapScreen(
                    viewModel = viewModel,
                    initialLocation = initialLocation,
                    onItemClick = { id -> navController.navigate("item_detail/$id") }
                )
            }

            composable(Screen.Tasks.route) {
                val viewModel: TaskTrackerViewModel = hiltViewModel()
                TaskTrackerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStats = { navController.navigate("productivity_stats") },
                    onNavigateToHistory = { navController.navigate("task_history") }
                )
            }

            composable("task_history") {
                val viewModel: TaskTrackerViewModel = hiltViewModel()
                TaskHistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("productivity_stats") {
                val viewModel: TaskTrackerViewModel = hiltViewModel()
                ProductivityStatsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "add_item?parentId={parentId}",
                arguments = listOf(
                    navArgument("parentId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                val viewModel: AddEditItemViewModel = hiltViewModel()
                AddEditItemScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { navController.navigate("location_picker") }
                )
            }

            composable(
                route = "item_detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("itemId") ?: 0L
                val viewModel: ItemDetailViewModel = hiltViewModel()
                ItemDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditItem = { navController.navigate("edit_item/$it") },
                    onLocationClick = { lat, lon -> 
                        navController.navigate(Screen.Map.route + "?lat=${lat.toFloat()}&lon=${lon.toFloat()}")
                    },
                    onNavigateToItemDetail = { navController.navigate("item_detail/$it") },
                    onAddItemInside = { navController.navigate("add_item?parentId=$it") },
                    onNavigateToCollection = { navController.navigate("collection/$it") }
                )
            }

            composable(
                route = "edit_item/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                val viewModel: AddEditItemViewModel = hiltViewModel()
                AddEditItemScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPickLocation = { navController.navigate("location_picker") }
                )
            }

            composable("location_picker") {
                val backStackEntry = remember(it) { navController.getBackStackEntry("add_item") }
                val viewModel: AddEditItemViewModel = hiltViewModel(backStackEntry)
                LocationPickerScreen(
                    initialLocation = viewModel.uiState.value.geoPoint,
                    onLocationSelected = { point ->
                        viewModel.updateLocation(point)
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}
