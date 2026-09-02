package com.inventoria.app.ui.screens.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.main.Screen

enum class TodoSegment(val label: String) {
    TODOS("Todos"),
    SCHEDULE("Schedule")
}

/**
 * The Todos tab: the todo list and the schedule calendar, one bar, switched locally.
 *
 * Mirrors InventoryHubScreen deliberately -- plain [rememberSaveable] segment state rather than a
 * nested nav graph, for the save/restore reasons documented on InventoriaApp.switchToTab, with a
 * BackHandler standing in for the one thing a nested graph would have given for free.
 *
 * The hub owns the one and only app bar, so the actions in it change with the segment: the todo
 * list's collapse-all and hide-completed toggles on Todos, a jump-to-today on Schedule. Each
 * segment keeps its own FAB and dialogs; those belong to the content, not the frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoHubScreen(
    todoViewModel: TodoViewModel,
    scheduleViewModel: ScheduleViewModel,
    onNavigateToHelp: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onOpenTaskDetail: (String) -> Unit
) {
    var segment by rememberSaveable { mutableStateOf(TodoSegment.TODOS) }

    BackHandler(enabled = segment != TodoSegment.TODOS) {
        segment = TodoSegment.TODOS
    }

    val hideCompleted by todoViewModel.hideCompleted.collectAsState()
    val collapsedIds by todoViewModel.collapsedTodoIds.collectAsState()

    Scaffold(
        topBar = {
            InventoriaTopBar(
                title = Screen.Todos.title,
                onNavigateToHelp = onNavigateToHelp,
                actions = {
                    when (segment) {
                        TodoSegment.TODOS -> {
                            // Fold/unfold everything at once. Shows whichever action is the useful
                            // one: with nothing folded there is nothing to expand, so it offers to
                            // collapse.
                            IconButton(
                                onClick = {
                                    if (collapsedIds.isEmpty()) todoViewModel.collapseAll() else todoViewModel.expandAll()
                                }
                            ) {
                                Icon(
                                    if (collapsedIds.isEmpty()) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (collapsedIds.isEmpty()) "Collapse all sub-todos" else "Expand all sub-todos"
                                )
                            }
                            IconButton(onClick = { todoViewModel.toggleHideCompleted() }) {
                                Icon(
                                    if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideCompleted) "Show completed todos" else "Hide completed todos",
                                    tint = if (hideCompleted) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                        TodoSegment.SCHEDULE -> {
                            IconButton(onClick = { scheduleViewModel.goToToday() }) {
                                Icon(Icons.Default.Today, contentDescription = "Jump to today")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TodoSegment.entries.forEachIndexed { index, seg ->
                    SegmentedButton(
                        selected = seg == segment,
                        onClick = { segment = seg },
                        shape = SegmentedButtonDefaults.itemShape(index, TodoSegment.entries.size)
                    ) {
                        Text(seg.label)
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (segment) {
                    TodoSegment.TODOS -> TodoScreen(
                        viewModel = todoViewModel,
                        onNavigateToTasks = onNavigateToTasks
                    )
                    TodoSegment.SCHEDULE -> ScheduleScreen(
                        viewModel = scheduleViewModel,
                        onOpenTaskDetail = onOpenTaskDetail
                    )
                }
            }
        }
    }
}
