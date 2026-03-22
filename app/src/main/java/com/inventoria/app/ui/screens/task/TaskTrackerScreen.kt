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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

enum class CalendarStatus { EMPTY, SOME, FULL_LOCAL, FULL_CALENDAR }

@Composable
fun rememberTick(intervalMs: Long = 1000): State<Long> {
    val tickState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            tickState.longValue = System.currentTimeMillis()
        }
    }
    return tickState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TaskTrackerScreen(
    viewModel: TaskTrackerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeSessions by viewModel.activeSessions.collectAsState()
    val currentTime by rememberTick()
    val completedSessions by viewModel.completedSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val isFlowModeEnabled by viewModel.isFlowModeEnabled.collectAsState()
    val isAutoStartPending by viewModel.isAutoStartPending.collectAsState()
    val isSelectionMode = selectedTaskIds.isNotEmpty()

    val totalScore by viewModel.totalScoreToday.collectAsState()
    val personalScore by viewModel.personalScoreToday.collectAsState()
    val socialScore by viewModel.socialScoreToday.collectAsState()

    val calendarPermissionState = rememberPermissionState(android.Manifest.permission.READ_CALENDAR)
    LaunchedEffect(calendarPermissionState.status.isGranted) { if (calendarPermissionState.status.isGranted) viewModel.refreshCalendar() }
    
    var selectedSessionGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    val currentSelectedSession = remember(selectedSessionGroupId, activeSessions, completedSessions) {
        selectedSessionGroupId?.let { groupId ->
            activeSessions.find { it.groupId == groupId }?.let { it.segments + listOfNotNull(it.activeSegment?.task) }
                ?: completedSessions.find { it.firstOrNull()?.groupId == groupId }
        }
    }

    val currentSelectedTask = remember(selectedTaskId, activeSessions, completedSessions) {
        selectedTaskId?.let { id ->
            activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) }.find { it.id == id }
                ?: completedSessions.flatten().find { it.id == id }
        }
    }

    val taskSuggestions = remember(activeSessions, completedSessions) {
        val allTasks = activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) } + completedSessions.flatten()
        allTasks.filter { it.name.isNotBlank() && !it.name.startsWith("Task ") && it.name.lowercase() != "untitled" && !it.isDeleted }
            .distinctBy { it.name.trim().lowercase() }.map { Pair(it.name.trim(), it.groupId) }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTaskIds.size} Selected") },
                    navigationIcon = { IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        IconButton(onClick = { viewModel.saveSelectedTasksToCalendar() }) { Icon(Icons.Default.Save, "Save Selected") }
                        IconButton(onClick = { viewModel.deleteSelectedTasks() }) { Icon(Icons.Default.Delete, "Delete Selected") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Task Tracker", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = {
                        IconButton(onClick = { if (calendarPermissionState.status.isGranted) viewModel.refreshCalendar() else calendarPermissionState.launchPermissionRequest() }) { Icon(Icons.Default.Sync, null) }
                        IconButton(onClick = onNavigateToStats) { Icon(Icons.Default.BarChart, null) }
                        IconButton(onClick = onNavigateToHistory) { Icon(Icons.Default.History, null) }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && activeSessions.size < 5) {
                FloatingActionButton(onClick = { viewModel.addNewTask() }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    DailyScoreCard(
                        totalScore = totalScore,
                        personalScore = personalScore,
                        socialScore = socialScore,
                        onClick = onNavigateToStats
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Flow Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Auto-start next task", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isFlowModeEnabled,
                                onCheckedChange = { viewModel.toggleFlowMode(it) }
                            )
                        }
                    }
                }
                if (isAutoStartPending) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("New task starting in 1s...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (activeSessions.isNotEmpty()) {
                    item { Text("Active Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(activeSessions) { session ->
                        ActiveSessionCard(
                            session = session,
                            currentTime = currentTime,
                            suggestions = taskSuggestions,
                            isFlowModeEnabled = isFlowModeEnabled,
                            onStop = { viewModel.stopTask(session) },
                            onPauseResume = { viewModel.pauseResumeTask(session) },
                            onUpdateName = { viewModel.updateSessionName(session.groupId, it) },
                            onAutocompleteSelect = { n, g -> viewModel.updateSessionNameAndGroup(session.groupId, n, g) },
                            onUpdateKind = { viewModel.updateSessionKind(session.groupId, it) },
                            onSessionClick = { selectedSessionGroupId = session.groupId }
                        )
                    }
                }
                if (completedSessions.isNotEmpty()) {
                    item { Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    val recentSessions = completedSessions.filter { it.maxByOrNull { t -> t.startTime }?.let { t -> currentTime - t.startTime < 86400000 } == true }
                    items(recentSessions) { session ->
                        if (session.size > 1) {
                            CompletedSessionCard(
                                segments = session,
                                currentTime = currentTime,
                                selectedTaskIds = selectedTaskIds,
                                onClick = { selectedSessionGroupId = session.first().groupId },
                                onDelete = { viewModel.deleteSession(session.first().groupId) },
                                onSegmentLongClick = { viewModel.toggleTaskSelection(it.id) },
                                onSegmentClick = { 
                                    if (isSelectionMode) viewModel.toggleTaskSelection(it.id) 
                                    else selectedTaskId = it.id 
                                }
                            )
                        } else {
                            val task = session.first()
                            SingleTaskItemCard(
                                task = task, isSelected = task.id in selectedTaskIds,
                                onClick = { if (isSelectionMode) viewModel.toggleTaskSelection(task.id) else selectedTaskId = task.id },
                                onLongClick = { viewModel.toggleTaskSelection(task.id) },
                                onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                onDelete = { viewModel.deleteSegment(task) },
                                onAddToCalendar = { addToGoogleCalendar(context, task) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    currentSelectedSession?.let { segments ->
        SessionDetailDialog(
            segments = segments, onDismiss = { selectedSessionGroupId = null },
            onUpdateSessionName = { viewModel.updateSessionName(segments.first().groupId, it) },
            onUpdateSessionKind = { viewModel.updateSessionKind(segments.first().groupId, it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) },
            onNavigateToTaskDetail = { selectedTaskId = it } // Bypassing route, using dialog
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

@Composable
fun DailyScoreCard(totalScore: Int, personalScore: Int, socialScore: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Today's Productivity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(text = "$totalScore pts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreMiniItem("Personal", personalScore, PurplePrimary)
                ScoreMiniItem("Social", socialScore, Success)
            }
        }
    }
}

@Composable
fun ScoreMiniItem(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = if (score >= 0) "+$score" else "$score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleTaskItemCard(
    task: Task, isSelected: Boolean = false,
    onClick: () -> Unit, onLongClick: () -> Unit,
    onToggleCalendar: () -> Unit, onDelete: () -> Unit, onAddToCalendar: () -> Unit
) {
    val context = LocalContext.current
    val taskColor = Color(task.kind.colorValue)
    val isCalendarTask = task.id.startsWith("cal_")
    
    // Task cards preserve original full duration/percentage as requested
    val percentage = calculatePercentageOfDay(task.duration, task.startTime)
    
    val backgroundColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primaryContainer else taskColor.copy(alpha = 0.1f))

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskKindChip(kind = task.kind, modifier = Modifier.scale(0.8f))
                    Text(text = task.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = percentage, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatDetailedDuration(task.duration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalendarActionIcon(isCalendarTask = isCalendarTask, savedToCalendar = task.savedToCalendar, onToggle = onToggleCalendar, onAdd = onAddToCalendar, onOpen = { openInSystemCalendar(context, task) })
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun CalendarActionIcon(isCalendarTask: Boolean, savedToCalendar: Boolean, onToggle: () -> Unit, onAdd: () -> Unit, onOpen: () -> Unit) {
    if (isCalendarTask) { IconButton(onClick = onOpen) { Icon(Icons.Default.EventAvailable, null, tint = Color(0xFF4285F4)) } } // Blue for purely loaded tasks
    else { IconButton(onClick = { if (!savedToCalendar) onAdd(); onToggle() }) { Icon(if (savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday, null, tint = if (savedToCalendar) Success else PurplePrimary) } } // Green check for Local
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedSessionCard(
    segments: List<Task>, 
    currentTime: Long,
    selectedTaskIds: Set<String>,
    onClick: () -> Unit, onDelete: () -> Unit,
    onSegmentLongClick: (Task) -> Unit, onSegmentClick: (Task) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val majorityKind = segments.groupBy { it.kind }.maxByOrNull { it.value.size }?.key ?: segments.first().kind
    val sessionName = segments.firstOrNull { !it.isNameCustom }?.name ?: segments.firstOrNull()?.name ?: "Untitled Session"
    
    val todayStart = getStartOfDay(currentTime)
    // MIDNIGHT BLEED: Session summary only shows time that happened TODAY
    val todayDuration = segments.sumOf { calculateOverlapWithToday(it.startTime, it.endTime ?: (it.startTime + it.duration), todayStart) }
    val percentage = calculatePercentageOfDay(todayDuration, todayStart)
    
    val taskColor = Color(majorityKind.colorValue)
    val status = calculateCalendarStatus(segments)
    
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskKindChip(kind = majorityKind, modifier = Modifier.scale(0.8f))
                    Text(text = sessionName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (segments.size > 1) { IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) { Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = percentage, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatDetailedDuration(todayDuration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = when(status) { CalendarStatus.FULL_CALENDAR -> Icons.Default.EventAvailable; CalendarStatus.FULL_LOCAL -> Icons.Default.CheckCircle; CalendarStatus.SOME -> Icons.Default.CalendarMonth; else -> Icons.Default.CalendarToday }, contentDescription = null, tint = when(status) { CalendarStatus.FULL_CALENDAR -> Color(0xFF4285F4); CalendarStatus.FULL_LOCAL -> Success; CalendarStatus.SOME -> PurplePrimary; else -> Color.Gray }, modifier = Modifier.size(24.dp).padding(4.dp))
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    segments.sortedByDescending { it.startTime }.forEach { segment ->
                        var showMenu by remember { mutableStateOf(false) }
                        val isSegmentSelected = segment.id in selectedTaskIds
                        val segmentBg by animateColorAsState(if (isSegmentSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)

                        Row(
                            modifier = Modifier.fillMaxWidth().background(segmentBg, RoundedCornerShape(4.dp)).combinedClickable(onClick = { onSegmentClick(segment) }, onLongClick = { showMenu = true }).padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(segment.kind.colorValue)))
                            Spacer(Modifier.width(8.dp))
                            Text(text = segment.name, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray, modifier = Modifier.weight(1f))
                            if (isSegmentSelected) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            } else if (segment.savedToCalendar) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Success)
                            } else if (segment.id.startsWith("cal_")) {
                                Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(14.dp), tint = Color(0xFF4285F4))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Edit Details") }, onClick = { showMenu = false; onSegmentClick(segment) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                DropdownMenuItem(text = { Text(if (isSegmentSelected) "Deselect" else "Select") }, onClick = { showMenu = false; onSegmentLongClick(segment) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculateCalendarStatus(segments: List<Task>): CalendarStatus {
    val total = segments.size; val savedLocal = segments.count { it.savedToCalendar }; val savedCalendar = segments.count { it.id.startsWith("cal_") }
    return when { savedCalendar == total -> CalendarStatus.FULL_CALENDAR; savedLocal == total -> CalendarStatus.FULL_LOCAL; savedLocal > 0 || savedCalendar > 0 -> CalendarStatus.SOME; else -> CalendarStatus.EMPTY }
}

@Composable
fun ActiveSessionCard(session: TaskSessionUI, currentTime: Long, suggestions: List<Pair<String, String>>, isFlowModeEnabled: Boolean, onStop: () -> Unit, onPauseResume: () -> Unit, onUpdateName: (String) -> Unit, onAutocompleteSelect: (String, String) -> Unit, onUpdateKind: (TaskKind) -> Unit, onSessionClick: () -> Unit) {
    val isExpanded by session.isExpanded.collectAsState(); val activeSegment = session.activeSegment; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; val activeElapsed by (activeSegment?.elapsedTime?.collectAsState() ?: remember { mutableStateOf(0L) }); val refTask = activeSegment?.task ?: session.segments.firstOrNull() ?: return; 
    
    val todayStart = getStartOfDay(currentTime)
    // MIDNIGHT BLEED: Active summary only shows time that happened TODAY
    val todaySegmentsDuration = session.segments.sumOf {calculateOverlapWithToday(it.startTime, it.endTime ?: (it.startTime + it.duration), todayStart)}
    val todayActiveElapsed = if (activeSegment != null) calculateOverlapWithToday(activeSegment.task.startTime, currentTime, todayStart) else 0L
    
    val totalTimeToday = todaySegmentsDuration + todayActiveElapsed
    val percentage = calculatePercentageOfDay(totalTimeToday, todayStart)
    
    val sessionName = session.segments.firstOrNull { !it.isNameCustom }?.name ?: activeSegment?.task?.name ?: session.segments.firstOrNull()?.name ?: "Untitled"; var editableName by remember(sessionName) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(sessionName, if (sessionName.startsWith("Task ")) androidx.compose.ui.text.TextRange(0, sessionName.length) else androidx.compose.ui.text.TextRange(sessionName.length))) }; var isFocused by remember { mutableStateOf(false) }; var dropdownDismissedByUser by remember { mutableStateOf(false) }; LaunchedEffect(editableName.text) { dropdownDismissedByUser = false }; val filteredSuggestions = remember(editableName.text, isFocused, dropdownDismissedByUser) { if (!isFocused || dropdownDismissedByUser || editableName.text.isBlank()) emptyList() else suggestions.filter { it.first.contains(editableName.text, ignoreCase = true) && !it.first.equals(editableName.text, ignoreCase = true) }.take(5) }; val taskColor = Color(refTask.kind.colorValue)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(session.groupId, activeSegment?.task?.id) { if (isFlowModeEnabled && sessionName.startsWith("Task ") && activeSegment?.task?.isRunning == true) { delay(100); focusRequester.requestFocus(); keyboardController?.show() } }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(value = editableName, onValueChange = { editableName = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focusState -> isFocused = focusState.isFocused; if (!focusState.isFocused && editableName.text != sessionName) onUpdateName(editableName.text) }, textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }), singleLine = true)
                        DropdownMenu(expanded = filteredSuggestions.isNotEmpty(), onDismissRequest = { dropdownDismissedByUser = true }, properties = PopupProperties(focusable = false), modifier = Modifier.fillMaxWidth(0.8f)) { filteredSuggestions.forEach { suggestion -> DropdownMenuItem(text = { Text(suggestion.first) }, onClick = { editableName = androidx.compose.ui.text.input.TextFieldValue(suggestion.first, androidx.compose.ui.text.TextRange(suggestion.first.length)); focusManager.clearFocus(); keyboardController?.hide(); onAutocompleteSelect(suggestion.first, suggestion.second) }) } }
                    }
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                IconButton(onClick = onSessionClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
            }
            Spacer(modifier = Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { TaskKindDropdownMenu(selectedKind = refTask.kind, onKindSelected = onUpdateKind); if (session.segments.isNotEmpty()) { IconButton(onClick = { session.isExpanded.value = !isExpanded }, modifier = Modifier.size(24.dp)) { Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray) } } }
            Spacer(modifier = Modifier.height(16.dp)); Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(text = formatTime(totalTimeToday), color = if (taskColor == Color.White) Color.Black else taskColor, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text(text = percentage, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    if (activeSegment == null) { Text(text = "PAUSED", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 12.dp)) }
                    IconButton(onClick = onPauseResume, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)) { Icon(if (activeSegment != null) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(8.dp))
                    if (isFlowModeEnabled) {
                        TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary), modifier = Modifier.height(40.dp)) {
                            Text("Stop & Continue", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onStop, modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)) { 
                            Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.error) 
                        }
                    }
                } 
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (activeSegment != null) { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(taskColor)); Spacer(Modifier.width(8.dp)); Text(text = "Current: ${activeSegment.task.name} - ${formatDetailedDuration(activeElapsed)} \u2022 ${calculatePercentageOfDay(activeElapsed, activeSegment.task.startTime)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.DarkGray) }; HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp), thickness = 0.5.dp) }
                    if (session.segments.isNotEmpty()) { Text("Previous segments:", style = MaterialTheme.typography.labelSmall, color = Color.Gray); session.segments.sortedByDescending { it.startTime }.forEach { segment -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(segment.kind.colorValue))); Spacer(Modifier.width(8.dp)); Text(text = "${segment.name} - ${formatDetailedDuration(segment.duration)} \u2022 ${calculatePercentageOfDay(segment.duration, segment.startTime)}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray) } } }
                }
            }
        }
    }
}

@Composable
fun SessionDetailDialog(
    segments: List<Task>, onDismiss: () -> Unit,
    onUpdateSessionName: (String) -> Unit, onUpdateSessionKind: (TaskKind) -> Unit,
    onToggleCalendar: (Task) -> Unit,
    onFlatten: () -> Unit, onNavigateToTaskDetail: (String) -> Unit
) {
    val sessionRef = segments.first(); var sessionNameInput by remember { mutableStateOf(sessionRef.name) }; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; var showFlattenConfirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { focusManager.clearFocus(); if (sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput); onDismiss() },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(8.dp)); Text("Session Details") } },
        text = {
            Column(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = sessionNameInput, onValueChange = { sessionNameInput = it }, label = { Text("Session Name") }, modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Session Category: ", style = MaterialTheme.typography.bodySmall); TaskKindDropdownMenu(selectedKind = sessionRef.kind, onKindSelected = onUpdateSessionKind) }
                HorizontalDivider(); Text("Segments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                segments.sortedByDescending { it.startTime }.forEach { segment ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onNavigateToTaskDetail(segment.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(segment.kind.colorValue))); Spacer(Modifier.width(8.dp))
                            Text(text = segment.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            if (segment.id.startsWith("cal_")) {
                                Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(16.dp), tint = Color(0xFF4285F4))
                            } else {
                                IconButton(onClick = { onToggleCalendar(segment) }, modifier = Modifier.size(24.dp)) { Icon(if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = if (segment.savedToCalendar) Success else PurplePrimary) }
                            }
                        }
                        Text(text = "${formatDetailedDuration(segment.duration)} \u2022 ${formatStartEndRange(segment.startTime, segment.endTime)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                if (segments.size > 1) { TextButton(onClick = { showFlattenConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Merge, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Flatten into one segment") } }
            }
        },
        confirmButton = { TextButton(onClick = { focusManager.clearFocus(); if (sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput); onDismiss() }) { Text("Close") } }
    )
    if (showFlattenConfirm) { AlertDialog(onDismissRequest = { showFlattenConfirm = false }, title = { Text("Flatten Session?") }, text = { Text("This will merge all segments into a single continuous task. This action cannot be undone.") }, confirmButton = { Button(onClick = { onFlatten(); showFlattenConfirm = false; onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Flatten") } }, dismissButton = { TextButton(onClick = { showFlattenConfirm = false }) { Text("Cancel") } }) }
}

@Composable
fun TaskDetailDialog(task: Task, onDismiss: () -> Unit, onSaveName: (String) -> Unit, onKindChange: (TaskKind) -> Unit, onToggleCalendar: (Boolean) -> Unit, onUpdateTime: (Long, Long) -> Unit) {
    val context = LocalContext.current; var name by remember(task.name) { mutableStateOf(task.name) }; var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; val isCalendarTask = task.id.startsWith("cal_")
    
    // Duration Editor State
    val initialDuration = task.duration
    var days by remember(initialDuration) { mutableStateOf(TimeUnit.MILLISECONDS.toDays(initialDuration).toString()) }
    var hours by remember(initialDuration) { mutableStateOf((TimeUnit.MILLISECONDS.toHours(initialDuration) % 24).toString()) }
    var minutes by remember(initialDuration) { mutableStateOf((TimeUnit.MILLISECONDS.toMinutes(initialDuration) % 60).toString()) }
    var seconds by remember(initialDuration) { mutableStateOf((TimeUnit.MILLISECONDS.toSeconds(initialDuration) % 60).toString()) }

    fun calculateNewDuration(): Long {
        val d = days.toLongOrNull() ?: 0L
        val h = hours.toLongOrNull() ?: 0L
        val m = minutes.toLongOrNull() ?: 0L
        val s = seconds.toLongOrNull() ?: 0L
        return TimeUnit.DAYS.toMillis(d) + TimeUnit.HOURS.toMillis(h) + TimeUnit.MINUTES.toMillis(m) + TimeUnit.SECONDS.toMillis(s)
    }

    LaunchedEffect(task.id, task.savedToCalendar, task.isRunning) { while (task.savedToCalendar || task.isRunning) { currentTime = System.currentTimeMillis(); delay(1000) } }
    AlertDialog(
        onDismissRequest = { 
            focusManager.clearFocus()
            val newDur = calculateNewDuration()
            if (newDur != task.duration && !isCalendarTask && !task.isRunning) {
                onUpdateTime(task.startTime, task.startTime + newDur)
            }
            if (name != task.name && !isCalendarTask) onSaveName(name)
            onDismiss() 
        },
        confirmButton = { 
            TextButton(onClick = { 
                focusManager.clearFocus()
                val newDur = calculateNewDuration()
                if (newDur != task.duration && !isCalendarTask && !task.isRunning) {
                    onUpdateTime(task.startTime, task.startTime + newDur)
                }
                if (name != task.name && !isCalendarTask) onSaveName(name)
                onDismiss() 
            }) { Text("Done") } 
        },
        title = { Text("Task Details") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && name != task.name && !isCalendarTask) onSaveName(name) }, enabled = !isCalendarTask, label = { Text("Task Name") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }))
                if (isCalendarTask) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Type: ", style = MaterialTheme.typography.bodySmall); TaskKindChip(kind = task.kind) } } else { TaskKindDropdownMenu(selectedKind = task.kind, onKindSelected = onKindChange) }
                HorizontalDivider()
                
                Text("Edit Duration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationPartField("Days", days, { days = it }, Modifier.weight(1f), !isCalendarTask && !task.isRunning)
                    DurationPartField("Hrs", hours, { hours = it }, Modifier.weight(1f), !isCalendarTask && !task.isRunning)
                    DurationPartField("Min", minutes, { minutes = it }, Modifier.weight(1f), !isCalendarTask && !task.isRunning)
                    DurationPartField("Sec", seconds, { seconds = it }, Modifier.weight(1f), !isCalendarTask && !task.isRunning)
                }
                if (task.isRunning) {
                    Text("Duration cannot be edited while task is running.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()
                val currentEndTime = task.endTime ?: currentTime; if (isSpanningDays(task.startTime, currentEndTime)) { DetailItem("Date", formatDateRange(task.startTime, currentEndTime)) }
                if (isCalendarTask) { DetailItem("Started", formatDateTime(task.startTime)); DetailItem("Stopped", formatDateTime(currentEndTime)) } else { EditableDetailItem("Started", formatDateTime(task.startTime)) { showDateTimePicker(context, task.startTime) { newStart -> onUpdateTime(newStart, task.endTime ?: System.currentTimeMillis()) } }; val stoppedText = if (task.endTime != null) formatDateTime(task.endTime!!) else "Running..."; EditableDetailItem("Stopped", stoppedText) { if (task.endTime != null) { showDateTimePicker(context, task.endTime!!) { newEnd -> onUpdateTime(task.startTime, newEnd) } } } }
                val liveDuration = if (task.isRunning) currentTime - task.startTime else task.duration; DetailItem("Duration", formatDetailedDuration(liveDuration))
                
                // Calendar Sync UI
                if (!isCalendarTask) { 
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { 
                        Checkbox(checked = task.savedToCalendar, onCheckedChange = onToggleCalendar)
                        Text("Auto-delete task in 24 hours", style = MaterialTheme.typography.bodySmall)
                    }
                    if (task.savedToCalendar) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp), tint = Success)
                            Spacer(Modifier.width(8.dp))
                            Text(text = "Task is saved to local completion history.", style = MaterialTheme.typography.labelSmall, color = Success)
                        }
                    } else { 
                        Button(onClick = { addToGoogleCalendar(context, task) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { 
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add to Google Calendar") 
                        } 
                    } 
                } else { 
                    Row(modifier = Modifier.fillMaxWidth().clickable { openInSystemCalendar(context, task) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(24.dp), tint = Color(0xFF4285F4))
                        Spacer(Modifier.width(8.dp)); Text(text = "Loaded from your device calendar. Tap to view.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4285F4)) 
                    } 
                }
                if (task.savedToCalendar && task.savedToCalendarAt != null && !isCalendarTask) { val remaining = 86400000 - (currentTime - task.savedToCalendarAt!!); if (remaining > 0) { Text(text = "Auto-delete in: ${formatDetailedDuration(remaining)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) } }
            }
        }
    )
}

@Composable
fun DurationPartField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) onValueChange(it) },
            label = { Text(label, fontSize = 10.sp) },
            textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )
    }
}

@Composable fun DetailItem(label: String, value: String) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium); Text(value, style = MaterialTheme.typography.bodyMedium) } }
@Composable fun EditableDetailItem(label: String, value: String, onEdit: () -> Unit) { Row(modifier = Modifier.fillMaxWidth().clickable { onEdit() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium); Row(verticalAlignment = Alignment.CenterVertically) { Text(value, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) } } }
fun formatTime(milliseconds: Long): String { val hours = TimeUnit.MILLISECONDS.toHours(milliseconds); val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60; val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60; return String.format("%02d:%02d:%02d", hours, minutes, seconds) }
fun formatDetailedDuration(milliseconds: Long): String { val days = TimeUnit.MILLISECONDS.toDays(milliseconds); val hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24; val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60; val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60; val parts = mutableListOf<String>(); if (days > 0) parts.add("${days} days"); if (hours > 0) parts.add("${hours} hours"); if (minutes > 0) parts.add("${minutes} min"); if (seconds > 0 || parts.isEmpty()) parts.add("${seconds} sec"); return parts.joinToString(" ") }
fun formatDate(timestamp: Long): String { val now = Calendar.getInstance(); val target = Calendar.getInstance().apply { timeInMillis = timestamp }; val isSameWeek = now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR) && now.get(Calendar.YEAR) == target.get(Calendar.YEAR); return if (isSameWeek) { val sdf = SimpleDateFormat("EEEE", Locale.getDefault()); "[${sdf.format(Date(timestamp))}]" } else { val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.getDefault()); "[${sdf.format(Date(timestamp)).lowercase()}]" } }
fun formatSimpleDate(timestamp: Long): String { val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()); return sdf.format(Date(timestamp)) }
fun formatDateRange(start: Long, end: Long): String { return "${formatSimpleDate(start)} - ${formatSimpleDate(end)}" }
fun isSpanningDays(start: Long, end: Long): Boolean { val startCal = Calendar.getInstance().apply { timeInMillis = start }; val endCal = Calendar.getInstance().apply { timeInMillis = end }; return startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) || startCal.get(Calendar.DAY_OF_YEAR) != endCal.get(Calendar.DAY_OF_YEAR) }
fun formatDateTime(timestamp: Long): String { val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); return "${formatDate(timestamp)} ${timeSdf.format(Date(timestamp))}" }
fun formatStartEndRange(start: Long, end: Long?): String { if (end == null) return "Started: ${formatDateTime(start)}"; val startCal = Calendar.getInstance().apply { timeInMillis = start }; val endCal = Calendar.getInstance().apply { timeInMillis = end }; val isSameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR); val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); return if (isSameDay) { "${formatDate(start)} ${timeSdf.format(Date(start))} - ${timeSdf.format(Date(end))}" } else { "${formatDateTime(start)} - ${formatDateTime(end)}" } }
fun formatCardDate(timestamp: Long): String { val now = Calendar.getInstance(); val target = Calendar.getInstance().apply { timeInMillis = timestamp }; if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) { return "" }; val threeDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -3); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis; return if (timestamp >= threeDaysAgo) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp)) } else { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp)) } }
fun getStartOfDay(timestamp: Long): Long { return Calendar.getInstance().apply { timeInMillis = timestamp; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
fun getDayLabel(timestamp: Long): String { val dateInfo = formatCardDate(timestamp); if (dateInfo.isEmpty()) return "Today"; val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }; val target = Calendar.getInstance().apply { timeInMillis = timestamp }; val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR); return if (isYesterday) "Yesterday" else dateInfo }
fun calculatePercentageOfDay(taskDuration: Long, timestamp: Long): String { val percentage = (taskDuration.toDouble() / 86400000.0) * 100.0; val dayLabel = getDayLabel(timestamp); return String.format("%.1f%% of %s", percentage, dayLabel) }
fun calculateOverlapWithToday(start: Long, end: Long, todayStart: Long): Long {
    val effectiveStart = maxOf(start, todayStart)
    val effectiveEnd = maxOf(end, todayStart)
    return if (effectiveEnd > effectiveStart) effectiveEnd - effectiveStart else 0L
}
fun calculateSessionPercentage(segments: List<Task>, activeDuration: Long = 0L, activeTask: Task? = null): String { val dayMillis = 86400000L; val dayDurations = mutableMapOf<Long, Long>(); segments.forEach { segment -> val dayStart = getStartOfDay(segment.startTime); dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + segment.duration }; if (activeTask != null) { val dayStart = getStartOfDay(activeTask.startTime); dayDurations[dayStart] = (dayDurations[dayStart] ?: 0L) + activeDuration }; if (dayDurations.isEmpty()) return "0.0% of Today"; return dayDurations.entries.sortedByDescending { it.key }.joinToString(" - ") { (dayStart, duration) -> val percentage = (duration.toDouble() / dayMillis.toDouble()) * 100.0; val dayLabel = getDayLabel(dayStart); String.format("%.1f%% of %s", percentage, dayLabel) } }
fun showDateTimePicker(context: Context, initialTime: Long, onTimeSelected: (Long) -> Unit) { val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }; DatePickerDialog(context, { _, year, month, day -> TimePickerDialog(context, { _, hour, minute -> val result = Calendar.getInstance().apply { set(year, month, day, hour, minute) }; onTimeSelected(result.timeInMillis) }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }
fun addToGoogleCalendar(context: Context, task: Task) { val googleColorId = when (task.kind) { TaskKind.TOMATO -> 11; TaskKind.TANGERINE -> 6; TaskKind.GRAPHITE -> 8; TaskKind.GRAPE -> 3; TaskKind.BLUEBERRY -> 9; TaskKind.LAVENDER -> 1; TaskKind.PEACOCK -> 7; TaskKind.BANANA -> 5; TaskKind.FLAMINGO -> 4; TaskKind.BASIL -> 10; TaskKind.SAGE -> 2 }; val description = "Type: ${task.kind.displayName}\nDuration: ${formatDetailedDuration(task.duration)}\nTracked via Inventoria Task Tracker\nTask ID: ${task.id}\nSession ID: ${task.groupId}"; val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, task.name).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, task.startTime).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, task.endTime ?: (task.startTime + task.duration)).putExtra(CalendarContract.Events.DESCRIPTION, description).putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY).putExtra("eventColorId", googleColorId.toString()); context.startActivity(intent) }
fun openInSystemCalendar(context: Context, task: Task) { if (task.id.startsWith("cal_")) { val eventId = task.id.removePrefix("cal_").toLongOrNull(); if (eventId != null) { val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId); try { context.startActivity(Intent(Intent.ACTION_VIEW).setData(uri)); return } catch (e: Exception) { Log.e("TaskTracker", "Could not open event $eventId", e) } } }; try { context.startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time/${task.startTime}"))) } catch (e: Exception) { Log.e("TaskTracker", "Could not open calendar", e) } }
