package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val currentTime by rememberTick()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val isSelectionMode = selectedTaskIds.isNotEmpty()
    val context = LocalContext.current
    
    var selectedSessionGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    val currentSelectedSession = remember(selectedSessionGroupId, completedSessions) {
        selectedSessionGroupId?.let { groupId ->
            completedSessions.find { it.firstOrNull()?.groupId == groupId }
        }
    }

    val currentSelectedTask = remember(selectedTaskId, completedSessions) {
        selectedTaskId?.let { id ->
            completedSessions.flatten().find { it.id == id }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTaskIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveSelectedTasksToCalendar() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Selected")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedTasks() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Task History", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (completedSessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No tasks recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(completedSessions) { session ->
                    if (session.size > 1) {
                        CompletedSessionCard(
                            segments = session,
                            currentTime = currentTime,
                            selectedTaskIds = selectedTaskIds,
                            onClick = { selectedSessionGroupId = session.first().groupId },
                            onDelete = { viewModel.deleteSession(session.first().groupId) },
                            onSegmentClick = { 
                                if (isSelectionMode) viewModel.toggleTaskSelection(it.id)
                                else selectedTaskId = it.id 
                            },
                            onSegmentLongClick = { task -> viewModel.toggleTaskSelection(task.id) },
                            onSegmentDelete = { viewModel.deleteSegment(it) },
                            onSegmentToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) }
                        )
                    } else {
                        val task = session.first()
                        SingleTaskItemCard(
                            task = task,
                            isSelected = task.id in selectedTaskIds,
                            onClick = { 
                                if (isSelectionMode) viewModel.toggleTaskSelection(task.id)
                                else selectedTaskId = task.id
                            },
                            onLongClick = { viewModel.toggleTaskSelection(task.id) },
                            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                            onDelete = { viewModel.deleteSegment(task) },
                            onAddToCalendar = { addToGoogleCalendar(context, task) }
                        )
                    }
                }
            }
        }
    }

    currentSelectedSession?.let { segments ->
        SessionDetailDialog(
            segments = segments,
            onDismiss = { selectedSessionGroupId = null },
            onUpdateSessionName = { name -> viewModel.updateSessionName(segments.first().groupId, name) },
            onUpdateSessionKind = { kind -> viewModel.updateSessionKind(segments.first().groupId, kind) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) },
            onNavigateToTaskDetail = { selectedTaskId = it },
            onDeleteSegment = { viewModel.deleteSegment(it) }
        )
    }

    currentSelectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTaskId = null },
            onSaveName = { viewModel.updateCompletedTaskName(task, it) },
            onKindChange = { viewModel.updateCompletedTaskKind(task, it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, it) },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) },
            onDelete = { viewModel.deleteSegment(task); selectedTaskId = null }
        )
    }
}
