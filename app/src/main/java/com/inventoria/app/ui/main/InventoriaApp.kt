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
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inventoria.app.ui.screens.clock.ClockScreen
import com.inventoria.app.ui.screens.clock.ClockViewModel
import com.inventoria.app.ui.screens.collections.*
import com.inventoria.app.ui.screens.dashboard.DashboardScreen
import com.inventoria.app.ui.screens.dashboard.DashboardViewModel
import com.inventoria.app.ui.screens.inventory.*
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.*
import com.inventoria.app.ui.screens.task.*
import com.inventoria.app.ui.screens.todo.TodoScreen
import com.inventoria.app.ui.screens.todo.TodoViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory)
    object Collections : Screen("collections", "Collections", Icons.Default.Collections)
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Timer)
    object Todos : Screen("todos", "Todos", Icons.Default.Checklist)
    object Map : Screen("map", "Map", Icons.Default.Map)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

/**
 * Switch to a bottom-nav/rail tab. Every jump to a tab route must go through here, including
 * ones triggered from inside a screen (e.g. the Todo list's "View on Tasks" arrow) rather than
 * from a tab tap.
 *
 * A plain navigate() to a tab route pushes it *on top of* the current tab instead of replacing
 * it, which quietly corrupts the save/restore state the nav bar relies on. Going Todos -> arrow
 * -> Tasks left the stack as [start, Todos, Tasks]; the next Todos tab tap popped both with
 * saveState, and NavController keys a saved sub-stack by its bottom-most entry, so it recorded
 * "Todos -> [Todos, Tasks]". The same tap's restoreState then replayed that pair and landed the
 * user back on Tasks -- and since the restore rebuilds the same stack, every later tap repeated
 * it, making the Todos tab permanently unreachable.
 */
private fun NavController.switchToTab(route: String) {
    // saveState/restoreState remembers each tab's scroll position etc.
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
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
        Screen.Todos,
        Screen.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentBaseRoute = currentDestination?.route?.split("?")?.first()
    // item_location_map (the item-detail "view this location" drill-down) intentionally has no
    // nav bar -- it has its own back button and shouldn't be reachable via tab taps at all.
    val showNavigation = screens.any { it.route == currentBaseRoute }

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
                            // Safe now that item_location_map (the one screen that used to cause
                            // tab-switch confusion) is a separate, non-tab-switchable route with
                            // its own back button -- see its composable() below.
                            navController.switchToTab(screen.route)
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
                                onClick = { navController.switchToTab(screen.route) }
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
                    // Jumps to the Inventory tab proper, so it goes through switchToTab like a
                    // tab tap -- a plain push here would stack duplicate Inventory entries and
                    // drop the tab's saved scroll state.
                    onNavigateToInventory = { navController.switchToTab(Screen.Inventory.route) },
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

            // Same screen/ViewModel as the Map tab above, but a distinct route: this is reached
            // as a drill-down from Item Detail ("view this item's location"), not from the
            // bottom nav. Sharing Screen.Map.route here caused the bottom nav's save/restore
            // state logic to conflate the two. Kept as a separate route with its own back
            // button (no bottom nav here) rather than a tab-switchable destination, so drilling
            // in here never interacts with the tab save/restore mechanism at all.
            composable(
                route = "item_location_map?lat={lat}&lon={lon}",
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
                    onItemClick = { id -> navController.navigate("item_detail/$id") },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Tasks.route) {
                val viewModel: TaskTrackerViewModel = hiltViewModel()
                TaskTrackerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStats = { navController.navigate("productivity_stats") },
                    onNavigateToHistory = { navController.navigate("task_history") },
                    onNavigateToClock = { navController.navigate("timers_alarms") }
                )
            }

            composable("timers_alarms") {
                val viewModel: ClockViewModel = hiltViewModel()
                ClockScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("task_history") {
                val viewModel: TaskTrackerViewModel = hiltViewModel()
                TaskHistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Todos.route) {
                val viewModel: TodoViewModel = hiltViewModel()
                TodoScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    // "View on Tasks" is a tab switch, not a drill-down -- must not plain-push.
                    onNavigateToTasks = { navController.switchToTab(Screen.Tasks.route) }
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
                    onNavigateBack = { navController.popBackStack() },
                    // A drill-down with its own back button, like task_history/productivity_stats
                    // -- deliberately not a tab route, so switchToTab is not involved.
                    onNavigateToTaskTypes = { navController.navigate("task_types") }
                )
            }

            composable("task_types") {
                val viewModel: TaskTypesViewModel = hiltViewModel()
                TaskTypesScreen(
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
            ) { _ ->
                val viewModel: ItemDetailViewModel = hiltViewModel()
                ItemDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditItem = { navController.navigate("edit_item/$it") },
                    onLocationClick = { lat, lon ->
                        navController.navigate("item_location_map?lat=${lat.toFloat()}&lon=${lon.toFloat()}")
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
