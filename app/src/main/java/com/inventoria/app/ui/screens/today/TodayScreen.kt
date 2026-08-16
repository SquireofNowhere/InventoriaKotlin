package com.inventoria.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.components.LinearProductivityChart
import com.inventoria.app.ui.main.Screen
import com.inventoria.app.ui.screens.todo.TodoDayHeader
import com.inventoria.app.ui.screens.todo.TodoRow
import com.inventoria.app.ui.screens.todo.TodoViewModel
import com.inventoria.app.ui.screens.todo.currentMinuteOfDay
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.delay

/**
 * The app's home: what's on today, and how the day has actually gone so far.
 *
 * Todo rows here are the same TodoRow the Todos screen draws, minus the drag handle and delete
 * button -- Today can check things off and start tracking them, but reordering/parenting/deleting
 * belong to the screen that owns the list.
 *
 * Row taps go to Todos rather than opening the edit dialog: this screen's TodoViewModel is a
 * different instance from that screen's (hiltViewModel resolves against the nav entry), so
 * startEditingTodo would set pendingEditTodo on an instance nothing renders a dialog for. Same
 * reason Today doesn't use tap-to-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    todayViewModel: TodayViewModel,
    todoViewModel: TodoViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToTodos: () -> Unit,
    onNavigateToTasks: () -> Unit
) {
    val tasks by todayViewModel.tasks.collectAsState()
    val todoSections by todoViewModel.todoSections.collectAsState()
    val taskTypeNames by todoViewModel.taskTypeNamesById.collectAsState()
    val todoIdsWithActiveSession by todoViewModel.todoIdsWithActiveSession.collectAsState()

    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }
    // Overdue-and-incomplete todos (and any deadline-less children trailing them) already resolve
    // into the today section upstream, in TodoViewModel.effectiveSectionDay -- so "today's list"
    // really is just the section keyed to today, with no extra filtering here.
    val todaySection = todoSections.firstOrNull { it.dayStart == todayStart }

    // Wall-clock minute, re-read once a minute, so a due time flips to its "past due" styling on
    // its own instead of waiting for some unrelated recomposition. Same ticker TodoScreen runs.
    val nowMinuteOfDay by produceState(currentMinuteOfDay()) {
        while (true) {
            delay(60_000)
            value = currentMinuteOfDay()
        }
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            InventoriaTopBar(
                // From the tab definition, not a literal -- the nav label and the bar showing two
                // different names for one screen is exactly what this pass was fixing.
                title = Screen.Today.title,
                actions = {
                    IconButton(onClick = { todayViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    // Settings and the manual both live behind here now that neither is a tab.
                    // Deliberately only on Today: it's the start destination, so one canonical
                    // way in beats scattering the same two entries across every tab.
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings and help")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { menuOpen = false; onNavigateToSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text("How To") },
                                leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                                onClick = { menuOpen = false; onNavigateToHelp() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (todaySection == null) {
                item { NothingDueToday(onNavigateToTodos) }
            } else {
                item(key = "today_header") {
                    TodoDayHeader(
                        todaySection.dayStart,
                        todaySection.totalDueCount,
                        todaySection.completedDueCount
                    )
                }
                items(todaySection.visibleTodos, key = { it.todo.id }) { entry ->
                    TodoRow(
                        entry = entry,
                        todayStart = todayStart,
                        nowMinuteOfDay = nowMinuteOfDay,
                        taskTypeNames = taskTypeNames,
                        hasActiveSession = entry.todo.id in todoIdsWithActiveSession,
                        onToggleCompleted = { todoViewModel.toggleComplete(entry.todo) },
                        // Editing lives on the Todos screen -- see the class KDoc.
                        onClick = onNavigateToTodos,
                        onStart = { todoViewModel.startTaskFromTodo(entry.todo) },
                        onViewTask = onNavigateToTasks,
                        showDragHandle = false,
                        showDelete = false
                    )
                }
            }

            item(key = "timeline") {
                Spacer(Modifier.height(8.dp))
                TodayTimelineCard(tasks)
            }
        }
    }
}

/**
 * The 24-hour timeline, on the gradient the old dashboard header used.
 *
 * The gradient isn't decoration: LinearProductivityChart draws its track, elapsed shading, now-line
 * and hour labels in white/black alphas, which need a saturated backdrop to read at all. Don't drop
 * this onto a plain surface without parameterising the chart's colours first.
 */
@Composable
private fun TodayTimelineCard(tasks: List<Task>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    getDayLabel(getStartOfDay(System.currentTimeMillis())),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "How your day has gone so far.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                LinearProductivityChart(
                    tasks = tasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun NothingDueToday(onNavigateToTodos: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Nothing due today.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Anything you give a deadline of today shows up here, ready to check off or start tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        FilledTonalButton(onClick = onNavigateToTodos) {
            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Plan something")
        }
    }
}
