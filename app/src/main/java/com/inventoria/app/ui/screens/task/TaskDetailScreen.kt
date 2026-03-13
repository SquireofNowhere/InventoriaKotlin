package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind

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
        }
    }
}
