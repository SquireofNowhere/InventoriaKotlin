package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
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
            CenterAlignedTopAppBar(
                title = { Text("Task History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                            isSelected = session.any { it.id in selectedTaskIds },
                            onClick = { selectedSessionGroupId = session.first().groupId },
                            onDelete = { viewModel.deleteSessionPermanently(session.first().groupId) },
                            onSegmentLongClick = { viewModel.toggleTaskSelection(it.id) }
                        )
                    } else {
                        val task = session.first()
                        SingleTaskItemCard(
                            task = task,
                            isSelected = task.id in selectedTaskIds,
                            onClick = { selectedTaskId = task.id },
                            onLongClick = { viewModel.toggleTaskSelection(task.id) },
                            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                            onDelete = { viewModel.deleteSegmentPermanently(task) },
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
            onUpdateSegment = { viewModel.updateSegment(it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) }
        )
    }

    currentSelectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTaskId = null },
            onSaveName = { viewModel.updateCompletedTaskName(task, it) },
            onKindChange = { viewModel.updateCompletedTaskKind(task, it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, it) },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) }
        )
    }
}
