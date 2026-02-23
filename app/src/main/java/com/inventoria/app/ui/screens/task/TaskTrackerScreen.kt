package com.inventoria.app.ui.screens.task

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackerScreen(
    viewModel: TaskTrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val completedTasks by viewModel.completedTasks.collectAsState()
    var taskToShowDetail by remember { mutableStateOf<TrackedTask?>(null) }

    // Permission launcher for POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.addNewTask()
        }
    }

    if (taskToShowDetail != null) {
        // Find the most up-to-date version of the task from the list
        val currentTask = completedTasks.find { it.id == taskToShowDetail?.id } ?: taskToShowDetail!!
        
        TaskDetailDialog(
            task = currentTask,
            onDismiss = { taskToShowDetail = null },
            onSaveName = { newName ->
                viewModel.updateCompletedTaskName(currentTask, newName)
            },
            onKindChange = { newKind ->
                viewModel.updateCompletedTaskKind(currentTask, newKind)
            },
            onToggleCalendar = { isChecked ->
                viewModel.setCompletedTaskCalendarStatus(currentTask, isChecked)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    if (viewModel.runningTasks.size < 5) {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.addNewTask()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.addNewTask()
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("New Task")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Running Timers Section
            if (viewModel.runningTasks.isNotEmpty()) {
                Text(
                    "Running Timers",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                viewModel.runningTasks.forEach { task ->
                    RunningTaskCard(
                        task = task,
                        onStop = { viewModel.stopTask(task) },
                        onUpdateName = { newName -> viewModel.updateTaskName(task, newName) },
                        onKindChange = { newKind -> viewModel.updateTaskKind(task, newKind) }
                    )
                }
                
                Divider()
            } else if (completedTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Timer, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No active tasks. Tap 'New Task' to start.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Completed Tasks Section
            if (completedTasks.isNotEmpty()) {
                Text(
                    "Recently Completed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(completedTasks) { task ->
                        TaskItemCard(
                            task = task,
                            onClick = { taskToShowDetail = task },
                            onToggleCalendar = { 
                                val newState = !task.savedToCalendar
                                viewModel.setCompletedTaskCalendarStatus(task, newState)
                                if (newState) {
                                    addToGoogleCalendar(context, task)
                                }
                            },
                            onDelete = { viewModel.clearCompletedTask(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskDetailDialog(
    task: TrackedTask,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onKindChange: (TaskKind) -> Unit,
    onToggleCalendar: (Boolean) -> Unit
) {
    var name by remember { mutableStateOf(task.name) }
    var showKindMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Box {
                    OutlinedButton(
                        onClick = { showKindMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(task.kind.color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(task.kind.displayName)
                    }
                    
                    DropdownMenu(
                        expanded = showKindMenu,
                        onDismissRequest = { showKindMenu = false }
                    ) {
                        TaskKind.values().forEach { kind ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(kind.color)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(kind.displayName)
                                    }
                                },
                                onClick = {
                                    onKindChange(kind)
                                    showKindMenu = false
                                }
                            )
                        }
                    }
                }
                
                Divider()
                
                if (isSpanningDays(task.startTime, task.endTime)) {
                    DetailItem(label = "Date", value = formatDateRange(task.startTime, task.endTime))
                }
                
                DetailItem(label = "Started", value = formatDateTime(task.startTime))
                DetailItem(label = "Stopped", value = formatDateTime(task.endTime))
                DetailItem(label = "Duration", value = formatDetailedDuration(task.duration))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.savedToCalendar,
                        onCheckedChange = onToggleCalendar
                    )
                    Text("Saved to Calendar")
                }
                
                if (task.savedToCalendar) {
                    Text(
                        "Note: Saved tasks will be cleared on app close.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onSaveName(name)
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RunningTaskCard(
    task: RunningTask,
    onStop: () -> Unit,
    onUpdateName: (String) -> Unit,
    onKindChange: (TaskKind) -> Unit
) {
    val elapsedTime by task.elapsedTime.collectAsState()
    var isEditing by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf(task.name) }
    var showKindMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = task.kind.color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        TextField(
                            value = currentName,
                            onValueChange = { currentName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onUpdateName(currentName)
                                    isEditing = false 
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = Success)
                                }
                            }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showKindMenu = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(task.kind.color)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = task.kind.displayName,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                        }
                        
                        DropdownMenu(
                            expanded = showKindMenu,
                            onDismissRequest = { showKindMenu = false }
                        ) {
                            TaskKind.values().forEach { kind ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(kind.color)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(kind.displayName)
                                        }
                                    },
                                    onClick = {
                                        onKindChange(kind)
                                        showKindMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = formatTime(elapsedTime),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.kind.color == Color.White) Color.Black else task.kind.color
                )
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: TrackedTask,
    onClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = task.kind.color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(task.kind.color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${task.kind.displayName} • Duration: ${formatTime(task.duration)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(
                onClick = onToggleCalendar
            ) {
                Icon(
                    imageVector = if (task.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday, 
                    contentDescription = if (task.savedToCalendar) "Saved to Calendar" else "Add to Calendar", 
                    tint = if (task.savedToCalendar) Success else PurplePrimary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

fun formatDetailedDuration(milliseconds: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
    val hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("$days days")
    if (hours > 0) parts.add("$hours hours")
    if (minutes > 0) parts.add("$minutes min")
    if (seconds > 0 || parts.isEmpty()) parts.add("$seconds sec")
    
    return parts.joinToString(" ")
}

fun formatDate(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val targetCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameWeek = calendar.get(Calendar.WEEK_OF_YEAR) == targetCalendar.get(Calendar.WEEK_OF_YEAR) &&
            calendar.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR)

    return if (isSameWeek) {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        "[${sdf.format(Date(timestamp))}]"
    } else {
        val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.getDefault())
        "[${sdf.format(Date(timestamp)).lowercase()}]"
    }
}

fun formatSimpleDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDateRange(start: Long, end: Long): String {
    return "${formatSimpleDate(start)} - ${formatSimpleDate(end)}"
}

fun isSpanningDays(start: Long, end: Long): Boolean {
    val startCal = Calendar.getInstance().apply { timeInMillis = start }
    val endCal = Calendar.getInstance().apply { timeInMillis = end }
    return startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) ||
            startCal.get(Calendar.DAY_OF_YEAR) != endCal.get(Calendar.DAY_OF_YEAR)
}

fun formatDateTime(timestamp: Long): String {
    val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return "${formatDate(timestamp)} ${timeSdf.format(Date(timestamp))}"
}

fun addToGoogleCalendar(context: Context, task: TrackedTask) {
    val durationStr = formatDetailedDuration(task.duration)
    
    // Google Calendar Event Colors mapping
    val (googleColorId, googleColorName) = when(task.kind) {
        TaskKind.FREE_TIME -> 8 to "Default colour"
        TaskKind.BIG_WASTE -> 11 to "Tomato"
        TaskKind.SMALL_WASTE -> 4 to "Flamingo"
        TaskKind.NEUTRAL_WAITING -> 8 to "Graphite"
        TaskKind.SMALL_PRODUCTIVE -> 2 to "Sage"
        TaskKind.BIG_PRODUCTIVE -> 10 to "Basil"
    }

    val description = "Type: ${task.kind.displayName} ($googleColorName)\nDuration: $durationStr\nTracked via Inventoria Task Tracker"

    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, task.name)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, task.startTime)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, task.endTime)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        // Keep attempting to set color via extras
        .putExtra("eventColorId", googleColorId.toString())
        .putExtra("colorId", googleColorId.toString())
        .putExtra("eventColor", task.kind.color.toArgb())
        .putExtra(CalendarContract.Events.EVENT_COLOR, task.kind.color.toArgb())
    
    context.startActivity(intent)
}
