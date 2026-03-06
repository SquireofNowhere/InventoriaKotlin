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
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.scale
import com.inventoria.app.R
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
    
    val personalScore by viewModel.personalScore.collectAsState()
    val socialScore by viewModel.socialScore.collectAsState()
    val scoreBreakdown by viewModel.scoreBreakdown.collectAsState()
    
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    val selectedSessionSegments = remember(selectedGroupId, activeSessions, completedSessions) {
        val groupId = selectedGroupId ?: return@remember null
        val active = activeSessions.find { it.groupId == groupId }
        if (active != null) {
            return@remember (active.segments + listOfNotNull(active.activeSegment?.task)).sortedByDescending { it.startTime }
        }
        completedSessions.find { it.firstOrNull()?.groupId == groupId }
    }

    val selectedTask = remember(selectedTaskId, activeSessions, completedSessions) {
        val taskId = selectedTaskId ?: return@remember null
        activeSessions.forEach { session ->
            if (session.activeSegment?.task?.id == taskId) return@remember session.activeSegment.task
            val seg = session.segments.find { it.id == taskId }
            if (seg != null) return@remember seg
        }
        completedSessions.flatten().find { it.id == taskId }
    }

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
                // Productivity Card
                item {
                    ProductivityScoreCard(
                        personalScore = personalScore,
                        socialScore = socialScore,
                        scoreBreakdown = scoreBreakdown,
                        onViewStats = onNavigateToStats
                    )
                }

                if (activeSessions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(activeSessions) { session ->
                        ActiveSessionCard(
                            session = session,
                            onStop = { viewModel.stopTask(session) },
                            onPauseResume = { viewModel.pauseResumeTask(session) },
                            onUpdateName = { viewModel.updateSessionName(session.groupId, it) },
                            onKindChange = { viewModel.updateSessionKind(session.groupId, it) },
                            onSessionClick = { 
                                selectedGroupId = session.groupId
                            }
                        )
                    }
                }

                if (completedSessions.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Recent Activity (Last 24h)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    val recentSessions = completedSessions.filter { session ->
                        val latestTask = session.maxByOrNull { it.startTime }
                        latestTask != null && (System.currentTimeMillis() - latestTask.startTime) < 24 * 60 * 60 * 1000L
                    }

                    if (recentSessions.isEmpty()) {
                        item {
                            Text("No recent activity found.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        items(recentSessions) { session ->
                            if (session.size > 1) {
                                CompletedSessionCard(
                                    segments = session,
                                    onClick = { selectedGroupId = session.first().groupId },
                                    onDelete = { viewModel.clearSession(session.first().groupId) }
                                )
                            } else {
                                val task = session.first()
                                SingleTaskItemCard(
                                    task = task,
                                    onClick = { selectedTaskId = task.id },
                                    onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                    onDelete = { viewModel.clearSegment(task) },
                                    onAddToCalendar = { addToGoogleCalendar(context, task) }
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    selectedSessionSegments?.let { segments ->
        SessionDetailDialog(
            segments = segments,
            onDismiss = { selectedGroupId = null },
            onUpdateSessionName = { name -> viewModel.updateSessionName(segments.first().groupId, name) },
            onUpdateSessionKind = { kind -> viewModel.updateSessionKind(segments.first().groupId, kind) },
            onUpdateSegment = { viewModel.updateSegment(it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) }
        )
    }

    selectedTask?.let { task ->
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

fun calculatePercentageOfDay(taskDuration: Long, timestamp: Long): String {
    val dayMillis = 24L * 60L * 60L * 1000L
    val percentage = (taskDuration.toDouble() / dayMillis.toDouble()) * 100
    val dayLabel = getDayLabel(timestamp)
    return String.format("%.1f%% of %s", percentage, dayLabel)
}

fun calculateSessionPercentage(segments: List<Task>, activeDuration: Long = 0L, activeTask: Task? = null): String {
    val dayMillis = 24L * 60L * 60L * 1000L
    val dayDurations = mutableMapOf<Long, Long>()

    segments.forEach { segment ->
        val dayStart = getStartOfDay(segment.startTime)
        dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + segment.duration
    }

    activeTask?.let {
        val dayStart = getStartOfDay(it.startTime)
        dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + activeDuration
    }

    if (dayDurations.isEmpty()) return "0.0% of Today"

    return dayDurations.entries
        .sortedByDescending { it.key }
        .joinToString(" - ") { (dayStart, duration) ->
            val percentage = (duration.toDouble() / dayMillis.toDouble()) * 100
            val dayLabel = getDayLabel(dayStart)
            String.format("%.1f%% of %s", percentage, dayLabel)
        }
}

fun getStartOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getDayLabel(timestamp: Long): String {
    val dateInfo = formatCardDate(timestamp)
    if (dateInfo.isEmpty()) return "Today"
    
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday"
    
    return dateInfo
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
                    text = "Duration: ${formatDetailedDuration(task.duration)} • $percentage",
                    style = MaterialTheme.typography.bodySmall)
            }
            
            if (isCalendarTask) {
                IconButton(onClick = { openInSystemCalendar(context, task) }) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Open in Calendar",
                        tint = Color(0xFF2196F3), // Material Blue
                        modifier = Modifier.size(24.dp)
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
                        imageVector = if (task.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                        contentDescription = if (task.savedToCalendar) "Remove from Calendar list" else "Add to Calendar",
                        tint = if (task.savedToCalendar) Success else PurplePrimary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
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
    onKindChange: (TaskKind) -> Unit,
    onSessionClick: () -> Unit
) {
    val isExpanded by session.isExpanded.collectAsState()
    val activeSegment = session.activeSegment
    val focusManager = LocalFocusManager.current
    
    val activeElapsed by (activeSegment?.elapsedTime?.collectAsState() ?: remember { mutableStateOf(0L) })
    
    val refTask = activeSegment?.task ?: session.segments.firstOrNull() ?: return
    val percentage = calculateSessionPercentage(session.segments, activeElapsed, activeSegment?.task)

    val prevTotal = session.segments.sumOf { it.duration }
    val totalTime = prevTotal + activeElapsed

    // Determine a stable session name: Use the first segment that is NOT custom-named
    val sessionName = (session.segments.find { !it.isNameCustom } ?: activeSegment?.task ?: session.segments.firstOrNull())?.name ?: "Untitled"
    var editableName by remember(sessionName) { mutableStateOf(sessionName) }
    
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(totalTime),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (taskColor == Color.White) Color.Black else taskColor
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
                            val segPercentage = calculatePercentageOfDay(activeElapsed, activeSegment.task.startTime)
                            Text(
                                text = "Current: ${activeSegment.task.name} - ${formatDetailedDuration(activeElapsed)} • $segPercentage",
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
                        session.segments.sortedByDescending { it.startTime }.forEach { segment ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(segment.kind.colorValue)))
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
    
    // Find majority segment type
    val majorityKind = segments.groupBy { it.kind }
        .maxByOrNull { it.value.size }?.key ?: segments.first().kind
    
    // Stable title: Use the first non-customized segment as the session title
    val sessionName = (segments.find { !it.isNameCustom } ?: segments.firstOrNull())?.name ?: "Untitled Session"
    
    val totalDuration = segments.sumOf { it.duration }
    val taskColor = Color(majorityKind.colorValue)
    val percentage = calculateSessionPercentage(segments)

    val allSaved = segments.all { it.savedToCalendar }
    val someSaved = segments.any { it.savedToCalendar }
    val isCalendarSession = segments.any { it.id.startsWith("cal_") }
    
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
                        text = "${segments.size} segment(s) • Total: ${formatDetailedDuration(totalDuration)} • $percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    if (allSaved && !isCalendarSession) {
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

                if (isCalendarSession) {
                    IconButton(onClick = { openInSystemCalendar(context, segments.first()) }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday, 
                            contentDescription = "Open in Calendar", 
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    if (allSaved) {
                        Icon(Icons.Default.EventAvailable, "All Saved", tint = Success, modifier = Modifier.size(20.dp))
                    } else if (someSaved) {
                        Icon(Icons.Default.CalendarMonth, "Partially Saved", tint = PurplePrimary, modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    segments.sortedByDescending { it.startTime }.forEach { segment ->
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
                                val segPercentage = calculatePercentageOfDay(segment.duration, segment.startTime)
                                Text(
                                    text = "${formatDetailedDuration(segment.duration)} • $segPercentage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            if (segment.id.startsWith("cal_")) {
                                IconButton(onClick = { openInSystemCalendar(context, segment) }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday, 
                                        null, 
                                        tint = Color(0xFF2196F3), 
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (segment.savedToCalendar) {
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
    onToggleCalendar: (Task) -> Unit,
    onFlatten: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Ticker for running tasks
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

    // Reference values
    val sessionRef = segments.find { !it.isNameCustom && !it.isKindCustom } ?: segments.firstOrNull() ?: return
    var sessionNameInput by remember(sessionRef.name) { mutableStateOf(sessionRef.name) }
    val focusManager = LocalFocusManager.current
    
    val segmentsWithEditors = remember { mutableStateListOf<String>() }
    var showFlattenConfirm by remember { mutableStateOf(false) }

    if (showFlattenConfirm) {
        AlertDialog(
            onDismissRequest = { showFlattenConfirm = false },
            title = { Text("Flatten Session?") },
            text = { Text("Are you sure? This cannot be undone. All segments will be merged into one single task, removing any gaps between them.") },
            confirmButton = {
                Button(
                    onClick = {
                        onFlatten?.invoke()
                        showFlattenConfirm = false
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Details")
                
                val isCalendarSession = segments.any { it.id.startsWith("cal_") }
                val allSaved = segments.all { it.savedToCalendar }
                if (!isCalendarSession && !allSaved && segments.size > 1 && onFlatten != null && !hasRunningTask) {
                    IconButton(onClick = { showFlattenConfirm = true }) {
                        Icon(Icons.Default.Compress, contentDescription = "Flatten", tint = PurplePrimary)
                    }
                }
            }
        },
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
                    val isCalendarSession = segments.any { it.id.startsWith("cal_") }
                    
                    OutlinedTextField(
                        value = sessionNameInput,
                        onValueChange = { 
                            sessionNameInput = it
                        },
                        label = { Text("Session Name") },
                        enabled = !isCalendarSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { 
                                if (!it.isFocused && sessionNameInput != sessionRef.name && !isCalendarSession) {
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
                        if (isCalendarSession) {
                            TaskKindChip(kind = sessionRef.kind)
                        } else {
                            TaskKindDropdownMenu(
                                selectedKind = sessionRef.kind,
                                onKindSelected = { onUpdateSessionKind(it) }
                            )
                        }
                    }
                    if (!isCalendarSession) {
                        Text(
                            "Note: These values push to all linked segments.",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            "This session is loaded from your device calendar.",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF2196F3)
                        )
                    }
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
                    segments.sortedByDescending { it.startTime }.forEach { segment ->
                        val isEditing = segmentsWithEditors.contains(segment.id)
                        val isCustom = segment.isNameCustom || segment.isKindCustom
                        val isCalendarSegment = segment.id.startsWith("cal_")
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
                                        if (!isCalendarSegment) {
                                            if (isEditing) segmentsWithEditors.remove(segment.id) 
                                            else segmentsWithEditors.add(segment.id)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    enabled = !isCalendarSegment
                                ) {
                                    Icon(
                                        imageVector = if (isCalendarSegment) Icons.Outlined.CalendarToday else if (isCustom) Icons.Default.LinkOff else Icons.Default.Link,
                                        contentDescription = null,
                                        tint = if (isCalendarSegment) Color(0xFF2196F3) else if (isCustom) PurplePrimary else Color.Gray.copy(alpha = 0.6f),
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
                                        val segPercentage = calculatePercentageOfDay(segmentDuration, segment.startTime)
                                        Text(
                                            text = " • ${if (segment.isRunning) "Running: ${formatTime(segmentDuration)}" else formatStartEndRange(segment.startTime, segment.endTime)} • $segPercentage",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (segment.isRunning) PurplePrimary else Color.Gray
                                        )
                                    }
                                }
                                
                                // Calendar Icon (The right-most icon)
                                if (isCalendarSegment) {
                                    IconButton(onClick = { openInSystemCalendar(context, segment) }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.EventAvailable,
                                            contentDescription = "Open in Calendar",
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { 
                                        if (!segment.savedToCalendar) {
                                            addToGoogleCalendar(context, segment)
                                        }
                                        onToggleCalendar(segment)
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                                            contentDescription = if (segment.savedToCalendar) "Remove from Calendar list" else "Add to Calendar",
                                            tint = if (segment.savedToCalendar) Success else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
                val runningTask = segments.find { it.isRunning }
                val runningDuration = if (runningTask != null) (ticker - runningTask.startTime) else 0L
                val totalPercentage = calculateSessionPercentage(segments.filter { !it.isRunning }, runningDuration, runningTask)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Time:", fontWeight = FontWeight.Bold)
                    Text("${formatDetailedDuration(totalDuration)} • $totalPercentage", fontWeight = FontWeight.Bold, color = PurplePrimary)
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
    val isCalendarTask = task.id.startsWith("cal_")

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
                    enabled = !isCalendarTask,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && name != task.name && !isCalendarTask) {
                                onSaveName(name)
                            }
                        },
                    singleLine = true,
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
                
                Divider()
                
                val currentEndTime = task.endTime ?: currentTime
                if (isSpanningDays(task.startTime, currentEndTime)) {
                    DetailItem(label = "Date", value = formatDateRange(task.startTime, currentEndTime))
                }
                
                if (isCalendarTask) {
                    DetailItem(label = "Started", value = formatDateTime(task.startTime))
                    DetailItem(label = "Stopped", value = formatDateTime(currentEndTime))
                } else {
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
                }
                
                val liveDuration = if (task.isRunning) (currentTime - task.startTime) else task.duration
                DetailItem(label = "Duration", value = formatDetailedDuration(liveDuration))
                
                if (!isCalendarTask) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.savedToCalendar,
                            onCheckedChange = onToggleCalendar
                        )
                        Text("Auto-delete task in 24 hours")
                    }
                    if (!task.savedToCalendar) {
                        Button(
                            onClick = { addToGoogleCalendar(context, task) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp))
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
                        Icon(Icons.Outlined.CalendarToday, null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loaded from your device calendar. Tap to view.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2196F3))
                    }
                }
                
                if (task.savedToCalendar && task.savedToCalendarAt != null && !isCalendarTask) {
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
                if (name != task.name && !isCalendarTask) onSaveName(name)
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

fun formatCardDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            
    if (isToday) return ""
    
    val threeDaysAgo = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -3)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    return if (timestamp >= threeDaysAgo) {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        sdf.format(Date(timestamp))
    } else {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(timestamp))
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

    val description = "Type: ${task.kind.displayName}\nDuration: $durationStr\nTracked via Inventoria Task Tracker\nTask ID: ${task.id}\nSession ID: ${task.groupId}"

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
    val isCalendarTask = task.id.startsWith("cal_")
    if (isCalendarTask) {
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
    
    // Fallback: Open calendar at specific time
    val uri = Uri.parse("content://com.android.calendar/time/${task.startTime}")
    val intent = Intent(Intent.ACTION_VIEW).setData(uri)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("TaskTracker", "Could not open calendar view", e)
    }
}
