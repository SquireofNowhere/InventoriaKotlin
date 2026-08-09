package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: TaskTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val activeSessions by viewModel.activeSessions.collectAsState()
    val completedSessions by viewModel.completedSessions.collectAsState()
    
    val task = remember(taskId, activeSessions, completedSessions) {
        activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) }.find { it.id == taskId }
            ?: completedSessions.flatten().find { it.id == taskId }
    }

    if (task == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Task not found")
        }
        return
    }

    var name by remember(task.name) { mutableStateOf(task.name) }
    var kind by remember(task.kind) { mutableStateOf(task.kind) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Task", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteSegment(task)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = {
                        viewModel.updateSegment(task.copy(name = name, kind = kind, isNameCustom = true, isKindCustom = true))
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Task Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Task Category", style = MaterialTheme.typography.titleSmall)
            TaskKindDropdownMenu(
                selectedKind = kind,
                onKindSelected = { kind = it }
            )

            HorizontalDivider()

            Text("Timing Info", style = MaterialTheme.typography.titleSmall)
            DetailItem("Started", formatDateTime(task.startTime))
            task.endTime?.let {
                DetailItem("Ended", formatDateTime(it))
            }
            DetailItem("Duration", formatDetailedDuration(task.duration))

            HorizontalDivider()

            Text("Point Calculation", style = MaterialTheme.typography.titleSmall)
            if (task.isRunning) {
                // Still running: no frozen score to show yet, so tick a live estimate off the
                // currently-selected Kind (previewScore hits the DB for the current streak, but
                // only once a second here, not per-frame -- fine for a single detail screen).
                val currentTime by rememberTick()
                val liveDuration = currentTime - task.startTime
                var livePreview by remember { mutableIntStateOf(0) }
                LaunchedEffect(liveDuration, kind) {
                    livePreview = viewModel.previewScore(kind, liveDuration)
                }
                DetailItem("Kind Value", (if (kind.productivityValue >= 0) "+" else "") + kind.productivityValue)
                DetailItem("Elapsed", formatDetailedDuration(liveDuration))
                Text(
                    text = "Running total: ${if (livePreview >= 0) "+" else ""}$livePreview pts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (livePreview >= 0) Success else Color(0xFFFF4D4D)
                )
                Text(
                    text = "Updates live while running, using your current momentum streak for this Kind.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Already frozen: show the ACTUAL stored score rather than recomputing (the streak
                // may have moved on since), and back out the momentum multiplier that must have
                // applied algebraically (score = round(kindValue * minutes * multiplier)) purely
                // for display, since the multiplier itself isn't separately stored.
                val minutes = task.duration / 60000.0
                val impliedMultiplier = if (task.kind.productivityValue != 0 && minutes > 0) {
                    task.score / (task.kind.productivityValue * minutes)
                } else 1.0
                DetailItem("Kind Value", (if (task.kind.productivityValue >= 0) "+" else "") + task.kind.productivityValue)
                DetailItem("Duration", formatDetailedDuration(task.duration))
                DetailItem("Momentum Multiplier", "${"%.2f".format(impliedMultiplier)}x")
                Text(
                    text = "Total: ${if (task.score >= 0) "+" else ""}${task.score} pts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (task.score >= 0) Success else Color(0xFFFF4D4D)
                )
            }
        }
    }
}
