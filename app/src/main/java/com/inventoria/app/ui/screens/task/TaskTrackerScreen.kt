package com.inventoria.app.ui.screens.task

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: TaskTrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeSessions by viewModel.activeSessions.collectAsState()
    val completedSessions by viewModel.completedSessions.collectAsState()
    val personalScore by viewModel.personalScore.collectAsState()
    val socialScore by viewModel.socialScore.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var sessionToShowDetail by remember { mutableStateOf<List<Task>?>(null) }
    var singleTaskToShowDetail by remember { mutableStateOf<Task?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.addNewTask()
        }
    }

    if (isLoading) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            ) {
                CircularProgressIndicator(color = PurplePrimary)
            }
        }
    }

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
                if (newState) addToGoogleCalendar(context, task)
            },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) }
        )
    }

    if (sessionToShowDetail != null) {
        val groupId = sessionToShowDetail!!.first().groupId
        val currentSession = (activeSessions.find { it.groupId == groupId }?.let { it.segments + (it.activeSegment?.task?.let { listOf(it) } ?: emptyList()) }
                             ?: completedSessions.find { it.first().groupId == groupId }) ?: sessionToShowDetail!!

        SessionDetailDialog(
            segments = currentSession,
            onDismiss = { sessionToShowDetail = null },
            onUpdateSessionName = { newName -> viewModel.updateSessionName(groupId, newName) },
            onUpdateSessionKind = { newKind -> viewModel.updateSessionKind(groupId, newKind) },
            onUpdateSegment = { updatedSegment -> viewModel.updateSegment(updatedSegment) },
            onToggleCalendar = { segment ->
                val newState = !segment.savedToCalendar
                viewModel.setSegmentCalendarStatus(segment, newState)
                if (newState) {
                    addToGoogleCalendar(context, segment)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    if (activeSessions.size < 5) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProductivityScoreCard(
                    personalScore = personalScore,
                    socialScore = socialScore
                )
            }

            if (activeSessions.isNotEmpty()) {
                item {
                    Text(
                        "Active Tracking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(activeSessions, key = { it.groupId }) { session ->
                    ActiveSessionCard(
                        session = session,
                        onStop = { viewModel.stopTask(session) },
                        onPauseResume = { viewModel.pauseResumeTask(session) },
                        onUpdateName = { newName -> viewModel.updateSessionName(session.groupId, newName) },
                        onKindChange = { newKind -> viewModel.updateSessionKind(session.groupId, newKind) },
                        onSessionClick = { sessionToShowDetail = session.segments + (session.activeSegment?.task?.let { listOf(it) } ?: emptyList()) }
                    )
                }
                
                item { Divider() }
            } else if (completedSessions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
            }

            if (completedSessions.isNotEmpty()) {
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(completedSessions) { sessionSegments ->
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
                                val newState = !task.savedToCalendar
                                viewModel.setSegmentCalendarStatus(task, newState)
                                if (newState) {
                                    addToGoogleCalendar(context, task)
                                }
                            },
                            onDelete = { viewModel.clearSegment(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleTaskItemCard(
    task: Task,
    onClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit
) {
    val taskColor = Color(task.kind.colorValue)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskKindChip(kind = task.kind)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Duration: ${formatDetailedDuration(task.duration)}",
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

@Composable
fun ActiveSessionCard(
    session: TaskSessionUI,
    onStop: () -> Unit,
    onPauseResume: () -> Unit,
    onUpdateName: (String) -> Unit,
    onKindChange: (TaskKind) -> Unit,
    onSessionClick: () -> Unit
) {
    val isExpanded by session.isExpanded.collectAsState()
    val activeSegment = session.activeSegment
    val focusManager = LocalFocusManager.current
    
    val activeElapsed by (activeSegment?.elapsedTime?.collectAsState() ?: remember { mutableStateOf(0L) })
    val prevTotal = session.segments.sumOf { it.duration }
    val totalTime = prevTotal + activeElapsed

    // Determine a stable session name: Use the first segment that is NOT custom-named
    val sessionName = (session.segments.find { !it.isNameCustom } ?: activeSegment?.task ?: session.segments.firstOrNull())?.name ?: "Untitled"
    var editableName by remember(sessionName) { mutableStateOf(sessionName) }
    
    val refTask = activeSegment?.task ?: session.segments.firstOrNull() ?: return
    val taskColor = Color(refTask.kind.colorValue)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSessionClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.2f))
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
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
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
                            onValueChange = { 
                                editableName = it
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { 
                                    if (!it.isFocused && editableName != sessionName) {
                                        onUpdateName(editableName)
                                    }
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    
                    TaskKindDropdownMenu(
                        selectedKind = refTask.kind,
                        onKindSelected = onKindChange
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(totalTime),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (taskColor == Color.White) Color.Black else taskColor
                    )
                    
                    if (activeSegment == null) {
                        Text(
                            text = "PAUSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Gray
                        )
                    }
                    
                    Row {
                        IconButton(onClick = onPauseResume) {
                            Icon(
                                imageVector = if (activeSegment != null) Icons.Default.Pause else Icons.Default.PlayArrow,
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
                        .padding(top = 8.dp, start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activeSegment != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(taskColor))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Current: ${activeSegment.task.name} - ${formatDetailedDuration(activeElapsed)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                        Divider(modifier = Modifier.padding(bottom = 4.dp), thickness = 0.5.dp)
                    }

                    if (session.segments.isNotEmpty()) {
                        Text(
                            "Previous segments:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        session.segments.sortedBy { it.startTime }.forEach { segment ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(segment.kind.colorValue)))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${segment.name} - ${formatDetailedDuration(segment.duration)}",
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
    var expanded by remember { mutableStateOf(false) }
    
    // Find majority segment type
    val majorityKind = segments.groupBy { it.kind }
        .maxByOrNull { it.value.size }?.key ?: segments.first().kind
    
    val refTask = segments.first()
    
    // Stable title: Use the first non-customized segment as the session title
    val sessionName = (segments.find { !it.isNameCustom } ?: segments.firstOrNull())?.name ?: "Untitled Session"
    
    val totalDuration = segments.sumOf { it.duration }
    val taskColor = Color(majorityKind.colorValue)

    val allSaved = segments.all { it.savedToCalendar }
    val someSaved = segments.any { it.savedToCalendar }
    
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(allSaved) {
        if (allSaved) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.05f))
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
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${segments.size} segment(s) • Total: ${formatDetailedDuration(totalDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    if (allSaved) {
                        val latestSaveAt = segments.maxOf { it.savedToCalendarAt ?: 0L }
                        val remaining = (24 * 60 * 60 * 1000L) - (currentTime - latestSaveAt)
                        if (remaining > 0) {
                            Text(
                                text = "Auto-delete session in: ${formatDetailedDuration(remaining)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (allSaved) {
                    Icon(Icons.Default.EventAvailable, "All Saved", tint = Success, modifier = Modifier.size(20.dp))
                } else if (someSaved) {
                    Icon(Icons.Default.CalendarMonth, "Partially Saved", tint = PurplePrimary, modifier = Modifier.size(20.dp))
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    segments.sortedBy { it.startTime }.forEach { segment ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TaskKindChip(kind = segment.kind)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = segment.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (segment.isNameCustom) FontWeight.Bold else FontWeight.Normal,
                                    color = if (segment.isNameCustom) PurplePrimary else Color.Unspecified
                                )
                                Text(
                                    text = formatDetailedDuration(segment.duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            if (segment.savedToCalendar) {
                                Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp))
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
    onToggleCalendar: (Task) -> Unit
) {
    // Determine the reference session values (the first segment that isn't custom)
    val sessionRef = segments.find { !it.isNameCustom && !it.isKindCustom } ?: segments.firstOrNull() ?: return
    var sessionNameInput by remember(sessionRef.name) { mutableStateOf(sessionRef.name) }
    val focusManager = LocalFocusManager.current
    
    val segmentsWithEditors = remember { mutableStateListOf<String>() }

    // Live update for running tasks in details
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasRunningTask = segments.any { it.isRunning }
    
    LaunchedEffect(hasRunningTask) {
        if (hasRunningTask) {
            while (true) {
                ticker = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Global Session Settings - Always visible at the top
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Global Session Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { 
                            sessionNameInput = it
                        },
                        label = { Text("Session Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { 
                                if (!it.isFocused && sessionNameInput != sessionRef.name) {
                                    onUpdateSessionName(sessionNameInput)
                                }
                            },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Session Type:", style = MaterialTheme.typography.bodySmall)
                        TaskKindDropdownMenu(
                            selectedKind = sessionRef.kind,
                            onKindSelected = { onUpdateSessionKind(it) }
                        )
                    }
                    Text(
                        "Note: These values push to all linked segments.",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = Color.Gray
                    )
                }
                
                Divider()

                Text(
                    "Segments", 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    segments.sortedBy { it.startTime }.forEach { segment ->
                        val isEditing = segmentsWithEditors.contains(segment.id)
                        val isCustom = segment.isNameCustom || segment.isKindCustom
                        var localSegmentName by remember(segment.id, segment.name) { mutableStateOf(segment.name) }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCustom) PurplePrimary.copy(alpha = 0.03f) else Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isCustom) PurplePrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                // Sync/Link Indicator (Left-most) - Also toggles editor
                                IconButton(
                                    onClick = { 
                                        if (isEditing) segmentsWithEditors.remove(segment.id) 
                                        else segmentsWithEditors.add(segment.id)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCustom) Icons.Default.LinkOff else Icons.Default.Link,
                                        contentDescription = if (isCustom) "Segment has overrides" else "Segment is linked to session",
                                        tint = if (isCustom) PurplePrimary else Color.Gray.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(
                                        text = segment.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TaskKindChip(kind = segment.kind, modifier = Modifier.scale(0.7f).offset(x = (-10).dp))
                                        val segmentDuration = if (segment.isRunning) (ticker - segment.startTime) else segment.duration
                                        Text(
                                            text = " • ${if (segment.isRunning) "Running: ${formatTime(segmentDuration)}" else formatStartEndRange(segment.startTime, segment.endTime)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (segment.isRunning) PurplePrimary else Color.Gray
                                        )
                                    }
                                }
                                
                                // Calendar Icon (The right-most icon)
                                IconButton(onClick = { onToggleCalendar(segment) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = if (segment.savedToCalendar) Success else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isEditing) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = localSegmentName,
                                        onValueChange = { localSegmentName = it },
                                        label = { Text("Segment Name Override") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { 
                                                if (!it.isFocused && localSegmentName != segment.name) {
                                                    onUpdateSegment(segment.copy(name = localSegmentName, isNameCustom = true))
                                                }
                                            },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        trailingIcon = {
                                            if (segment.isNameCustom) {
                                                IconButton(onClick = { 
                                                    localSegmentName = sessionRef.name
                                                    onUpdateSegment(segment.copy(name = sessionRef.name, isNameCustom = false)) 
                                                }) {
                                                    Icon(Icons.Default.RestartAlt, "Reset name", tint = Color.Gray)
                                                }
                                            }
                                        }
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Segment Type:", style = MaterialTheme.typography.labelMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (segment.isKindCustom) {
                                                IconButton(
                                                    onClick = { onUpdateSegment(segment.copy(kind = sessionRef.kind, isKindCustom = false)) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.RestartAlt, "Reset kind", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            TaskKindDropdownMenu(
                                                selectedKind = segment.kind,
                                                onKindSelected = { onUpdateSegment(segment.copy(kind = it, isKindCustom = true)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Divider()
                
                val totalDuration = segments.sumOf { 
                    if (it.isRunning) (ticker - it.startTime) else it.duration 
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Time:", fontWeight = FontWeight.Bold)
                    Text(formatDetailedDuration(totalDuration), fontWeight = FontWeight.Bold, color = PurplePrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// Extension to allow scaling of chips
@Composable
fun Modifier.scale(scale: Float): Modifier = this.then(Modifier.graphicsLayer(scaleX = scale, scaleY = scale))

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

    LaunchedEffect(key1 = task.id, key2 = task.savedToCalendar, key3 = task.isRunning) {
        while (task.savedToCalendar || task.isRunning) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    fun showDateTimePicker(initialTime: Long, onTimeSelected: (Long) -> Unit) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && name != task.name) {
                                onSaveName(name)
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                
                TaskKindDropdownMenu(
                    selectedKind = task.kind,
                    onKindSelected = onKindChange
                )
                
                Divider()
                
                val currentEndTime = task.endTime ?: currentTime
                if (isSpanningDays(task.startTime, currentEndTime)) {
                    DetailItem(label = "Date", value = formatDateRange(task.startTime, currentEndTime))
                }
                
                EditableDetailItem(
                    label = "Started", 
                    value = formatDateTime(task.startTime),
                    onEdit = { 
                        showDateTimePicker(task.startTime) { newStart ->
                            onUpdateTime(newStart, task.endTime ?: System.currentTimeMillis())
                        }
                    }
                )
                
                EditableDetailItem(
                    label = "Stopped", 
                    value = if (task.endTime != null) formatDateTime(task.endTime!!) else "Running...",
                    onEdit = {
                        if (task.endTime != null) {
                            showDateTimePicker(task.endTime!!) { newEnd ->
                                onUpdateTime(task.startTime, newEnd)
                            }
                        }
                    }
                )
                
                val liveDuration = if (task.isRunning) (currentTime - task.startTime) else task.duration
                DetailItem(label = "Duration", value = formatDetailedDuration(liveDuration))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.savedToCalendar,
                        onCheckedChange = onToggleCalendar
                    )
                    Text("Saved to Calendar")
                }
                
                if (task.savedToCalendar && task.savedToCalendarAt != null) {
                    val remaining = (24 * 60 * 60 * 1000L) - (currentTime - task.savedToCalendarAt!!)
                    if (remaining > 0) {
                        Text(
                            text = "Auto-delete in: ${formatDetailedDuration(remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                if (name != task.name) onSaveName(name)
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
fun EditableDetailItem(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Edit, 
                contentDescription = "Edit $label", 
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(4.dp))
            Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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

fun addToGoogleCalendar(context: Context, task: Task) {
    val durationStr = formatDetailedDuration(task.duration)
    
    val googleColorId = when(task.kind) {
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

    val description = "Type: ${task.kind.displayName}\nDuration: $durationStr\nTracked via Inventoria Task Tracker"

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
