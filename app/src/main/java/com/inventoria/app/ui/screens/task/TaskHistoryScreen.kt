package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.util.bucketByDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatTimeOfDay(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

/** For a list of time-gutter rows in the order they're displayed, returns which ones should
 * actually show their clock time -- false whenever it's identical to the immediately preceding
 * row's (e.g. an interruption starting the instant its parent gets paused, landing on the same
 * minute), so the gutter doesn't print the same "08:45" twice in a row. */
internal fun <T> showTimeFlagsById(items: List<T>, idAndStartTime: (T) -> Pair<String, Long>): Map<String, Boolean> {
    val flags = mutableMapOf<String, Boolean>()
    var lastLabel: String? = null
    items.forEach { item ->
        val (id, startTime) = idAndStartTime(item)
        val label = formatTimeOfDay(startTime)
        flags[id] = label != lastLabel
        lastLabel = label
    }
    return flags
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val flatCompletedTasks by viewModel.flatCompletedTasks.collectAsState()
    val isFlatView by viewModel.isTaskHistoryFlatView.collectAsState()
    val currentTime by rememberTick()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val taskTypeNames by viewModel.taskTypeNamesById.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val taskTypeStats by viewModel.taskTypeStats.collectAsState()
    val activityGroups by viewModel.completedActivityGroups.collectAsState()
    val isSelectionMode = selectedTaskIds.isNotEmpty()
    val context = LocalContext.current

    var selectedSessionGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var pendingActivityDelete by remember { mutableStateOf<ActivityGroup?>(null) }
    var pendingScopedEdit by remember { mutableStateOf<PendingScopedEdit?>(null) }
    val activityFor: (Task) -> ActivityGroup? = { task -> activityGroups.find { it.key == activityKeyOf(task) } }

    val currentSelectedSession = remember(selectedSessionGroupId, completedSessions) {
        selectedSessionGroupId?.let { groupId ->
            completedSessions.find { it.firstOrNull()?.groupId == groupId }
        }
    }

    val currentSelectedTask = remember(selectedTaskId, completedSessions) {
        selectedTaskId?.let { id ->
            completedSessions.flatten().find { it.id == id }
        }
    }

    // Autofill pool for the name fields in the detail dialogs -- history only knows completed
    // work, which is what the suggestions should be learning from anyway.
    val suggestionSourceTasks = remember(completedSessions) { completedSessions.flatten() }

    // The day-by-day breakdown (used for every day header's total/mini-timeline) is always
    // computed from individual segments, even in grouped view -- a session's own segments can
    // span multiple days, so only the flat, per-segment data can say what actually happened
    // on any single given day.
    val flatDayBuckets = remember(flatCompletedTasks) {
        bucketByDay(flatCompletedTasks) { getStartOfDay(it.startTime) }
    }
    val dayStats = remember(flatDayBuckets) {
        flatDayBuckets.associate { it.dayStart to it.items }
    }
    // Grouped view is by activity (same name + same type), not by session -- the same rule the
    // Tasks screen's toggle uses. A group spanning days is bucketed under its most recent sitting,
    // since it renders as one card and has to live under a single day header.
    val activityDayBuckets = remember(activityGroups) {
        bucketByDay(activityGroups) { getStartOfDay(it.mostRecentStartTime) }
    }

    // Per-day "should this row show its clock time" lookups, keyed by day then by task id --
    // computed once here (remember requires composable context, unlike inside LazyListScope's
    // plain content lambda) and just read as plain maps down in the list below.
    val flatShowTimeByDay = remember(flatDayBuckets) {
        flatDayBuckets.associate { day -> day.dayStart to showTimeFlagsById(day.items) { it.id to it.startTime } }
    }
    // Only single-segment sessions show a time gutter (CompletedSessionCard has no spot for
    // one), so the dedup chain only tracks those, skipping multi-segment sessions entirely
    // rather than comparing against a label nobody can see.
    val activityShowTimeByDay = remember(activityDayBuckets) {
        activityDayBuckets.associate { day ->
            day.dayStart to showTimeFlagsById(day.items.filter { it.segments.size == 1 }) {
                it.segments.first().id to it.segments.first().startTime
            }
        }
    }

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
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveSelectedTasksToCalendar() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Selected")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedTasks() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Task History", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setTaskHistoryFlatView(!isFlatView) }) {
                            Icon(
                                if (isFlatView) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (isFlatView) "Switch to Grouped View" else "Switch to Flat View"
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        val isEmpty = if (isFlatView) flatDayBuckets.isEmpty() else activityDayBuckets.isEmpty()
        if (isEmpty) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tasks recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (isFlatView) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                flatDayBuckets.forEach { day ->
                    item(key = "day_${day.dayStart}") {
                        DayTimelineHeader(day.dayStart, day.items)
                    }
                    val showTimeById = flatShowTimeByDay[day.dayStart] ?: emptyMap()
                    items(day.items, key = { it.id }) { task ->
                        TimelineTaskRow(
                            task = task,
                            isSelected = task.id in selectedTaskIds,
                            taskTypeNames = taskTypeNames,
                            showTime = showTimeById[task.id] != false,
                            onClick = {
                                if (isSelectionMode) viewModel.toggleTaskSelection(task.id)
                                else selectedTaskId = task.id
                            },
                            onLongClick = { viewModel.toggleTaskSelection(task.id) },
                            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                            onDelete = { viewModel.deleteSegment(task) },
                            onAddToCalendar = { addToGoogleCalendar(context, task) },
                            onHideCalendarItem = { viewModel.hideCalendarTask(task) }
                        )
                    }
                    item(key = "spacer_${day.dayStart}") { Spacer(Modifier.height(8.dp)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activityDayBuckets.forEach { day ->
                    item(key = "day_${day.dayStart}") {
                        DayTimelineHeader(day.dayStart, dayStats[day.dayStart] ?: emptyList())
                    }
                    val showTimeById = activityShowTimeByDay[day.dayStart] ?: emptyMap()
                    items(day.items, key = { "activity_${it.key.name}_${it.key.taskTypeId}" }) { group ->
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
                                onSegmentClick = {
                                    if (isSelectionMode) viewModel.toggleTaskSelection(it.id)
                                    else selectedTaskId = it.id
                                },
                                onSegmentLongClick = { task -> viewModel.toggleTaskSelection(task.id) },
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
                                onClick = {
                                    if (isSelectionMode) viewModel.toggleTaskSelection(task.id)
                                    else selectedTaskId = task.id
                                },
                                onLongClick = { viewModel.toggleTaskSelection(task.id) },
                                onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                onDelete = { viewModel.deleteSegment(task) },
                                onAddToCalendar = { addToGoogleCalendar(context, task) },
                                onHideCalendarItem = { viewModel.hideCalendarTask(task) }
                            )
                        }
                    }
                    item(key = "spacer_${day.dayStart}") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
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

    currentSelectedSession?.let { segments ->
        SessionDetailDialog(
            segments = segments,
            taskTypes = taskTypes,
            taskTypeStats = taskTypeStats,
            suggestionSourceTasks = suggestionSourceTasks,
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
            onFlatten = { viewModel.flattenSession(segments.first().groupId) },
            onNavigateToTaskDetail = { selectedTaskId = it },
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
}

/** Day section header: which day it was, its actual date, how much got tracked, and a mini
 * 24-hour timeline bar showing roughly when in the day things happened -- the "day tracker on
 * a calendar" look, condensed into one row per day. */
@Composable
internal fun DayTimelineHeader(dayStart: Long, tasksThatDay: List<Task>) {
    val totalDuration = tasksThatDay.sumOf { it.duration }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = getDayLabel(dayStart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatSimpleDate(dayStart),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (totalDuration > 0) {
                Text(
                    text = "${formatDetailedDuration(totalDuration)} tracked",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        DayMiniTimeline(dayStart, tasksThatDay)
    }
}

/** A thin bar spanning midnight-to-midnight, with a colored segment (in the task's Kind color)
 * for each task positioned and sized proportionally to when it happened during the day. */
@Composable
internal fun DayMiniTimeline(dayStart: Long, tasksThatDay: List<Task>) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        drawRect(color = trackColor, size = size)
        val dayMillis = 86_400_000f
        tasksThatDay.forEach { task ->
            val startOffset = (task.startTime - dayStart).toFloat().coerceIn(0f, dayMillis)
            val endOffset = (startOffset + task.duration.toFloat()).coerceIn(0f, dayMillis)
            val left = (startOffset / dayMillis) * size.width
            val segmentWidth = ((endOffset - startOffset) / dayMillis * size.width).coerceAtLeast(3f)
            drawRect(
                color = Color(task.kind.colorValue),
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height)
            )
        }
    }
}

/** One history row with a clock-time gutter on the left (like a calendar day view) leading into
 * the existing task card, so a flat/day-grouped list reads as a timeline rather than a bare feed. */
@Composable
internal fun TimelineTaskRow(
    task: Task,
    isSelected: Boolean,
    taskTypeNames: Map<String, String>,
    showTime: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit,
    onHideCalendarItem: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showTime) {
            Text(
                text = formatTimeOfDay(task.startTime),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        } else {
            Spacer(Modifier.width(40.dp))
        }
        SingleTaskItemCard(
            task = task,
            isSelected = isSelected,
            taskTypeNames = taskTypeNames,
            modifier = Modifier.weight(1f),
            onClick = onClick,
            onLongClick = onLongClick,
            onToggleCalendar = onToggleCalendar,
            onDelete = onDelete,
            onAddToCalendar = onAddToCalendar,
            onHideCalendarItem = onHideCalendarItem
        )
    }
}
