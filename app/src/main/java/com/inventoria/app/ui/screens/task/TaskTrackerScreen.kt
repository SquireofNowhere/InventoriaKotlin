package com.inventoria.app.ui.screens.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackerScreen(
    viewModel: TaskTrackerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val activeSessions by viewModel.activeSessions.collectAsState()
    val completedSessions by viewModel.completedSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedSessionForDetail by remember { mutableStateOf<List<Task>?>(null) }
    var selectedTaskForDetail by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Task Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.BarChart, contentDescription = "Stats")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeSessions.size < 5) {
                FloatingActionButton(
                    onClick = { viewModel.addNewTask() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Task")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeSessions.isNotEmpty()) {
                    item {
                        Text(
                            "Active Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(activeSessions) { session ->
                        ActiveSessionCard(
                            session = session,
                            onStop = { viewModel.stopTask(session) },
                            onPauseResume = { viewModel.pauseResumeTask(session) },
                            onUpdateName = { name -> viewModel.updateSessionName(session.groupId, name) },
                            onUpdateKind = { kind -> viewModel.updateSessionKind(session.groupId, kind) },
                            onSessionClick = {
                                selectedSessionForDetail = session.segments + listOfNotNull(session.activeSegment?.task)
                            }
                        )
                    }
                }

                if (completedSessions.isNotEmpty()) {
                    item {
                        Text(
                            "Recent Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    
                    val recentSessions = completedSessions.filter { session ->
                        val latestTask = session.maxByOrNull { it.startTime }
                        latestTask != null && System.currentTimeMillis() - latestTask.startTime < 86400000
                    }

                    if (recentSessions.isEmpty()) {
                        item {
                            Text(
                                "No sessions in the last 24 hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(recentSessions) { session ->
                            if (session.size > 1) {
                                CompletedSessionCard(
                                    segments = session,
                                    onClick = { selectedSessionForDetail = session },
                                    onDelete = { viewModel.clearSession(session.first().groupId) }
                                )
                            } else {
                                val task = session.first()
                                SingleTaskItemCard(
                                    task = task,
                                    onClick = { selectedTaskForDetail = task },
                                    onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                    onDelete = { viewModel.clearSegment(task) },
                                    onAddToCalendar = { addToGoogleCalendar(context, task) }
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    selectedSessionForDetail?.let { segments ->
        SessionDetailDialog(
            segments = segments,
            onDismiss = { selectedSessionForDetail = null },
            onUpdateSessionName = { name -> viewModel.updateSessionName(segments.first().groupId, name) },
            onUpdateSessionKind = { kind -> viewModel.updateSessionKind(segments.first().groupId, kind) },
            onUpdateSegment = { viewModel.updateSegment(it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) }
        )
    }

    selectedTaskForDetail?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTaskForDetail = null },
            onSaveName = { viewModel.updateCompletedTaskName(task, it) },
            onKindChange = { viewModel.updateCompletedTaskKind(task, it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, it) },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) }
        )
    }
}

@Composable
fun SingleTaskItemCard(
    task: Task,
    onClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit
) {
    val context = LocalContext.current
    val taskColor = Color(task.kind.colorValue)
    val isCalendarTask = task.id.startsWith("cal_")
    val percentage = calculatePercentageOfDay(task.duration, task.startTime)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = taskColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskKindChip(kind = task.kind)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Duration: ${formatDetailedDuration(task.duration)} • $percentage",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (isCalendarTask) {
                IconButton(onClick = { openInSystemCalendar(context, task) }) {
                    Icon(
                        Icons.Default.EventAvailable,
                        contentDescription = "Open in Calendar",
                        tint = Success
                    )
                }
            } else {
                IconButton(onClick = {
                    if (!task.savedToCalendar) {
                        onAddToCalendar()
                    }
                    onToggleCalendar()
                }) {
                    Icon(
                        if (task.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                        contentDescription = if (task.savedToCalendar) "Remove from Calendar list" else "Add to Calendar",
                        tint = if (task.savedToCalendar) Success else PurplePrimary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ActiveSessionCard(
    session: TaskSessionUI,
    onStop: () -> Unit,
    onPauseResume: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateKind: (TaskKind) -> Unit,
    onSessionClick: () -> Unit
) {
    val isExpanded by session.isExpanded.collectAsState()
    val activeSegment = session.activeSegment
    val focusManager = LocalFocusManager.current
    
    val activeElapsed by (activeSegment?.elapsedTime?.collectAsState() ?: remember { mutableStateOf(0L) })
    
    val refTask = activeSegment?.task ?: session.segments.firstOrNull() ?: return
    
    val percentage = calculateSessionPercentage(
        session.segments,
        activeElapsed,
        activeSegment?.task
    )
    
    val totalTime = session.segments.sumOf { it.duration } + activeElapsed
    
    val sessionName = session.segments.firstOrNull { !it.isNameCustom }?.name 
        ?: activeSegment?.task?.name 
        ?: session.segments.firstOrNull()?.name 
        ?: "Untitled"
        
    var editableName by remember(sessionName) { mutableStateOf(sessionName) }
    val taskColor = Color(refTask.kind.colorValue)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSessionClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = taskColor.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { session.isExpanded.value = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Show Segments",
                        tint = Color.Gray.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BasicTextField(
                            value = editableName,
                            onValueChange = { editableName = it },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { 
                                    if (!it.isFocused && editableName != sessionName) {
                                        onUpdateName(editableName)
                                    }
                                },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    TaskKindDropdownMenu(
                        selectedKind = refTask.kind,
                        onKindSelected = onUpdateKind
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(totalTime),
                            color = if (taskColor == Color.White) Color.Black else taskColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = percentage,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    if (activeSegment == null) {
                        Text(
                            text = "PAUSED",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Row {
                        IconButton(onClick = onPauseResume) {
                            Icon(
                                if (activeSegment != null) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (activeSegment != null) "Pause" else "Resume",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activeSegment != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(taskColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            val segPercentage = calculatePercentageOfDay(activeElapsed, activeSegment.task.startTime)
                            Text(
                                text = "Current: ${activeSegment.task.name} - ${formatDetailedDuration(activeElapsed)} • $segPercentage",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp), thickness = 0.5.dp)
                    }
                    
                    if (session.segments.isNotEmpty()) {
                        Text(
                            text = "Previous segments:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        session.segments.sortedByDescending { it.startTime }.forEach { segment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(segment.kind.colorValue))
                                )
                                Spacer(Modifier.width(8.dp))
                                val segPercentage = calculatePercentageOfDay(segment.duration, segment.startTime)
                                Text(
                                    text = "${segment.name} - ${formatDetailedDuration(segment.duration)} • $segPercentage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedSessionCard(
    segments: List<Task>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    
    val majorityKind = segments.groupBy { it.kind }
        .maxByOrNull { it.value.size }?.key ?: segments.first().kind
        
    val sessionName = segments.firstOrNull { !it.isNameCustom }?.name 
        ?: segments.firstOrNull()?.name 
        ?: "Untitled Session"
        
    val totalDuration = segments.sumOf { it.duration }
    val taskColor = Color(majorityKind.colorValue)
    val percentage = calculateSessionPercentage(segments)
    
    val allSaved = segments.all { it.savedToCalendar }
    val someSaved = segments.any { it.savedToCalendar }
    val isCalendarSession = segments.any { it.id.startsWith("cal_") }
    
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(allSaved) {
        while (allSaved) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = taskColor.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TaskKindChip(kind = majorityKind, modifier = Modifier.scale(0.8f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = sessionName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${segments.size} segment(s) • Total: ${formatDetailedDuration(totalDuration)} • $percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    if (allSaved && !isCalendarSession) {
                        val latestSaveAt = segments.mapNotNull { it.savedToCalendarAt }.maxOrNull() ?: 0L
                        val remaining = 86400000 - (currentTime - latestSaveAt)
                        if (remaining > 0) {
                            Text(
                                text = "Auto-delete session in: ${formatDetailedDuration(remaining)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                if (isCalendarSession) {
                    IconButton(onClick = { openInSystemCalendar(context, segments.first()) }) {
                        Icon(Icons.Default.EventAvailable, contentDescription = "Open in Calendar", tint = Success)
                    }
                } else {
                    if (allSaved) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = "All Saved",
                            modifier = Modifier.size(20.dp),
                            tint = Success
                        )
                    } else if (someSaved) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Partially Saved",
                            modifier = Modifier.size(20.dp),
                            tint = PurplePrimary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    segments.sortedByDescending { it.startTime }.forEach { segment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(segment.kind.colorValue))
                            )
                            Spacer(Modifier.width(8.dp))
                            val segPercentage = calculatePercentageOfDay(segment.duration, segment.startTime)
                            Text(
                                text = "${segment.name} - ${formatDetailedDuration(segment.duration)} • $segPercentage",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(Modifier.weight(1f))
                            if (segment.savedToCalendar) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Success)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailDialog(
    segments: List<Task>,
    onDismiss: () -> Unit,
    onUpdateSessionName: (String) -> Unit,
    onUpdateSessionKind: (TaskKind) -> Unit,
    onUpdateSegment: (Task) -> Unit,
    onToggleCalendar: (Task) -> Unit,
    onFlatten: () -> Unit
) {
    val context = LocalContext.current
    val sessionRef = segments.first()
    var sessionNameInput by remember { mutableStateOf(sessionRef.name) }
    val focusManager = LocalFocusManager.current
    
    var showFlattenConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Session Details")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sessionNameInput,
                    onValueChange = { sessionNameInput = it },
                    label = { Text("Session Name") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { 
                        if (sessionNameInput != sessionRef.name) {
                            onUpdateSessionName(sessionNameInput)
                        }
                        focusManager.clearFocus()
                    })
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Session Category: ", style = MaterialTheme.typography.bodySmall)
                    TaskKindDropdownMenu(
                        selectedKind = sessionRef.kind,
                        onKindSelected = onUpdateSessionKind
                    )
                }
                
                HorizontalDivider()
                
                Text("Segments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                segments.sortedByDescending { it.startTime }.forEach { segment ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(segment.kind.colorValue)))
                            Spacer(Modifier.width(8.dp))
                            var localSegmentName by remember(segment.name) { mutableStateOf(segment.name) }
                            BasicTextField(
                                value = localSegmentName,
                                onValueChange = { localSegmentName = it },
                                modifier = Modifier.weight(1f).onFocusChanged { 
                                    if (!it.isFocused && localSegmentName != segment.name) {
                                        onUpdateSegment(segment.copy(name = localSegmentName, isNameCustom = true))
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { onToggleCalendar(segment) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (segment.savedToCalendar) Success else PurplePrimary
                                )
                            }
                        }
                        Text(
                            text = "${formatDetailedDuration(segment.duration)} • ${formatStartEndRange(segment.startTime, segment.endTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                
                if (segments.size > 1) {
                    TextButton(
                        onClick = { showFlattenConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Flatten into one segment")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sessionNameInput != sessionRef.name) {
                    onUpdateSessionName(sessionNameInput)
                }
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
    
    if (showFlattenConfirm) {
        AlertDialog(
            onDismissRequest = { showFlattenConfirm = false },
            title = { Text("Flatten Session?") },
            text = { Text("This will merge all segments into a single continuous task. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onFlatten()
                        showFlattenConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Flatten")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFlattenConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TaskDetailDialog(
    task: Task,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onKindChange: (TaskKind) -> Unit,
    onToggleCalendar: (Boolean) -> Unit,
    onUpdateTime: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var name by remember(task.name) { mutableStateOf(task.name) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val focusManager = LocalFocusManager.current
    val isCalendarTask = task.id.startsWith("cal_")

    LaunchedEffect(task.id, task.savedToCalendar, task.isRunning) {
        while (task.savedToCalendar || task.isRunning) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (name != task.name && !isCalendarTask) {
                    onSaveName(name)
                }
                onDismiss()
            }) {
                Text("Done")
            }
        },
        title = { Text("Task Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && name != task.name && !isCalendarTask) {
                                onSaveName(name)
                            }
                        },
                    enabled = !isCalendarTask,
                    label = { Text("Task Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                
                if (isCalendarTask) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type: ", style = MaterialTheme.typography.bodySmall)
                        TaskKindChip(kind = task.kind)
                    }
                } else {
                    TaskKindDropdownMenu(
                        selectedKind = task.kind,
                        onKindSelected = onKindChange
                    )
                }
                
                HorizontalDivider()
                
                val currentEndTime = task.endTime ?: currentTime
                
                if (isSpanningDays(task.startTime, currentEndTime)) {
                    DetailItem("Date", formatDateRange(task.startTime, currentEndTime))
                }
                
                if (isCalendarTask) {
                    DetailItem("Started", formatDateTime(task.startTime))
                    DetailItem("Stopped", formatDateTime(currentEndTime))
                } else {
                    EditableDetailItem("Started", formatDateTime(task.startTime)) {
                        showDateTimePicker(context, task.startTime) { newStart ->
                            onUpdateTime(newStart, task.endTime ?: System.currentTimeMillis())
                        }
                    }
                    val stoppedText = if (task.endTime != null) formatDateTime(task.endTime!!) else "Running..."
                    EditableDetailItem("Stopped", stoppedText) {
                        if (task.endTime != null) {
                            showDateTimePicker(context, task.endTime!!) { newEnd ->
                                onUpdateTime(task.startTime, newEnd)
                            }
                        }
                    }
                }
                
                val liveDuration = if (task.isRunning) currentTime - task.startTime else task.duration
                DetailItem("Duration", formatDetailedDuration(liveDuration))
                
                if (!isCalendarTask) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = task.savedToCalendar, onCheckedChange = onToggleCalendar)
                        Text("Auto-delete task in 24 hours", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (!task.savedToCalendar) {
                        Button(
                            onClick = { addToGoogleCalendar(context, task) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add to Google Calendar")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openInSystemCalendar(context, task) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF4285F4)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Loaded from your device calendar. Tap to view.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4285F4)
                        )
                    }
                }
                
                if (task.savedToCalendar && task.savedToCalendarAt != null && !isCalendarTask) {
                    val remaining = 86400000 - (currentTime - task.savedToCalendarAt!!)
                    if (remaining > 0) {
                        Text(
                            text = "Auto-delete in: ${formatDetailedDuration(remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EditableDetailItem(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
    if (days > 0) parts.add("${days} days")
    if (hours > 0) parts.add("${hours} hours")
    if (minutes > 0) parts.add("${minutes} min")
    if (seconds > 0 || parts.isEmpty()) parts.add("${seconds} sec")
    
    return parts.joinToString(" ")
}

fun formatDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val isSameWeek = now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR) &&
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
            
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

fun formatStartEndRange(start: Long, end: Long?): String {
    if (end == null) return "Started: ${formatDateTime(start)}"
    
    val startCal = Calendar.getInstance().apply { timeInMillis = start }
    val endCal = Calendar.getInstance().apply { timeInMillis = end }
    val isSameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
            startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
            
    val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return if (isSameDay) {
        "${formatDate(start)} ${timeSdf.format(Date(start))} - ${timeSdf.format(Date(end))}"
    } else {
        "${formatDateTime(start)} - ${formatDateTime(end)}"
    }
}

fun formatCardDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
        return ""
    }
    
    val threeDaysAgo = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -3)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    return if (timestamp >= threeDaysAgo) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun getStartOfDay(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun getDayLabel(timestamp: Long): String {
    val dateInfo = formatCardDate(timestamp)
    if (dateInfo.isEmpty()) return "Today"
    
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            
    return if (isYesterday) "Yesterday" else dateInfo
}

fun calculatePercentageOfDay(taskDuration: Long, timestamp: Long): String {
    val percentage = (taskDuration.toDouble() / 86400000.0) * 100.0
    val dayLabel = getDayLabel(timestamp)
    return String.format("%.1f%% of %s", percentage, dayLabel)
}

fun calculateSessionPercentage(
    segments: List<Task>,
    activeDuration: Long = 0L,
    activeTask: Task? = null
): String {
    val dayMillis = 86400000L
    val dayDurations = mutableMapOf<Long, Long>()
    
    segments.forEach { segment ->
        val dayStart = getStartOfDay(segment.startTime)
        dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + segment.duration
    }
    
    if (activeTask != null) {
        val dayStart = getStartOfDay(activeTask.startTime)
        dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + activeDuration
    }
    
    if (dayDurations.isEmpty()) return "0.0% of Today"
    
    return dayDurations.entries.sortedByDescending { it.key }.joinToString(" - ") { (dayStart, duration) ->
        val percentage = (duration.toDouble() / dayMillis.toDouble()) * 100.0
        val dayLabel = getDayLabel(dayStart)
        String.format("%.1f%% of %s", percentage, dayLabel)
    }
}

fun showDateTimePicker(context: Context, initialTime: Long, onTimeSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }
    
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val result = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute)
                    }
                    onTimeSelected(result.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun addToGoogleCalendar(context: Context, task: Task) {
    val googleColorId = when (task.kind) {
        TaskKind.TOMATO -> 11
        TaskKind.TANGERINE -> 6
        TaskKind.GRAPHITE -> 8
        TaskKind.GRAPE -> 3
        TaskKind.BLUEBERRY -> 9
        TaskKind.LAVENDER -> 1
        TaskKind.PEACOCK -> 7
        TaskKind.BANANA -> 5
        TaskKind.FLAMINGO -> 4
        TaskKind.BASIL -> 10
        TaskKind.SAGE -> 2
    }
    
    val description = "Type: ${task.kind.displayName}\n" +
            "Duration: ${formatDetailedDuration(task.duration)}\n" +
            "Tracked via Inventoria Task Tracker\n" +
            "Task ID: ${task.id}\n" +
            "Session ID: ${task.groupId}"
            
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, task.name)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, task.startTime)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, task.endTime ?: (task.startTime + task.duration))
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        .putExtra("eventColorId", googleColorId.toString())
        
    context.startActivity(intent)
}

fun openInSystemCalendar(context: Context, task: Task) {
    if (task.id.startsWith("cal_")) {
        val eventIdStr = task.id.removePrefix("cal_")
        val eventId = eventIdStr.toLongOrNull()
        if (eventId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e("TaskTracker", "Could not open specific event $eventId", e)
            }
        }
    }
    
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse("content://com.android.calendar/time/${task.startTime}"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("TaskTracker", "Could not open calendar view", e)
    }
}
