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
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.ui.screens.clock.ClockScreen
import com.inventoria.app.ui.screens.clock.ClockViewModel
import com.inventoria.app.ui.screens.help.HelpArticleScreen
import com.inventoria.app.ui.screens.help.HelpCategoryScreen
import com.inventoria.app.ui.screens.help.HelpIndexScreen
import com.inventoria.app.ui.screens.help.catalog.HelpCatalog
import com.inventoria.app.ui.screens.collections.*
import com.inventoria.app.ui.screens.inventory.*
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.screens.settings.*
import com.inventoria.app.ui.screens.task.*
import com.inventoria.app.ui.screens.today.TodayScreen
import com.inventoria.app.ui.screens.today.TodayViewModel
import com.inventoria.app.ui.screens.todo.ScheduleViewModel
import com.inventoria.app.ui.screens.todo.TodoHubScreen
import com.inventoria.app.ui.screens.todo.TodoViewModel

/**
 * A bottom-nav/rail tab, and nothing else. Every other destination below is a plain string
 * literal, the same way collection/create, task_history and item_location_map always were.
 *
 * This used to double as a route holder for screens that weren't really tabs (Inventory's route
 * got string-concatenated with "?fromCollection=" at its call site), which is exactly the
 * ambiguity that made it easy to plain-navigate to a tab route by accident -- see switchToTab.
 *
 * [title] is the screen's real name and the single source of it -- the same string its top app bar
 * shows, so the tab you tapped and the screen you landed on agree. [shortTitle] is only a fallback
 * for when [title] can't fit a nav item at the current width; see [AdaptiveNavLabel]. Where a name
 * is already short both are the same string.
 *
 * [helpCategoryId] is the HelpCatalog category this tab's help button aims at. It's an id rather
 * than a route so the tab decides *what* it's about and the nav layer decides where that lands --
 * see openHelpFor, which redirects to the index when a category has nothing written in it yet.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val shortTitle: String,
    val icon: ImageVector,
    val helpCategoryId: String
) {
    object Today : Screen("today", "Today", "Today", Icons.Default.Today, "today")
    object Todos : Screen("todos", "Todos", "Plan", Icons.Default.Checklist, "todos")
    object Tasks : Screen("tasks", "Task Tracker", "Track", Icons.Default.Timer, "tasks")
    object InventoryHub :
        Screen("inventory_hub", "Inventory", "Inventory", Icons.Default.Inventory, "inventory")
    object Settings : Screen("settings", "Settings", "Settings", Icons.Default.Settings, "settings")
}

/**
 * Draws [Screen.title], swapping to [Screen.shortTitle] if the full name would be ellipsized.
 *
 * Measuring rather than guessing at a breakpoint, because how much fits depends on the tab count,
 * the display's font scale and the user's chosen system font -- all of which can change without the
 * screen width changing. The first layout pass reports the overflow and the second draws the short
 * form; the short form is by definition narrower, so this settles in one swap and can't oscillate.
 *
 * Keyed on the width so a fold or rotation that widens the bar gets to try the full name again
 * rather than staying stuck on the abbreviation.
 */
@Composable
private fun AdaptiveNavLabel(screen: Screen) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    var useShort by remember(screen, screenWidth) { mutableStateOf(false) }

    Text(
        text = if (useShort) screen.shortTitle else screen.title,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!useShort && result.hasVisualOverflow) useShort = true
        }
    )
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

/**
 * Nav bar/rail order for a chosen focus: Today stays first (it's the start destination and the
 * dashboard), the focus tab comes right after it, the other two areas keep their canonical
 * relative order, Settings stays last. TODOS reproduces the pre-focus order exactly.
 *
 * Order is all this changes -- every tab is always in the list, the NavHost's composable()
 * registrations stay put, and switching focus performs no navigation, which is what keeps the
 * save/restore behaviour switchToTab's KDoc warns about out of reach.
 */
private fun tabOrderFor(focus: FocusArea): List<Screen> = when (focus) {
    FocusArea.INVENTORY ->
        listOf(Screen.Today, Screen.InventoryHub, Screen.Todos, Screen.Tasks, Screen.Settings)
    FocusArea.TASKS ->
        listOf(Screen.Today, Screen.Tasks, Screen.Todos, Screen.InventoryHub, Screen.Settings)
    FocusArea.TODOS ->
        listOf(Screen.Today, Screen.Todos, Screen.Tasks, Screen.InventoryHub, Screen.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaApp() {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isWideScreen = screenWidth >= 600

    val navController = rememberNavController()

    // Activity-scoped: owns the focus pref the bar orders itself by, plus the one-time launch
    // dialogs, which must survive tab switches.
    val launchViewModel: AppLaunchViewModel = hiltViewModel()
    val focusArea by launchViewModel.focusArea.collectAsState()
    val screens = remember(focusArea) { tabOrderFor(focusArea) }

    val showFocusPrompt by launchViewModel.showFocusPrompt.collectAsState()
    val pendingWhatsNew by launchViewModel.pendingWhatsNew.collectAsState()
    // Strictly sequential, never stacked: the focus choice reshapes the tabs and dashboard the
    // changelog describes, so it goes first and What's New waits for it to be answered.
    if (showFocusPrompt) {
        FocusPromptDialog(
            onChoose = { launchViewModel.chooseFocus(it) },
            onDismiss = { launchViewModel.dismissFocusPrompt() }
        )
    } else {
        pendingWhatsNew?.let { entries ->
            WhatsNewDialog(
                entries = entries,
                onDismiss = { launchViewModel.dismissWhatsNew() },
                // Dismiss first: the history screen shows the same entries, so reading them there
                // counts as having seen them. Plain push -- it's a drill-down with its own back.
                onSeeAll = {
                    launchViewModel.dismissWhatsNew()
                    navController.navigate("version_history")
                }
            )
        }
    }

    /**
     * Open the manual at the section covering [categoryId], or at the index if that section has
     * nothing in it yet.
     *
     * Most categories are still empty stubs, and HelpCategoryScreen would render one as a lone
     * summary line -- a dead end reached by pressing the button marked "help". The index at least
     * shows the shape of the manual and reaches the sections that are written. This check is why
     * Screen carries a category id rather than a route: as articles get written, each tab's help
     * button starts landing on its own section with no change here.
     *
     * HelpCatalog is deliberately lazy (it must not be built at launch), so this only touches it
     * inside the lambda, on tap.
     */
    val openHelpFor: (String) -> Unit = { categoryId ->
        val hasArticles = HelpCatalog.category(categoryId)?.articles?.isNotEmpty() == true
        navController.navigate(if (hasArticles) "help/category/$categoryId" else "help")
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentBaseRoute = currentDestination?.route?.split("?")?.first()
    // Anything that isn't a tab renders chrome-free with its own back button: item_location_map
    // (the item-detail "view this location" drill-down), settings, help, and the collection item
    // picker (inventory?fromCollection=), which is a modal sub-mode with its own BackHandler,
    // unsaved-changes dialog and confirm FAB.
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
                        label = { AdaptiveNavLabel(screen) },
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
                                label = { AdaptiveNavLabel(screen) },
                                selected = selected,
                                // Four tabs always leave room for a label, even at 360dp -- the
                                // label just shortens itself if the full title doesn't fit.
                                alwaysShowLabel = true,
                                onClick = { navController.switchToTab(screen.route) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    todayViewModel = hiltViewModel<TodayViewModel>(),
                    // A different TodoViewModel instance than the Todos destination holds -- see
                    // TodayScreen's KDoc for what that rules out (editing, tap-to-select).
                    todoViewModel = hiltViewModel<TodoViewModel>(),
                    onNavigateToHelp = { openHelpFor(Screen.Today.helpCategoryId) },
                    // Both of these are tab jumps, not drill-downs -- must not plain-push.
                    onNavigateToTodos = { navController.switchToTab(Screen.Todos.route) },
                    onNavigateToTasks = { navController.switchToTab(Screen.Tasks.route) }
                )
            }

            composable(Screen.InventoryHub.route) {
                InventoryHubScreen(
                    inventoryViewModel = hiltViewModel<InventoryListViewModel>(),
                    collectionsViewModel = hiltViewModel<CollectionsViewModel>(),
                    hubViewModel = hiltViewModel<InventoryHubViewModel>(),
                    onNavigateToHelp = { openHelpFor(Screen.InventoryHub.helpCategoryId) },
                    onAddItem = { navController.navigate("add_item") },
                    onItemClick = { id -> navController.navigate("item_detail/$id") },
                    onEditItem = { id -> navController.navigate("edit_item/$id") },
                    onCollectionClick = { id -> navController.navigate("collection/$id") },
                    onCreateCollection = { navController.navigate("collection/create") }
                )
            }

            // The collection item picker, pushed from CollectionDetailScreen -- not the Inventory
            // tab. Kept a separate route rather than a hub segment because it's a modal sub-mode
            // of InventoryListScreen (staged selection, BackHandler, unsaved-changes dialog, Save
            // FAB) and none of that survives being a tab.
            composable(
                route = "inventory?fromCollection={fromCollection}",
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
                    onNavigateToAddItems = { navController.navigate("inventory?fromCollection=$it") },
                    onNavigateToItemDetail = { navController.navigate("item_detail/$it") }
                )
            }

            // The map as reached from Item Detail ("view this item's location"), rather than from
            // the Inventory tab's Map segment. Kept a separate, non-tab route with its own back
            // button so drilling in here never interacts with the tab save/restore mechanism at
            // all -- when the map WAS a tab, sharing one route between the two conflated them.
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
                    onNavigateToHelp = { openHelpFor(Screen.Tasks.helpCategoryId) },
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

            // Todos | Schedule, switched locally inside the hub -- same arrangement as InventoryHub,
            // for the same save/restore reasons its KDoc gives.
            composable(Screen.Todos.route) {
                TodoHubScreen(
                    todoViewModel = hiltViewModel<TodoViewModel>(),
                    scheduleViewModel = hiltViewModel<ScheduleViewModel>(),
                    onNavigateToHelp = { openHelpFor(Screen.Todos.helpCategoryId) },
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

            // A tab again, not a drill-down off Today's overflow -- so it keeps the nav bar, has no
            // back arrow, and every jump to it goes through switchToTab like any other tab.
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToTaskTypes = { navController.navigate("task_types") },
                    // The bar's "?" aims at the Settings section; the screen's own "How To" row
                    // still opens the manual's index, which is what that row has always meant.
                    onNavigateToHelp = { openHelpFor(Screen.Settings.helpCategoryId) },
                    onNavigateToHelpIndex = { navController.navigate("help") },
                    onNavigateToVersionHistory = { navController.navigate("version_history") }
                )
            }

            // Reached from Settings > About and from the What's New dialog's "See all".
            composable("version_history") {
                VersionHistoryScreen(onNavigateBack = { navController.popBackStack() })
            }

            // The manual. Static route first, matching the collection/create-before-collection/{id}
            // ordering this NavHost already relies on.
            composable("help") {
                HelpIndexScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenCategory = { navController.navigate("help/category/$it") },
                    onOpenArticle = { navController.navigate("help/article/$it") }
                )
            }

            composable(
                route = "help/category/{categoryId}",
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ) { backStackEntry ->
                HelpCategoryScreen(
                    categoryId = backStackEntry.arguments?.getString("categoryId").orEmpty(),
                    onNavigateBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate("help/article/$it") }
                )
            }

            composable(
                route = "help/article/{articleId}",
                arguments = listOf(navArgument("articleId") { type = NavType.StringType })
            ) { backStackEntry ->
                HelpArticleScreen(
                    articleId = backStackEntry.arguments?.getString("articleId").orEmpty(),
                    onNavigateBack = { navController.popBackStack() },
                    // A plain push, so following a chain of related guides and then going back
                    // retraces your reading order.
                    onOpenArticle = { navController.navigate("help/article/$it") }
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
