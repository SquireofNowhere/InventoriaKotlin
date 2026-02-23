package com.inventoria.app.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.inventory.InventoryListScreen
import com.inventoria.app.ui.screens.inventory.AddEditItemScreen
import com.inventoria.app.ui.screens.inventory.ItemDetailScreen
import com.inventoria.app.ui.screens.settings.SettingsScreen
import com.inventoria.app.ui.screens.task.TaskTrackerScreen
import androidx.compose.ui.unit.dp

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object TaskTracker : Screen("task_tracker", "Tasks", Icons.Default.Timer)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AddEditItem : Screen("add_edit_item", "Add/Edit Item", Icons.Default.Add)
    object ItemDetail : Screen("item_detail", "Item Detail", Icons.Default.Info)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Inventory,
    Screen.TaskTracker,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Check if current screen is part of the bottom navigation hierarchy
    val shouldShowBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == screen.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToInventory = {
                        // Navigate to Inventory as a Tab Switch to prevent nested state issues
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAddItem = {
                        navController.navigate(Screen.AddEditItem.route + "/-1")
                    },
                    onNavigateToItemDetail = { itemId ->
                        navController.navigate(Screen.ItemDetail.route + "/$itemId")
                    }
                )
            }
            
            composable(Screen.Inventory.route) {
                InventoryListScreen(
                    onAddItem = {
                        navController.navigate(Screen.AddEditItem.route + "/-1")
                    },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.route + "/$itemId")
                    }
                )
            }

            composable(Screen.TaskTracker.route) {
                TaskTrackerScreen()
            }
            
            composable(
                route = Screen.ItemDetail.route + "/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                ItemDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEditItem = { itemId ->
                        navController.navigate(Screen.AddEditItem.route + "/$itemId")
                    }
                )
            }
            
            composable(
                route = Screen.AddEditItem.route + "/{itemId}",
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                AddEditItemScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
