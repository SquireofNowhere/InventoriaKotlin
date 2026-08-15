package com.inventoria.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTypesScreen(
    viewModel: TaskTypesViewModel,
    onNavigateBack: () -> Unit
) {
    val taskTypes by viewModel.taskTypes.collectAsState()
    val stats by viewModel.stats.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<TaskType?>(null) }
    var deletingType by remember { mutableStateOf<TaskType?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Task Types", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Task Type")
            }
        }
    ) { padding ->
        if (taskTypes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No task types yet. Add one to start grouping your tasks by activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "A task type groups differently-named tasks under one activity. " +
                            "\"Eating with V\" and \"Eating out\" can share the type \"Eating\" " +
                            "while keeping their own kinds and points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(taskTypes, key = { it.id }) { type ->
                    TaskTypeRow(
                        taskType = type,
                        stats = stats[type.id],
                        onEdit = { editingType = type },
                        onDelete = { deletingType = type }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddDialog) {
        TaskTypeNameDialog(
            title = "New Task Type",
            initialName = "",
            existingNames = taskTypes.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addTaskType(name)
                showAddDialog = false
            }
        )
    }

    editingType?.let { type ->
        TaskTypeNameDialog(
            title = "Rename Task Type",
            initialName = type.name,
            existingNames = taskTypes.filter { it.id != type.id }.map { it.name },
            onDismiss = { editingType = null },
            onConfirm = { name ->
                viewModel.renameTaskType(type, name)
                editingType = null
            }
        )
    }

    deletingType?.let { type ->
        val affected = stats[type.id]?.taskCount ?: 0
        AlertDialog(
            onDismissRequest = { deletingType = null },
            title = { Text("Delete \"${type.name}\"?") },
            text = {
                Text(
                    if (affected > 0) {
                        // The tasks themselves survive -- only the grouping label is removed --
                        // so say so explicitly rather than letting a count imply data loss.
                        "${if (affected == 1) "1 task uses" else "$affected tasks use"} this type. " +
                            "They'll be kept, but will no longer have a type."
                    } else {
                        "This type isn't used by any tasks yet."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTaskType(type.id)
                    deletingType = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingType = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TaskTypeRow(
    taskType: TaskType,
    stats: TaskTypeStats?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val avg = stats?.averagePoints
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(taskType.name, fontWeight = FontWeight.Bold)
                Text(
                    text = buildString {
                        val count = stats?.taskCount ?: 0
                        append(if (count == 1) "1 task" else "$count tasks")
                        stats?.mostUsedKind?.let { append(" - mostly ${it.displayName}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stats?.averageLabel ?: "--",
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        avg == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        avg > 0 -> MaterialTheme.colorScheme.primary
                        avg < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    "avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TaskTypeNameDialog(
    title: String,
    initialName: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val isDuplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val canSave = trimmed.isNotBlank() && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Eating") },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text("A type with this name already exists") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
