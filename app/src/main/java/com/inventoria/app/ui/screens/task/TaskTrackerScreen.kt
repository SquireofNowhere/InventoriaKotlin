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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.main.Screen
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.util.bucketByDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// Presentation-only: activeSessions is a flat list, but interruptions chain via
// Task.interruptedGroupId (an inner task can itself be paused and interrupted). This walks
// that chain into a depth-first order so the UI can render it as a nested hierarchy instead of
// unrelated-looking flat cards.
private data class ActiveSessionTreeEntry(val session: TaskSessionUI, val depth: Int, val parentName: String?)

private fun buildActiveSessionTree(activeSessions: List<TaskSessionUI>): List<ActiveSessionTreeEntry> {
    fun refTaskOf(session: TaskSessionUI) = session.activeSegment?.task ?: session.segments.firstOrNull()
    val activeGroupIds = activeSessions.map { it.groupId }.toSet()
    val childrenByParentGroupId = activeSessions.groupBy { refTaskOf(it)?.interruptedGroupId }

    val result = mutableListOf<ActiveSessionTreeEntry>()
    fun visit(session: TaskSessionUI, depth: Int, parentName: String?) {
        result.add(ActiveSessionTreeEntry(session, depth, parentName))
        val name = refTaskOf(session)?.name ?: "Untitled"
        childrenByParentGroupId[session.groupId]?.forEach { child -> visit(child, depth + 1, name) }
    }

    activeSessions
        .filter { session -> refTaskOf(session)?.interruptedGroupId?.let { it !in activeGroupIds } != false }
        .forEach { visit(it, 0, null) }
    return result
}

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
    onNavigateToHelp: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToClock: () -> Unit,
    openTaskId: String? = null,
    onOpenTaskIdConsumed: () -> Unit = {}
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
    val pendingInnerTaskPrompt by viewModel.pendingInnerTaskPrompt.collectAsState()
    val pendingInnerTaskRename by viewModel.pendingInnerTaskRename.collectAsState()
    val pendingTodoCompletionCheckIn by viewModel.pendingTodoCompletionCheckIn.collectAsState()
    val isSelectionMode = selectedTaskIds.isNotEmpty()

    val totalScore by viewModel.totalScoreToday.collectAsState()
    val personalScore by viewModel.personalScoreToday.collectAsState()
    val socialScore by viewModel.socialScoreToday.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val taskTypeStats by viewModel.taskTypeStats.collectAsState()
    val taskTypeNames by viewModel.taskTypeNamesById.collectAsState()
    val isRecentFlatView by viewModel.isRecentSessionsFlatView.collectAsState()
    val activityGroups by viewModel.completedActivityGroups.collectAsState()
    var pendingActivityDelete by remember { mutableStateOf<ActivityGroup?>(null) }
    // Discarding a running session is the one destructive action on these cards that cannot be
    // undone by soft-delete semantics alone: the time it is still accruing has never been written
    // anywhere else, so it asks first. The completed cards keep their immediate soft delete.
    var pendingSessionDiscard by remember { mutableStateOf<TaskSessionUI?>(null) }
    var pendingScopedEdit by remember { mutableStateOf<PendingScopedEdit?>(null) }
    val activityFor: (Task) -> ActivityGroup? = { task -> activityGroups.find { it.key == activityKeyOf(task) } }

    val calendarPermissionState = rememberPermissionState(android.Manifest.permission.READ_CALENDAR)
    LaunchedEffect(calendarPermissionState.status.isGranted) { if (calendarPermissionState.status.isGranted) viewModel.refreshCalendar() }
    
    var selectedSessionGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    // A task started from the FAB opens its own details straight away -- see
    // TaskTrackerViewModel.openSessionDetails. currentSelectedSession below resolves null until the
    // new session arrives in activeSessions, then the dialog appears; no waiting needed here.
    LaunchedEffect(Unit) {
        viewModel.openSessionDetails.collect { groupId -> selectedSessionGroupId = groupId }
    }

    // Tapping a task on the Schedule tab hands off here via AppLaunchViewModel's sticky request
    // (see openTaskId's caller) -- open its edit dialog once, then tell the caller to clear the
    // request so switching tabs again later doesn't reopen the same task.
    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            selectedTaskId = openTaskId
            onOpenTaskIdConsumed()
        }
    }
    var showProductivityDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

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

    // "Recent" is the last 24h, keyed off each session's most recent segment so a session that
    // started yesterday but ran into today still counts. Bucketed by day exactly like the History
    // screen (that window can straddle midnight), and dayStats is computed from individual
    // segments even in grouped view, since a session's segments can land on different days.
    val recentSessions = remember(completedSessions, currentTime) {
        completedSessions.filter { session ->
            session.maxByOrNull { it.startTime }?.let { currentTime - it.startTime < 86_400_000 } == true
        }
    }
    val recentFlatTasks = remember(recentSessions) {
        recentSessions.flatten().sortedByDescending { it.startTime }
    }
    val recentFlatDayBuckets = remember(recentFlatTasks) { bucketByDay(recentFlatTasks) { getStartOfDay(it.startTime) } }
    val recentDayStats = remember(recentFlatDayBuckets) { recentFlatDayBuckets.associate { it.dayStart to it.items } }
    // Grouped view works in activities, not sessions: same name + same type share a card even
    // across days, which is what the toggle is for. Bucketed under the day of the most recent
    // sitting, the same way a multi-day session is.
    val recentActivityGroups = remember(activityGroups, currentTime) {
        activityGroups.filter { currentTime - it.mostRecentStartTime < 86_400_000 }
    }
    val recentActivityDayBuckets = remember(recentActivityGroups) {
        bucketByDay(recentActivityGroups) { getStartOfDay(it.mostRecentStartTime) }
    }
    val recentFlatShowTimeByDay = remember(recentFlatDayBuckets) {
        recentFlatDayBuckets.associate { day -> day.dayStart to showTimeFlagsById(day.items) { it.id to it.startTime } }
    }
    // Only single-segment cards get a clock gutter -- CompletedSessionCard has nowhere to put one
    // -- so the dedup chain only tracks those, same as the History screen.
    val recentActivityShowTimeByDay = remember(recentActivityDayBuckets) {
        recentActivityDayBuckets.associate { day ->
            day.dayStart to showTimeFlagsById(day.items.filter { it.segments.size == 1 }) {
                it.segments.first().id to it.segments.first().startTime
            }
        }
    }

    // Raw pool for autofill; the type/recent tiering and filtering happens per-field in
    // buildTaskSuggestions, since each field has its own query text.
    val suggestionSourceTasks = remember(activeSessions, completedSessions) {
        activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) } + completedSessions.flatten()
    }

    val activeSessionTree = remember(activeSessions) { buildActiveSessionTree(activeSessions) }

    // Every delete here is a tombstone kept for DELETED_ROW_RETENTION_MILLIS, so this offers back
    // the one mistake people actually notice -- the one they just made.
    val undoSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.undoPrompts.collect { label ->
            val result = undoSnackbarHostState.showSnackbar(
                message = "Deleted \"$label\"",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(undoSnackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTaskIds.size} Selected") },
                    navigationIcon = { IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, contentDescription = "Clear selection") } },
                    actions = {
                        IconButton(onClick = { viewModel.saveSelectedTasksToCalendar() }) { Icon(Icons.Default.Save, contentDescription = "Save selected to calendar") }
                        IconButton(onClick = { viewModel.deleteSelectedTasks() }) { Icon(Icons.Default.Delete, contentDescription = "Delete selected") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                InventoriaTopBar(
                    title = Screen.Tasks.title,
                    onNavigateToHelp = onNavigateToHelp,
                    // One action and an overflow, not four actions. This screen has by far the
                    // most to offer of any tab, and four 48dp buttons plus the sync glyph and the
                    // help button left a centred "Task Tracker" under 100dp to render 22sp type
                    // in -- permanently ellipsized on a 360dp phone. The split is by kind: the
                    // calendar refresh acts on this screen and stays out; Timers, Stats and
                    // History are all "go to another screen" and go in the menu, where they get
                    // real labels instead of a glyph you have to recognise.
                    actions = {
                        IconButton(onClick = { if (calendarPermissionState.status.isGranted) viewModel.refreshCalendar() else calendarPermissionState.launchPermissionRequest() }) { Icon(Icons.Default.Sync, contentDescription = "Refresh calendar") }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Timers & alarms") },
                                    leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onNavigateToClock() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Productivity stats") },
                                    leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onNavigateToStats() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Task history") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onNavigateToHistory() }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && activeSessions.size < 5) {
                FloatingActionButton(onClick = { viewModel.addNewTask(openDetails = true) }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Start a new task") }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val todayStart = getStartOfDay(currentTime)
                    val allTodayTasks = (completedSessions.flatten() + activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) })
                        .filter { (it.endTime ?: currentTime) >= todayStart }

                    DailyScoreCard(
                        totalScore = totalScore,
                        personalScore = personalScore,
                        socialScore = socialScore,
                        tasks = allTodayTasks,
                        currentTime = currentTime,
                        onClick = { showProductivityDialog = true }
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
                    items(activeSessionTree, key = { it.session.groupId }) { entry ->
                        val session = entry.session
                        ActiveSessionCard(
                            session = session,
                            currentTime = currentTime,
                            suggestionSourceTasks = suggestionSourceTasks,
                            taskTypes = taskTypes,
                            taskTypeStats = taskTypeStats,
                            isFlowModeEnabled = isFlowModeEnabled,
                            depth = entry.depth,
                            parentName = entry.parentName,
                            onStop = { viewModel.stopTask(session) },
                            onPauseResume = { viewModel.pauseResumeTask(session) },
                            onDiscard = { pendingSessionDiscard = session },
                            onUpdateName = { viewModel.updateSessionName(session.groupId, it) },
                            onAutocompleteSelect = { name, kind, typeId -> viewModel.applyRecentSuggestion(session.groupId, name, kind, typeId) },
                            onTaskTypeSelect = { typeId -> viewModel.applyTaskTypeSuggestion(session.groupId, typeId) },
                            // Explicit pick, unlike the autofill path above: label only, no Kind
                            // prefill -- the user is correcting the type, not restarting the task.
                            onTaskTypeChange = { typeId -> viewModel.updateSessionTaskType(session.groupId, typeId) },
                            onUpdateKind = { kind ->
                                // Only the segment actually shown in this card -- the running
                                // one, or the most recent paused one if nothing's running --
                                // not the whole session's history (see ErrorLog.md #18).
                                (session.activeSegment?.task ?: session.segments.firstOrNull())?.let {
                                    viewModel.updateSegmentKind(it.id, kind)
                                }
                            },
                            onSessionClick = { selectedSessionGroupId = session.groupId },
                            onEditTask = { selectedTaskId = it },
                            onToggleStreak = { task, enabled -> viewModel.setInnerTaskCountsForStreak(task, enabled) }
                        )
                    }
                }
                if (recentSessions.isNotEmpty() || recentActivityGroups.isNotEmpty()) {
                    item {
                        // Same two views the History screen offers, over the last 24h instead of
                        // the whole record: sessions as cards, or every segment in the order it
                        // actually happened. The choice persists under its own key.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.setRecentSessionsFlatView(!isRecentFlatView) }) {
                                Icon(
                                    if (isRecentFlatView) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.List,
                                    contentDescription = if (isRecentFlatView) "Switch to Grouped View" else "Switch to Flat View",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (isRecentFlatView) {
                        recentFlatDayBuckets.forEach { day ->
                            item(key = "recent_day_${day.dayStart}") { DayTimelineHeader(day.dayStart, day.items) }
                            val showTimeById = recentFlatShowTimeByDay[day.dayStart] ?: emptyMap()
                            items(day.items, key = { "recent_flat_${it.id}" }) { task ->
                                TimelineTaskRow(
                                    task = task,
                                    isSelected = task.id in selectedTaskIds,
                                    taskTypeNames = taskTypeNames,
                                    showTime = showTimeById[task.id] != false,
                                    onClick = { if (isSelectionMode) viewModel.toggleTaskSelection(task.id) else selectedTaskId = task.id },
                                    onLongClick = { viewModel.toggleTaskSelection(task.id) },
                                    onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                    onDelete = { viewModel.deleteSegment(task) },
                                    onAddToCalendar = { addToGoogleCalendar(context, task) }
                                )
                            }
                        }
                    } else {
                        recentActivityDayBuckets.forEach { day ->
                            item(key = "recent_sday_${day.dayStart}") {
                                DayTimelineHeader(day.dayStart, recentDayStats[day.dayStart] ?: emptyList())
                            }
                            val showTimeById = recentActivityShowTimeByDay[day.dayStart] ?: emptyMap()
                            items(day.items, key = { "recent_activity_${it.key.name}_${it.key.taskTypeId}" }) { group ->
                                // One sitting of one segment is still just a row; anything that
                                // spans more than that earns a card, whether the "more" is extra
                                // segments or extra sittings of the same activity.
                                if (group.segments.size > 1) {
                                    CompletedSessionCard(
                                        segments = group.segments,
                                        currentTime = currentTime,
                                        selectedTaskIds = selectedTaskIds,
                                        taskTypeNames = taskTypeNames,
                                        sessionCount = group.sessionCount,
                                        onClick = { selectedSessionGroupId = group.groupIds.first() },
                                        onDelete = {
                                            if (group.sessionCount > 1) pendingActivityDelete = group
                                            else viewModel.deleteSession(group.groupIds.first())
                                        },
                                        onSegmentLongClick = { viewModel.toggleTaskSelection(it.id) },
                                        onSegmentClick = {
                                            if (isSelectionMode) viewModel.toggleTaskSelection(it.id)
                                            else selectedTaskId = it.id
                                        },
                                        onSegmentDelete = { viewModel.deleteSegment(it) },
                                        onSegmentToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
                                        onHideCalendarItem = { group.segments.forEach { viewModel.hideCalendarTask(it) } }
                                    )
                                } else {
                                    val task = group.segments.first()
                                    TimelineTaskRow(
                                        task = task,
                                        isSelected = task.id in selectedTaskIds,
                                        taskTypeNames = taskTypeNames,
                                        showTime = showTimeById[task.id] != false,
                                        onClick = { if (isSelectionMode) viewModel.toggleTaskSelection(task.id) else selectedTaskId = task.id },
                                        onLongClick = { viewModel.toggleTaskSelection(task.id) },
                                        onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                        onDelete = { viewModel.deleteSegment(task) },
                                        onAddToCalendar = { addToGoogleCalendar(context, task) },
                                        onHideCalendarItem = { viewModel.hideCalendarTask(task) }
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    if (showProductivityDialog) {
        val todayStart = getStartOfDay(currentTime)
        val allTodayTasks = (completedSessions.flatten() + activeSessions.flatMap { it.segments + listOfNotNull(it.activeSegment?.task) })
            .filter { (it.endTime ?: currentTime) >= todayStart }

        DailyProductivityDialog(
            tasks = allTodayTasks,
            totalScore = totalScore,
            personalScore = personalScore,
            socialScore = socialScore,
            onDismiss = { showProductivityDialog = false },
            dampen = { raw -> viewModel.previewDampen(raw) }
        )
    }

    currentSelectedSession?.let { segments ->
        SessionDetailDialog(
            segments = segments, taskTypes = taskTypes,
            taskTypeStats = taskTypeStats, suggestionSourceTasks = suggestionSourceTasks,
            onDismiss = { selectedSessionGroupId = null },
            onUpdateSessionName = { name ->
                scopedEdit(
                    group = activityFor(segments.first()),
                    description = "Rename to \"$name\"",
                    applyAll = { ids -> viewModel.updateActivityName(ids, name) },
                    applyOne = { viewModel.updateSessionName(segments.first().groupId, name) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onUpdateSessionKind = { kind ->
                scopedEdit(
                    group = activityFor(segments.first()),
                    description = "Change Kind to ${kind.displayName.split(" • ").last()}",
                    applyAll = { ids -> viewModel.updateActivityKind(ids, kind) },
                    applyOne = { viewModel.updateSessionKind(segments.first().groupId, kind) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onUpdateSessionTaskType = { typeId ->
                scopedEdit(
                    group = activityFor(segments.first()),
                    description = "Change type to ${typeId?.let { taskTypeNames[it] } ?: "no type"}",
                    applyAll = { ids -> viewModel.updateActivityTaskType(ids, typeId) },
                    applyOne = { viewModel.updateSessionTaskType(segments.first().groupId, typeId) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { ids -> viewModel.flattenSession(segments.first().groupId, ids) },
            onNavigateToTaskDetail = { selectedTaskId = it }, // Bypassing route, using dialog
            onDeleteSegment = { viewModel.deleteSegment(it) }
        )
    }

    currentSelectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            taskTypes = taskTypes,
            taskTypeStats = taskTypeStats,
            suggestionSourceTasks = suggestionSourceTasks,
            onDismiss = { selectedTaskId = null },
            // Segment-scoped by default (this dialog edits one segment), with "change all"
            // reaching every sitting of the activity -- the two ends of the ladder. Retagging the
            // middle rung, one whole session, is what the session dialog is for.
            onSaveName = { name ->
                scopedEdit(
                    group = activityFor(task),
                    description = "Rename to \"$name\"",
                    applyAll = { ids -> viewModel.updateActivityName(ids, name) },
                    applyOne = { viewModel.updateCompletedTaskName(task, name) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onKindChange = { kind ->
                scopedEdit(
                    group = activityFor(task),
                    description = "Change Kind to ${kind.displayName.split(" • ").last()}",
                    applyAll = { ids -> viewModel.updateActivityKind(ids, kind) },
                    applyOne = { viewModel.updateCompletedTaskKind(task, kind) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onTaskTypeChange = { typeId ->
                scopedEdit(
                    group = activityFor(task),
                    description = "Change type to ${typeId?.let { taskTypeNames[it] } ?: "no type"}",
                    applyAll = { ids -> viewModel.updateActivityTaskType(ids, typeId) },
                    applyOne = { viewModel.updateCompletedTaskType(task, typeId) },
                    setPending = { pendingScopedEdit = it }
                )
            },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, it) },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) },
            onDelete = { viewModel.deleteSegment(task); selectedTaskId = null },
            previewScore = { kind, durationMs -> viewModel.previewScore(kind, durationMs) },
            onSplit = { splitTime, secondName, secondKind, secondTypeId -> viewModel.splitSegment(task, splitTime, secondName, secondKind, secondTypeId) },
            nextTaskName = viewModel.nextTaskName
        )
    }

    pendingScopedEdit?.let { pending ->
        ScopedEditPrompt(pending = pending, onDismiss = { pendingScopedEdit = null })
    }

    pendingActivityDelete?.let { group ->
        EditScopeDialog(
            title = "Delete \"${group.displayName}\"?",
            message = "This card covers ${group.sessionCount} separate sittings. Deleting all of " +
                "them removes every segment from each one.",
            allLabel = "Delete all ${group.sessionCount}",
            oneLabel = "Delete the most recent only",
            destructive = true,
            onAll = { viewModel.deleteSessions(group.groupIds) },
            onOne = { viewModel.deleteSession(group.groupIds.first()) },
            onDismiss = { pendingActivityDelete = null }
        )
    }

    pendingSessionDiscard?.let { session ->
        val refTask = session.activeSegment?.task ?: session.segments.firstOrNull()
        AlertDialog(
            onDismissRequest = { pendingSessionDiscard = null },
            title = { Text("Discard \"${refTask?.name.orEmpty().ifBlank { "this session" }}\"?") },
            text = {
                Text(
                    "This session is still running. Discarding throws away every segment of it, " +
                        "including the time it has tracked so far — none of it is scored or kept. " +
                        "To keep the record instead, use Stop."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.discardSession(session)
                        pendingSessionDiscard = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSessionDiscard = null }) { Text("Cancel") }
            }
        )
    }

    pendingInnerTaskPrompt?.let { groupId ->
        AlertDialog(
            onDismissRequest = { viewModel.respondToInnerTaskPrompt(enable = false, pausedGroupId = groupId) },
            title = { Text("Track Interruptions?") },
            text = { Text("When pausing a task, another inner task will start running to make sure you keep track of exactly what interrupted you when you resume. You can change this later in Settings.") },
            confirmButton = {
                TextButton(onClick = { viewModel.respondToInnerTaskPrompt(enable = true, pausedGroupId = groupId) }) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondToInnerTaskPrompt(enable = false, pausedGroupId = groupId) }) {
                    Text("Not Now")
                }
            }
        )
    }

    pendingInnerTaskRename?.let { innerTask ->
        var interruptionName by remember(innerTask.id) {
            mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(innerTask.name, androidx.compose.ui.text.TextRange(0, innerTask.name.length)))
        }
        var countsForStreak by remember(innerTask.id) { mutableStateOf(innerTask.countsForStreak) }
        var interruptionKind by remember(innerTask.id) { mutableStateOf(innerTask.kind) }
        var interruptionTypeId by remember(innerTask.id) { mutableStateOf(innerTask.taskTypeId) }
        var isNameFocused by remember(innerTask.id) { mutableStateOf(false) }
        var dropdownDismissedByUser by remember(innerTask.id) { mutableStateOf(false) }
        val innerTaskFocusRequester = remember { FocusRequester() }
        val innerTaskKeyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(innerTask.id) {
            delay(100)
            innerTaskFocusRequester.requestFocus()
            innerTaskKeyboardController?.show()
        }
        LaunchedEffect(interruptionName.text) { dropdownDismissedByUser = false }
        val filteredInnerTaskSuggestions = remember(interruptionName.text, isNameFocused, dropdownDismissedByUser, taskTypes, taskTypeStats, suggestionSourceTasks) {
            if (!isNameFocused || dropdownDismissedByUser) emptyList()
            else buildTaskSuggestions(interruptionName.text, taskTypes, taskTypeStats, suggestionSourceTasks)
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissInnerTaskRenameDialog() },
            title = { Text("What's interrupting you?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tracking: ${formatTime(currentTime - innerTask.startTime)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = interruptionName,
                            onValueChange = { interruptionName = it },
                            label = { Text("e.g. Get Water") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(innerTaskFocusRequester)
                                .onFocusChanged { isNameFocused = it.isFocused }
                        )
                        DropdownMenu(
                            expanded = filteredInnerTaskSuggestions.isNotEmpty(),
                            onDismissRequest = { dropdownDismissedByUser = true },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            filteredInnerTaskSuggestions.forEach { suggestion ->
                                when (suggestion) {
                                    is TaskSuggestion.Type -> DropdownMenuItem(
                                        text = { TaskTypeSuggestionLabel(suggestion) },
                                        onClick = {
                                            interruptionName = androidx.compose.ui.text.input.TextFieldValue(suggestion.label, androidx.compose.ui.text.TextRange(suggestion.label.length))
                                            interruptionTypeId = suggestion.typeId
                                            suggestion.mostUsedKind?.let { interruptionKind = it }
                                            dropdownDismissedByUser = true
                                        }
                                    )
                                    is TaskSuggestion.Recent -> DropdownMenuItem(
                                        text = { RecentSuggestionLabel(suggestion) },
                                        onClick = {
                                            interruptionName = androidx.compose.ui.text.input.TextFieldValue(suggestion.label, androidx.compose.ui.text.TextRange(suggestion.label.length))
                                            interruptionKind = suggestion.kind
                                            // Same rule as the session path: inherit the name's
                                            // settled type when it has one, never clear on null.
                                            suggestion.typeId?.let { interruptionTypeId = it }
                                            dropdownDismissedByUser = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TaskKindDropdownMenu(selectedKind = interruptionKind, onKindSelected = { interruptionKind = it })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Count as a streak break",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = countsForStreak, onCheckedChange = { countsForStreak = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameInnerTask(innerTask, interruptionName.text, countsForStreak, interruptionKind, interruptionTypeId) }) {
                    Text("Save")
                }
            }
        )
    }

    pendingTodoCompletionCheckIn?.let { todo ->
        AlertDialog(
            onDismissRequest = { viewModel.respondToTodoCompletionCheckIn(false) },
            title = { Text("Finished?") },
            text = { Text("Is \"${todo.title}\" complete, or still ongoing?") },
            confirmButton = {
                TextButton(onClick = { viewModel.respondToTodoCompletionCheckIn(true) }) {
                    Text("Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondToTodoCompletionCheckIn(false) }) {
                    Text("Still Ongoing")
                }
            }
        )
    }
}

@Composable
fun DailyScoreCard(totalScore: Int, personalScore: Int, socialScore: Int, tasks: List<Task>, currentTime: Long, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ProductivityPieChart(tasks = tasks, currentTime = currentTime, modifier = Modifier.size(80.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Today's Productivity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(text = "$totalScore pts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    task: Task,
    isSelected: Boolean = false,
    taskTypeNames: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit,
    onHideCalendarItem: () -> Unit = {}
) {
    val context = LocalContext.current
    val taskColor = Color(task.kind.colorValue)
    val isCalendarTask = task.id.startsWith("cal_")
    val percentage = calculatePercentageOfDay(task.duration, task.startTime)
    val backgroundColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primaryContainer else taskColor.copy(alpha = 0.1f))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left color bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else taskColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val typeName = task.taskTypeId?.let { taskTypeNames[it] }
                    if (typeName != null) TaskTypeLabel(typeName)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.originTodoId != null) {
                            Icon(Icons.Default.Checklist, contentDescription = "From a Todo", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "${formatDetailedDuration(task.duration)} • $percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (task.score >= 0) "+${task.score} pts" else "${task.score} pts",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (task.score >= 0) Success else Color(0xFFFF4D4D)
                    )
                }
                TaskKindChip(kind = task.kind)
                Spacer(Modifier.width(4.dp))
                if (isCalendarTask) {
                    IconButton(onClick = { openInSystemCalendar(context, task) }) {
                        Icon(Icons.Default.EventAvailable, "Open in Calendar", tint = Success, modifier = Modifier.size(20.dp))
                    }
                    // Same trailing slot as the delete on a normal card, and deliberately not the
                    // same icon or wording: this row is a view of a system calendar event, which
                    // the app reads and does not own. Dismissing takes it off this list only.
                    IconButton(onClick = onHideCalendarItem) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Remove from Inventoria (keeps the calendar event)", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = {
                        if (!task.savedToCalendar) onAddToCalendar()
                        onToggleCalendar()
                    }) {
                        Icon(
                            if (task.savedToCalendar) Icons.Default.EventAvailable else Icons.Outlined.CalendarToday,
                            contentDescription = if (task.savedToCalendar) "Saved" else "Add to Calendar",
                            tint = if (task.savedToCalendar) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedSessionCard(
    segments: List<Task>,
    currentTime: Long,
    selectedTaskIds: Set<String>,
    taskTypeNames: Map<String, String> = emptyMap(),
    /** >1 when this card stands for several sittings of one activity rather than a single
     * session -- see ActivityGroup. Shown in the meta line so a card covering a week of lunches
     * can't be mistaken for one very long one. */
    sessionCount: Int = 1,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSegmentClick: (Task) -> Unit,
    onSegmentLongClick: (Task) -> Unit,
    onSegmentDelete: (Task) -> Unit,
    onSegmentToggleCalendar: (Task) -> Unit,
    onHideCalendarItem: () -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val majorityKind = segments.groupBy { it.kind }.maxByOrNull { it.value.size }?.key ?: segments.first().kind
    // Same majority rule the Kind above already uses: segments of one session can carry different
    // types (an interruption gets its own), and the header summarises the session, not any one
    // segment. Untyped segments don't get a vote -- expand the session to see them individually.
    val majorityTypeName = segments.mapNotNull { it.taskTypeId?.let { id -> taskTypeNames[id] } }
        .groupBy { it }.maxByOrNull { it.value.size }?.key
    val sessionName = segments.firstOrNull { it.isNameCustom }?.name ?: segments.firstOrNull()?.name ?: "Untitled Session"
    val todayStart = getStartOfDay(currentTime)
    val todayDuration = segments.sumOf { calculateOverlapWithToday(it.startTime, it.endTime ?: (it.startTime + it.duration), todayStart) }
    val taskColor = Color(majorityKind.colorValue)
    val percentage = calculatePercentageOfDay(todayDuration, todayStart)
    val allSaved = segments.all { it.savedToCalendar }
    val someSaved = segments.any { it.savedToCalendar }
    val isCalendarSession = segments.any { it.id.startsWith("cal_") }
    val hasTodoOrigin = segments.any { it.originTodoId != null }
    val sessionScore = segments.sumOf { it.score }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(taskColor)
                        .clickable { onClick() }
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).clickable { onClick() }) {
                    majorityTypeName?.let { TaskTypeLabel(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasTodoOrigin) {
                            Icon(Icons.Default.Checklist, contentDescription = "From a Todo", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(text = sessionName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = (if (sessionCount > 1) "$sessionCount sittings • " else "") +
                            "${segments.size} segments • ${formatDetailedDuration(todayDuration)} • $percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (sessionScore >= 0) "+$sessionScore pts" else "$sessionScore pts",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (sessionScore >= 0) Success else Color(0xFFFF4D4D)
                    )
                    if (allSaved && !isCalendarSession) {
                        val latestSaveAt = segments.mapNotNull { it.savedToCalendarAt }.maxOrNull() ?: 0L
                        val remaining = 86400000 - (currentTime - latestSaveAt)
                        if (remaining > 0) {
                            Text(text = "Auto-delete in: ${formatDetailedDuration(remaining)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TaskKindChip(kind = majorityKind)
                Spacer(Modifier.width(4.dp))
                if (isCalendarSession) {
                    IconButton(onClick = { openInSystemCalendar(context, segments.first()) }) {
                        Icon(Icons.Default.EventAvailable, contentDescription = "Open in Calendar", tint = Success, modifier = Modifier.size(20.dp))
                    }
                    // See SingleTaskItemCard: a calendar-sourced card is dismissed, not deleted.
                    IconButton(onClick = onHideCalendarItem) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Remove from Inventoria (keeps the calendar event)", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                } else {
                    if (allSaved) Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(20.dp), tint = Success)
                    else if (someSaved) Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(20.dp), tint = PurplePrimary)
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = taskColor.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 2.dp))
                    segments.sortedByDescending { it.startTime }.forEach { segment ->
                        SegmentRow(
                            segment = segment,
                            isSelected = segment.id in selectedTaskIds,
                            typeName = segment.taskTypeId?.let { taskTypeNames[it] },
                            onClick = { onSegmentClick(segment) },
                            onLongClick = { onSegmentLongClick(segment) },
                            onToggleCalendar = { onSegmentToggleCalendar(segment) },
                            onAddToCalendar = { addToGoogleCalendar(context, segment) },
                            onDelete = { onSegmentDelete(segment) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * An edit made from a detail dialog that could reasonably mean "this sitting" or "this activity",
 * held until the user says which. [applyOne] is the dialog's original per-session (or per-segment)
 * behaviour; [applyAll] is the same edit across every sitting of the activity.
 */
data class PendingScopedEdit(
    val activityName: String,
    val sessionCount: Int,
    val description: String,
    val applyAll: () -> Unit,
    val applyOne: () -> Unit
)

/**
 * Routes a detail-dialog edit through the scope prompt when -- and only when -- the thing being
 * edited is part of an activity spanning several sittings. A one-sitting activity has nothing to
 * disambiguate, so it just applies, and an edit to a still-running session never prompts: its
 * activity grouping is about completed history, and quietly offering to rewrite that mid-session
 * is more surprising than useful.
 */
fun scopedEdit(
    group: ActivityGroup?,
    description: String,
    applyAll: (List<String>) -> Unit,
    applyOne: () -> Unit,
    setPending: (PendingScopedEdit?) -> Unit
) {
    if (group == null || group.sessionCount <= 1) {
        applyOne()
        return
    }
    setPending(
        PendingScopedEdit(
            activityName = group.displayName,
            sessionCount = group.sessionCount,
            description = description,
            applyAll = { applyAll(group.groupIds) },
            applyOne = applyOne
        )
    )
}

@Composable
fun ScopedEditPrompt(pending: PendingScopedEdit, onDismiss: () -> Unit) {
    EditScopeDialog(
        title = pending.description,
        message = "\"${pending.activityName}\" covers ${pending.sessionCount} sittings. Apply this " +
            "to all of them, or only to the one you opened?",
        allLabel = "Change all ${pending.sessionCount}",
        oneLabel = "Just this one",
        onAll = pending.applyAll,
        onOne = pending.applyOne,
        onDismiss = onDismiss
    )
}

/**
 * Three-way scope prompt for an action on a card that stands for several sittings: apply it to all
 * of them, to this one only, or not at all.
 *
 * Exists because grouping sessions by activity means one tap can now reach work from other days.
 * Nothing that spans sittings happens without passing through here.
 */
@Composable
fun EditScopeDialog(
    title: String,
    message: String,
    allLabel: String,
    oneLabel: String,
    destructive: Boolean = false,
    onAll: () -> Unit,
    onOne: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onAll(); onDismiss() }) {
                    Text(allLabel, color = accent, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onOne(); onDismiss() }) { Text(oneLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SegmentRow(
    segment: Task,
    isSelected: Boolean,
    typeName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onAddToCalendar: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val segmentColor = Color(segment.kind.colorValue)
    val isCalendarTask = segment.id.startsWith("cal_")
    val percentage = calculatePercentageOfDay(segment.duration, segment.startTime)
    val backgroundColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primaryContainer else segmentColor.copy(alpha = 0.08f))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else segmentColor.copy(alpha = 0.7f)))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                typeName?.let { TaskTypeLabel(it, iconSize = 10.dp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (segment.originTodoId != null) {
                        Icon(Icons.Default.Checklist, contentDescription = "From a Todo", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(text = segment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "${formatDetailedDuration(segment.duration)} • $percentage • ${if (segment.score >= 0) "+${segment.score} pts" else "${segment.score} pts"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TaskKindChip(kind = segment.kind, modifier = Modifier.scale(0.85f))
            Spacer(Modifier.width(2.dp))
            if (isCalendarTask) {
                IconButton(onClick = { openInSystemCalendar(context, segment) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.EventAvailable, "Open in Calendar", tint = Success, modifier = Modifier.size(18.dp))
                }
            } else {
                IconButton(onClick = { if (!segment.savedToCalendar) onAddToCalendar(); onToggleCalendar() }, modifier = Modifier.size(32.dp)) {
                    Icon(if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Outlined.CalendarToday, null, tint = if (segment.savedToCalendar) Success else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


@Composable
fun ActiveSessionCard(session: TaskSessionUI, currentTime: Long, suggestionSourceTasks: List<Task>, taskTypes: List<TaskType>, taskTypeStats: Map<String, TaskTypeStats>, isFlowModeEnabled: Boolean, depth: Int = 0, parentName: String? = null, onStop: () -> Unit, onPauseResume: () -> Unit, onDiscard: () -> Unit, onUpdateName: (String) -> Unit, onAutocompleteSelect: (String, TaskKind, String?) -> Unit, onTaskTypeSelect: (String) -> Unit, onTaskTypeChange: (String?) -> Unit, onUpdateKind: (TaskKind) -> Unit, onSessionClick: () -> Unit, onEditTask: (String) -> Unit, onToggleStreak: (Task, Boolean) -> Unit) {
    val isExpanded by session.isExpanded.collectAsState(); val activeSegment = session.activeSegment; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; val activeElapsed by (activeSegment?.elapsedTime?.collectAsState() ?: remember { mutableStateOf(0L) }); val refTask = activeSegment?.task ?: session.segments.firstOrNull() ?: return; 
    
    val todayStart = getStartOfDay(currentTime)
    // MIDNIGHT BLEED: Active summary only shows time that happened TODAY
    val todaySegmentsDuration = session.segments.sumOf {calculateOverlapWithToday(it.startTime, it.endTime ?: (it.startTime + it.duration), todayStart)}
    val todayActiveElapsed = if (activeSegment != null) calculateOverlapWithToday(activeSegment.task.startTime, currentTime, todayStart) else 0L
    
    val totalTimeToday = todaySegmentsDuration + todayActiveElapsed
    val percentage = calculatePercentageOfDay(totalTimeToday, todayStart)
    
    val sessionName = session.segments.firstOrNull { !it.isNameCustom }?.name ?: activeSegment?.task?.name ?: session.segments.firstOrNull()?.name ?: "Untitled"; var editableName by remember(sessionName) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(sessionName, if (sessionName.startsWith("Task ")) androidx.compose.ui.text.TextRange(0, sessionName.length) else androidx.compose.ui.text.TextRange(sessionName.length))) }; var isFocused by remember { mutableStateOf(false) }; var dropdownDismissedByUser by remember { mutableStateOf(false) };
    // Picking a suggestion clears focus, which fires the field's own save-on-blur. That made two
    // writers race for the same name off one tap -- and the blur handler reads the text field
    // state as of the last composition, so it could still be mid-typing ("tes") while the pick
    // wrote the full label ("testing"), with whichever landed second winning. The pick is the
    // authoritative write; this makes the blur that immediately follows it stand down.
    var suggestionJustApplied by remember { mutableStateOf(false) }; LaunchedEffect(editableName.text) { dropdownDismissedByUser = false }; val filteredSuggestions = remember(editableName.text, isFocused, dropdownDismissedByUser, taskTypes, taskTypeStats, suggestionSourceTasks) { if (!isFocused || dropdownDismissedByUser) emptyList() else buildTaskSuggestions(editableName.text, taskTypes, taskTypeStats, suggestionSourceTasks) }; val taskColor = Color(refTask.kind.colorValue)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(session.groupId, activeSegment?.task?.id) { if (sessionName.startsWith("Task ") && activeSegment?.task?.isRunning == true) { delay(100); focusRequester.requestFocus(); keyboardController?.show() } }
    Card(modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = taskColor.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (depth > 0 && parentName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(Icons.Filled.SubdirectoryArrowRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Interrupting $parentName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Doubles as the picker: reading and setting the type are the same control, off the
            // type list this card already receives for its autofill dropdown (so picking a type
            // suggestion relabels it immediately too). Session-scoped, the same scope the autofill
            // path writes at -- an interruption is its own session, so it types independently.
            TaskTypeDropdownMenu(
                selectedTypeId = refTask.taskTypeId,
                taskTypes = taskTypes,
                onTypeSelected = onTaskTypeChange,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (refTask.originTodoId != null) {
                    Icon(Icons.Default.Checklist, contentDescription = "From a Todo", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(value = editableName, onValueChange = { editableName = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focusState -> isFocused = focusState.isFocused; if (!focusState.isFocused) { if (suggestionJustApplied) suggestionJustApplied = false else if (editableName.text != sessionName) onUpdateName(editableName.text) } }, textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }), singleLine = true)
                        DropdownMenu(expanded = filteredSuggestions.isNotEmpty(), onDismissRequest = { dropdownDismissedByUser = true }, properties = PopupProperties(focusable = false), modifier = Modifier.fillMaxWidth(0.8f)) {
                            filteredSuggestions.forEach { suggestion ->
                                when (suggestion) {
                                    is TaskSuggestion.Type -> DropdownMenuItem(
                                        text = { TaskTypeSuggestionLabel(suggestion) },
                                        onClick = {
                                            // Types keep the keyboard up: the type is the broad
                                            // activity and the user is expected to carry on typing
                                            // the specific name ("Eating" -> "Eating with V").
                                            editableName = androidx.compose.ui.text.input.TextFieldValue(suggestion.label, androidx.compose.ui.text.TextRange(suggestion.label.length))
                                            dropdownDismissedByUser = true
                                            onTaskTypeSelect(suggestion.typeId)
                                        }
                                    )
                                    is TaskSuggestion.Recent -> DropdownMenuItem(
                                        text = { RecentSuggestionLabel(suggestion) },
                                        onClick = {
                                            editableName = androidx.compose.ui.text.input.TextFieldValue(suggestion.label, androidx.compose.ui.text.TextRange(suggestion.label.length))
                                            // Write first, then blur: the blur below stands down
                                            // (see suggestionJustApplied) so this is the only
                                            // write, and it carries the full label rather than
                                            // whatever the field happened to hold.
                                            onAutocompleteSelect(suggestion.label, suggestion.kind, suggestion.typeId)
                                            suggestionJustApplied = true
                                            focusManager.clearFocus(); keyboardController?.hide()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onEditTask(refTask.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit task", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onSessionClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
            }
            Spacer(modifier = Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { TaskKindDropdownMenu(selectedKind = refTask.kind, onKindSelected = onUpdateKind); if (session.segments.isNotEmpty()) { IconButton(onClick = { session.isExpanded.value = !isExpanded }, modifier = Modifier.size(24.dp)) { Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray) } } }
            Spacer(modifier = Modifier.height(16.dp)); Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(text = formatTime(totalTimeToday), color = if (taskColor == Color.White) Color.Black else taskColor, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text(text = percentage, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    if (activeSegment == null) { Text(text = "PAUSED", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 12.dp)) }
                    IconButton(onClick = onPauseResume, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)) { Icon(if (activeSegment != null) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(8.dp))
                    if (isFlowModeEnabled && refTask.interruptedGroupId == null) {
                        TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary), modifier = Modifier.height(40.dp)) {
                            Text("Stop & Continue", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onStop, modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)) {
                            Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Trailing destructive action, same slot the two completed cards put theirs in.
                    // Stop keeps the session; this throws it away, which is why it is the one action
                    // on these cards that asks first.
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDiscard, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Discard this session", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (refTask.interruptedGroupId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Count as a streak break", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Switch(
                        checked = refTask.countsForStreak,
                        onCheckedChange = { onToggleStreak(refTask, it) },
                        modifier = Modifier.scale(0.7f)
                    )
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
    segments: List<Task>, taskTypes: List<TaskType>,
    taskTypeStats: Map<String, TaskTypeStats>, suggestionSourceTasks: List<Task>,
    onDismiss: () -> Unit,
    onUpdateSessionName: (String) -> Unit, onUpdateSessionKind: (TaskKind) -> Unit,
    onUpdateSessionTaskType: (String?) -> Unit,
    onToggleCalendar: (Task) -> Unit,
    onFlatten: (Set<String>) -> Unit, onNavigateToTaskDetail: (String) -> Unit,
    onDeleteSegment: (Task) -> Unit
) {
    val sessionRef = segments.first(); var sessionNameInput by remember { mutableStateOf(sessionRef.name) }; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; var showFlattenPicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { focusManager.clearFocus(); if (sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput); onDismiss() },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(8.dp)); Text("Session Details") } },
        text = {
            Column(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Same autofill dropdown as every other naming field; label-only picks for the
                // same reason as TaskDetailDialog -- kind/type pickers sit right below, behind
                // the scoped-edit prompt.
                TaskNameAutofillField(
                    value = sessionNameInput,
                    onValueChange = { sessionNameInput = it },
                    label = "Session Name",
                    taskTypes = taskTypes,
                    taskTypeStats = taskTypeStats,
                    suggestionSourceTasks = suggestionSourceTasks,
                    fieldModifier = Modifier.onFocusChanged { if (!it.isFocused && sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() })
                )
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Session Category: ", style = MaterialTheme.typography.bodySmall); TaskKindDropdownMenu(selectedKind = sessionRef.kind, onKindSelected = onUpdateSessionKind) }
                // Whole-session, unlike TaskDetailDialog's per-segment picker: this dialog edits
                // the session, so retyping here retypes every segment in it. Anchored on the first
                // segment's type, which is what the card header summarises.
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Session Type: ", style = MaterialTheme.typography.bodySmall); TaskTypeDropdownMenu(selectedTypeId = sessionRef.taskTypeId, taskTypes = taskTypes, onTypeSelected = onUpdateSessionTaskType) }
                HorizontalDivider(); Text("Segments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                segments.sortedByDescending { it.startTime }.forEach { segment ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onNavigateToTaskDetail(segment.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(segment.kind.colorValue))); Spacer(Modifier.width(8.dp))
                            Text(text = segment.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            Text(
                                text = if (segment.score >= 0) "+${segment.score} pts" else "${segment.score} pts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (segment.score >= 0) Success else Color(0xFFFF4D4D),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            if (segment.id.startsWith("cal_")) {
                                Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(16.dp), tint = Color(0xFF4285F4))
                            } else {
                                IconButton(onClick = { onToggleCalendar(segment) }, modifier = Modifier.size(24.dp)) { Icon(if (segment.savedToCalendar) Icons.Default.EventAvailable else Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = if (segment.savedToCalendar) Success else PurplePrimary) }
                                IconButton(onClick = { onDeleteSegment(segment) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Text(text = "${formatDetailedDuration(segment.duration)} \u2022 ${formatStartEndRange(segment.startTime, segment.endTime)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                if (segments.size > 1) { TextButton(onClick = { showFlattenPicker = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Merge, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Flatten segments…") } }
            }
        },
        confirmButton = { TextButton(onClick = { focusManager.clearFocus(); if (sessionNameInput != sessionRef.name) onUpdateSessionName(sessionNameInput); onDismiss() }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { focusManager.clearFocus(); onDismiss() }) { Text("Cancel") } }
    )
    if (showFlattenPicker) {
        FlattenSegmentsDialog(
            segments = segments,
            onDismiss = { showFlattenPicker = false },
            onFlatten = { ids -> onFlatten(ids); showFlattenPicker = false; onDismiss() }
        )
    }
}

/**
 * Which segments to merge. Everything starts ticked, so the old "flatten the whole session" is
 * still one tap -- but a break that was real can be unticked and kept. Only a run of neighbours
 * can be merged (see TaskTrackerViewModel.areContiguous); pick two that skip one and the button
 * greys out with a note saying why.
 */
@Composable
private fun FlattenSegmentsDialog(segments: List<Task>, onDismiss: () -> Unit, onFlatten: (Set<String>) -> Unit) {
    val sorted = remember(segments) { segments.sortedBy { it.startTime } }
    val chosenIds = remember(segments) { mutableStateListOf<String>().apply { addAll(sorted.map { it.id }) } }
    val chosen = sorted.filter { it.id in chosenIds }
    val contiguous = TaskTrackerViewModel.areContiguous(sorted, chosen)
    val canFlatten = chosen.size > 1 && contiguous
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flatten segments") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tick the segments to merge into one continuous stretch. Their pauses are discarded. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { chosenIds.clear(); chosenIds.addAll(sorted.map { it.id }) }, enabled = chosenIds.size < sorted.size) { Text("All") }
                    TextButton(onClick = { chosenIds.clear() }, enabled = chosenIds.isNotEmpty()) { Text("None") }
                }
                sorted.forEach { segment ->
                    val ticked = segment.id in chosenIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { if (ticked) chosenIds.remove(segment.id) else chosenIds.add(segment.id) }
                    ) {
                        Checkbox(checked = ticked, onCheckedChange = { if (it) chosenIds.add(segment.id) else chosenIds.remove(segment.id) })
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(segment.kind.colorValue))); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(segment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatDetailedDuration(segment.duration)} • ${formatStartEndRange(segment.startTime, segment.endTime)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
                if (chosen.size > 1 && !contiguous) {
                    Text("Only segments that follow each other can be merged -- tick the one in between, or leave it out with the rest.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else if (chosen.size < 2) {
                    Text("Tick at least two segments.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onFlatten(chosenIds.toSet()) }, enabled = canFlatten, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(if (chosen.size == sorted.size) "Flatten all" else "Flatten ${chosen.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TaskDetailDialog(task: Task, taskTypes: List<TaskType>, taskTypeStats: Map<String, TaskTypeStats>, suggestionSourceTasks: List<Task>, onDismiss: () -> Unit, onSaveName: (String) -> Unit, onKindChange: (TaskKind) -> Unit, onTaskTypeChange: (String?) -> Unit, onToggleCalendar: (Boolean) -> Unit, onUpdateTime: (Long, Long) -> Unit, onDelete: () -> Unit, previewScore: suspend (TaskKind, Long) -> Int, onSplit: (Long, String, TaskKind, String?) -> Unit, nextTaskName: String) {
    val context = LocalContext.current; var name by remember(task.name) { mutableStateOf(task.name) }; var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }; val focusManager = LocalFocusManager.current; val keyboardController = LocalSoftwareKeyboardController.current; val isCalendarTask = task.id.startsWith("cal_")
    var showSplitDialog by remember { mutableStateOf(false) }
    
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
        dismissButton = {
            Row {
                TextButton(onClick = { focusManager.clearFocus(); onDismiss() }) { Text("Cancel") }
                if (!isCalendarTask) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        title = { Text("Task Details") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Same autofill dropdown as every other naming field. Picks fill the label only:
                // kind and type have their own pickers right below, and routing a pick through
                // onKindChange/onTaskTypeChange would fire the scoped-edit prompt (twice, for a
                // Recent) over a dialog the user is still typing into.
                TaskNameAutofillField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Task Name",
                    taskTypes = taskTypes,
                    taskTypeStats = taskTypeStats,
                    suggestionSourceTasks = suggestionSourceTasks,
                    fieldModifier = Modifier.onFocusChanged { if (!it.isFocused && name != task.name && !isCalendarTask) onSaveName(name) },
                    enabled = !isCalendarTask,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() })
                )
                if (isCalendarTask) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Kind: ", style = MaterialTheme.typography.bodySmall); TaskKindChip(kind = task.kind) } } else { TaskKindDropdownMenu(selectedKind = task.kind, onKindSelected = onKindChange) }
                // Calendar-sourced tasks are read-only mirrors of an external event, so their type
                // is shown but not editable -- same treatment the name and Kind get above.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type: ", style = MaterialTheme.typography.bodySmall)
                    TaskTypeDropdownMenu(
                        selectedTypeId = task.taskTypeId,
                        taskTypes = taskTypes,
                        onTypeSelected = onTaskTypeChange,
                        enabled = !isCalendarTask
                    )
                }
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

                if (!isCalendarTask) {
                    TextButton(onClick = { showSplitDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Split This Segment")
                    }
                }

                if (!isCalendarTask) {
                    HorizontalDivider()
                    Text("Point Calculation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (task.isRunning) {
                        // No frozen score yet -- tick a live estimate off the current Kind (updates
                        // whenever liveDuration or the Kind changes; previewScore hits the DB for the
                        // current streak, but only once a second here via currentTime, not per-frame).
                        var livePreview by remember { mutableIntStateOf(0) }
                        LaunchedEffect(liveDuration, task.kind) { livePreview = previewScore(task.kind, liveDuration) }
                        DetailItem("Kind Value", (if (task.kind.productivityValue >= 0) "+" else "") + task.kind.productivityValue)
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
                        // Already frozen: show the ACTUAL stored score (the streak may have moved on
                        // since), and back out the momentum multiplier that must have applied
                        // algebraically (score = round(kindValue * minutes * multiplier)) purely for
                        // display, since the multiplier itself isn't stored anywhere.
                        val minutes = task.duration / 60000.0
                        val impliedMultiplier = if (task.kind.productivityValue != 0 && minutes > 0) {
                            task.score / (task.kind.productivityValue * minutes)
                        } else 1.0
                        DetailItem("Kind Value", (if (task.kind.productivityValue >= 0) "+" else "") + task.kind.productivityValue)
                        DetailItem("Momentum Multiplier", "${"%.2f".format(impliedMultiplier)}x")
                        Text(
                            text = "Total: ${if (task.score >= 0) "+" else ""}${task.score} pts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (task.score >= 0) Success else Color(0xFFFF4D4D)
                        )
                    }
                }

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

    if (showSplitDialog) {
        val effectiveEnd = task.endTime ?: currentTime
        val totalSpan = (effectiveEnd - task.startTime).coerceAtLeast(1L)
        // Defaults to ticking live off "how long has this been running so far" for an ongoing
        // task -- currentTime already advances every second while task.isRunning (see the
        // LaunchedEffect above). The instant the user edits any Hrs/Min/Sec field, it freezes to
        // that manual value and stops following the clock, since typing "1 min 4 sec" means "I
        // want exactly that split point," not "keep counting from wherever I paused you."
        var useLiveOffset by remember { mutableStateOf(task.isRunning) }
        // The fields hold their own freely-typed text, NOT a value derived-and-clamped from
        // offsetMs -- clamping the display itself made any typed number that (even momentarily)
        // exceeded the segment's length snap back to the old value, which read as "my edit keeps
        // getting reverted." Validity is checked separately below without touching what's typed.
        val initialOffsetMs = if (useLiveOffset) (currentTime - task.startTime) else totalSpan / 2
        var hoursStr by remember { mutableStateOf(TimeUnit.MILLISECONDS.toHours(initialOffsetMs).toString()) }
        var minutesStr by remember { mutableStateOf((TimeUnit.MILLISECONDS.toMinutes(initialOffsetMs) % 60).toString()) }
        var secondsStr by remember { mutableStateOf((TimeUnit.MILLISECONDS.toSeconds(initialOffsetMs) % 60).toString()) }
        var splitName by remember { mutableStateOf(nextTaskName) }
        var splitKind by remember { mutableStateOf(task.kind) }
        // Starts untyped, same as any brand-new task (the second half is deliberately a fresh,
        // independent task) -- stamped only when an autofill pick supplies one.
        var splitTypeId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(currentTime) {
            if (useLiveOffset) {
                val liveOffset = (currentTime - task.startTime).coerceIn(0L, totalSpan)
                hoursStr = TimeUnit.MILLISECONDS.toHours(liveOffset).toString()
                minutesStr = (TimeUnit.MILLISECONDS.toMinutes(liveOffset) % 60).toString()
                secondsStr = (TimeUnit.MILLISECONDS.toSeconds(liveOffset) % 60).toString()
            }
        }

        val offsetMs = TimeUnit.HOURS.toMillis(hoursStr.toLongOrNull() ?: 0L) +
            TimeUnit.MINUTES.toMillis(minutesStr.toLongOrNull() ?: 0L) +
            TimeUnit.SECONDS.toMillis(secondsStr.toLongOrNull() ?: 0L)

        val splitTime = task.startTime + offsetMs
        // While still ticking live off "now" for a running task, offsetMs and totalSpan are
        // (by construction) the same instant, so a strict offsetMs < totalSpan would never pass
        // -- by the time Split is actually tapped, real "now" has moved past this displayed
        // instant anyway, so only require some non-zero time has actually elapsed. Once frozen to
        // a manual value (or the segment was already complete to begin with), the strict upper
        // bound is what actually prevents a zero-length second half.
        val isValidSplit = if (useLiveOffset) offsetMs > 0 else (offsetMs > 0 && offsetMs < totalSpan)
        val splitFraction = (offsetMs.toFloat() / totalSpan.toFloat()).coerceIn(0f, 1f)

        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Split This Segment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Cuts this segment in two, this far into it. The first part keeps this name and category; the second part becomes a brand new, separate task.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DurationPartField("Hrs", hoursStr, { useLiveOffset = false; hoursStr = it }, Modifier.weight(1f))
                        DurationPartField("Min", minutesStr, { useLiveOffset = false; minutesStr = it }, Modifier.weight(1f))
                        DurationPartField("Sec", secondsStr, { useLiveOffset = false; secondsStr = it }, Modifier.weight(1f))
                    }
                    Text(
                        text = formatDetailedDuration(offsetMs) + " in" + if (useLiveOffset) " -- still counting, live" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (useLiveOffset) Success else MaterialTheme.colorScheme.primary
                    )

                    // Split graphic: proportional bar showing where the cut falls across the
                    // segment's full span, colored by each half's Kind, with a fixed-width
                    // divider between them so the boundary stays visible even when both halves
                    // happen to share the same Kind (and thus the same color).
                    val secondOffsetMs = (totalSpan - offsetMs).coerceAtLeast(0L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(modifier = Modifier.weight(splitFraction.coerceAtLeast(0.001f)).fillMaxHeight().background(Color(task.kind.colorValue)))
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                        Box(modifier = Modifier.weight((1f - splitFraction).coerceAtLeast(0.001f)).fillMaxHeight().background(Color(splitKind.colorValue)))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(splitFraction.coerceAtLeast(0.001f)), contentAlignment = Alignment.Center) {
                            Text(formatDetailedDuration(offsetMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(modifier = Modifier.weight((1f - splitFraction).coerceAtLeast(0.001f)), contentAlignment = Alignment.Center) {
                            Text(formatDetailedDuration(secondOffsetMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = "At ${formatDateTime(splitTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isValidSplit) {
                        Text(
                            text = "Split time must be between the start and ${if (task.isRunning) "now" else "end"}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // The second half is a new task, so a pick stamps everything the active-card
                    // autofill would: a Type prefills its most-used Kind, a Recent carries its
                    // kind and settled type (never clearing on null, same rule as the card).
                    TaskNameAutofillField(
                        value = splitName,
                        onValueChange = { splitName = it },
                        label = "New Task Name",
                        taskTypes = taskTypes,
                        taskTypeStats = taskTypeStats,
                        suggestionSourceTasks = suggestionSourceTasks,
                        onPickType = { picked ->
                            splitTypeId = picked.typeId
                            picked.mostUsedKind?.let { splitKind = it }
                        },
                        onPickRecent = { picked ->
                            splitKind = picked.kind
                            picked.typeId?.let { splitTypeId = it }
                        }
                    )
                    Text("New Task Category", style = MaterialTheme.typography.labelSmall)
                    TaskKindDropdownMenu(selectedKind = splitKind, onKindSelected = { splitKind = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSplit(splitTime, splitName, splitKind, splitTypeId)
                        showSplitDialog = false
                        onDismiss()
                    },
                    enabled = isValidSplit
                ) { Text("Split") }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) { Text("Cancel") }
            }
        )
    }
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
fun formatDateRange(start: Long, end: Long): String { return "${formatSimpleDate(start)} - ${formatSimpleDate(end)}" }
fun isSpanningDays(start: Long, end: Long): Boolean { val startCal = Calendar.getInstance().apply { timeInMillis = start }; val endCal = Calendar.getInstance().apply { timeInMillis = end }; return startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) || startCal.get(Calendar.DAY_OF_YEAR) != endCal.get(Calendar.DAY_OF_YEAR) }
fun formatDateTime(timestamp: Long): String { val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); return "${formatDate(timestamp)} ${timeSdf.format(Date(timestamp))}" }
fun formatStartEndRange(start: Long, end: Long?): String { if (end == null) return "Started: ${formatDateTime(start)}"; val startCal = Calendar.getInstance().apply { timeInMillis = start }; val endCal = Calendar.getInstance().apply { timeInMillis = end }; val isSameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR); val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()); return if (isSameDay) { "${formatDate(start)} ${timeSdf.format(Date(start))} - ${timeSdf.format(Date(end))}" } else { "${formatDateTime(start)} - ${formatDateTime(end)}" } }
fun calculatePercentageOfDay(taskDuration: Long, timestamp: Long): String { val percentage = (taskDuration.toDouble() / 86400000.0) * 100.0; val dayLabel = getDayLabel(timestamp); return String.format("%.1f%% of %s", percentage, dayLabel) }
fun calculateOverlapWithToday(start: Long, end: Long, todayStart: Long): Long {
    val effectiveStart = maxOf(start, todayStart)
    val effectiveEnd = maxOf(end, todayStart)
    return if (effectiveEnd > effectiveStart) effectiveEnd - effectiveStart else 0L
}
fun showDateTimePicker(context: Context, initialTime: Long, onTimeSelected: (Long) -> Unit) { val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }; DatePickerDialog(context, { _, year, month, day -> TimePickerDialog(context, { _, hour, minute -> val result = Calendar.getInstance().apply { set(year, month, day, hour, minute) }; onTimeSelected(result.timeInMillis) }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }
fun addToGoogleCalendar(context: Context, task: Task) { val googleColorId = when (task.kind) { TaskKind.TOMATO -> 11; TaskKind.TANGERINE -> 6; TaskKind.GRAPHITE -> 8; TaskKind.GRAPE -> 3; TaskKind.BLUEBERRY -> 9; TaskKind.LAVENDER -> 1; TaskKind.PEACOCK -> 7; TaskKind.BANANA -> 5; TaskKind.FLAMINGO -> 4; TaskKind.BASIL -> 10; TaskKind.SAGE -> 2 }; val description = "Type: ${task.kind.displayName}\nDuration: ${formatDetailedDuration(task.duration)}\nTracked via Inventoria Task Tracker\nTask ID: ${task.id}\nSession ID: ${task.groupId}"; val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, task.name).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, task.startTime).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, task.endTime ?: (task.startTime + task.duration)).putExtra(CalendarContract.Events.DESCRIPTION, description).putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY).putExtra("eventColorId", googleColorId.toString()); context.startActivity(intent) }
fun openInSystemCalendar(context: Context, task: Task) { if (task.id.startsWith("cal_")) { val eventId = task.id.removePrefix("cal_").toLongOrNull(); if (eventId != null) { val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId); try { context.startActivity(Intent(Intent.ACTION_VIEW).setData(uri)); return } catch (e: Exception) { Log.e("TaskTracker", "Could not open event $eventId", e) } } }; try { context.startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time/${task.startTime}"))) } catch (e: Exception) { Log.e("TaskTracker", "Could not open calendar", e) } }
