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
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.Task
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val completedSessions by viewModel.completedSessions.collectAsState()
    
    // Past year filter (consistent with stats)
    val oneYearAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -364) }.timeInMillis
    val historySessions = completedSessions.filter { session ->
        session.maxOf { it.startTime } >= oneYearAgo
    }

    var sessionToShowDetail by remember { mutableStateOf<List<Task>?>(null) }
    var singleTaskToShowDetail by remember { mutableStateOf<Task?>(null) }

    if (singleTaskToShowDetail != null) {
        val task = singleTaskToShowDetail!!
        TaskDetailDialog(
            task = task,
            onDismiss = { singleTaskToShowDetail = null },
            onSaveName = { viewModel.updateCompletedTaskName(task, it) },
            onKindChange = { viewModel.updateCompletedTaskKind(task, it) },
            onToggleCalendar = { 
                val newState = !task.savedToCalendar
                viewModel.setSegmentCalendarStatus(task, newState)
            },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) }
        )
    }

    if (sessionToShowDetail != null) {
        val groupId = sessionToShowDetail!!.first().groupId
        val currentSession = (completedSessions.find { it.first().groupId == groupId }) ?: sessionToShowDetail!!
        
        SessionDetailDialog(
            segments = currentSession,
            onDismiss = { sessionToShowDetail = null },
            onUpdateSessionName = { newName -> viewModel.updateSessionName(groupId, newName) },
            onUpdateSessionKind = { newKind -> viewModel.updateSessionKind(groupId, newKind) },
            onUpdateSegment = { updatedSegment -> viewModel.updateSegment(updatedSegment) },
            onToggleCalendar = { segment ->
                val newState = !segment.savedToCalendar
                viewModel.setSegmentCalendarStatus(segment, newState)
            },
            onFlatten = {
                viewModel.flattenSession(groupId)
                sessionToShowDetail = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task History (Past Year)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (historySessions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No history found for the last year.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(historySessions) { sessionSegments ->
                if (sessionSegments.size > 1) {
                    CompletedSessionCard(
                        segments = sessionSegments,
                        onClick = { sessionToShowDetail = sessionSegments },
                        onDelete = { viewModel.clearSession(sessionSegments.first().groupId) }
                    )
                } else {
                    val task = sessionSegments.first()
                    SingleTaskItemCard(
                        task = task,
                        onClick = { singleTaskToShowDetail = task },
                        onToggleCalendar = {
                            if (!task.id.startsWith("cal_")) {
                                val newState = !task.savedToCalendar
                                viewModel.setSegmentCalendarStatus(task, newState)
                            }
                        },
                        onDelete = { viewModel.clearSegment(task) },
                        onAddToCalendar = { addToGoogleCalendar(context, task) }
                    )
                }
            }
        }
    }
}
