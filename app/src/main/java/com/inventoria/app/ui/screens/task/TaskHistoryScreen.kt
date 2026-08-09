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

private fun formatTimeOfDay(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

/** For a list of time-gutter rows in the order they're displayed, returns which ones should
 * actually show their clock time -- false whenever it's identical to the immediately preceding
 * row's (e.g. an interruption starting the instant its parent gets paused, landing on the same
 * minute), so the gutter doesn't print the same "08:45" twice in a row. */
private fun <T> showTimeFlagsById(items: List<T>, idAndStartTime: (T) -> Pair<String, Long>): Map<String, Boolean> {
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
    val isSelectionMode = selectedTaskIds.isNotEmpty()
    val context = LocalContext.current

    var selectedSessionGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

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
    // A multi-day session is bucketed under the day of its most recent segment -- it's rendered
    // as one card either way, so it has to live under a single day header.
    val sessionDayBuckets = remember(completedSessions) {
        bucketByDay(completedSessions.filter { it.isNotEmpty() }) { session -> getStartOfDay(session.first().startTime) }
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
    val sessionShowTimeByDay = remember(sessionDayBuckets) {
        sessionDayBuckets.associate { day ->
            day.dayStart to showTimeFlagsById(day.items.filter { it.size == 1 }) { it.first().id to it.first().startTime }
        }
    }

    Scaffold(
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
        val isEmpty = if (isFlatView) flatDayBuckets.isEmpty() else sessionDayBuckets.isEmpty()
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
                            showTime = showTimeById[task.id] != false,
                            onClick = {
                                if (isSelectionMode) viewModel.toggleTaskSelection(task.id)
                                else selectedTaskId = task.id
                            },
                            onLongClick = { viewModel.toggleTaskSelection(task.id) },
                            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                            onDelete = { viewModel.deleteSegment(task) },
                            onAddToCalendar = { addToGoogleCalendar(context, task) }
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
                sessionDayBuckets.forEach { day ->
                    item(key = "day_${day.dayStart}") {
                        DayTimelineHeader(day.dayStart, dayStats[day.dayStart] ?: emptyList())
                    }
                    val showTimeById = sessionShowTimeByDay[day.dayStart] ?: emptyMap()
                    items(day.items, key = { it.first().groupId }) { session ->
                        if (session.size > 1) {
                            CompletedSessionCard(
                                segments = session,
                                currentTime = currentTime,
                                selectedTaskIds = selectedTaskIds,
                                onClick = { selectedSessionGroupId = session.first().groupId },
                                onDelete = { viewModel.deleteSession(session.first().groupId) },
                                onSegmentClick = {
                                    if (isSelectionMode) viewModel.toggleTaskSelection(it.id)
                                    else selectedTaskId = it.id
                                },
                                onSegmentLongClick = { task -> viewModel.toggleTaskSelection(task.id) },
                                onSegmentDelete = { viewModel.deleteSegment(it) },
                                onSegmentToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) }
                            )
                        } else {
                            val task = session.first()
                            TimelineTaskRow(
                                task = task,
                                isSelected = task.id in selectedTaskIds,
                                showTime = showTimeById[task.id] != false,
                                onClick = {
                                    if (isSelectionMode) viewModel.toggleTaskSelection(task.id)
                                    else selectedTaskId = task.id
                                },
                                onLongClick = { viewModel.toggleTaskSelection(task.id) },
                                onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, !task.savedToCalendar) },
                                onDelete = { viewModel.deleteSegment(task) },
                                onAddToCalendar = { addToGoogleCalendar(context, task) }
                            )
                        }
                    }
                    item(key = "spacer_${day.dayStart}") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    currentSelectedSession?.let { segments ->
        SessionDetailDialog(
            segments = segments,
            onDismiss = { selectedSessionGroupId = null },
            onUpdateSessionName = { name -> viewModel.updateSessionName(segments.first().groupId, name) },
            onUpdateSessionKind = { kind -> viewModel.updateSessionKind(segments.first().groupId, kind) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(it, !it.savedToCalendar) },
            onFlatten = { viewModel.flattenSession(segments.first().groupId) },
            onNavigateToTaskDetail = { selectedTaskId = it },
            onDeleteSegment = { viewModel.deleteSegment(it) }
        )
    }

    currentSelectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTaskId = null },
            onSaveName = { viewModel.updateCompletedTaskName(task, it) },
            onKindChange = { viewModel.updateCompletedTaskKind(task, it) },
            onToggleCalendar = { viewModel.setSegmentCalendarStatus(task, it) },
            onUpdateTime = { start, end -> viewModel.updateSegmentTime(task, start, end) },
            onDelete = { viewModel.deleteSegment(task); selectedTaskId = null }
        )
    }
}

/** Day section header: which day it was, its actual date, how much got tracked, and a mini
 * 24-hour timeline bar showing roughly when in the day things happened -- the "day tracker on
 * a calendar" look, condensed into one row per day. */
@Composable
private fun DayTimelineHeader(dayStart: Long, tasksThatDay: List<Task>) {
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
private fun DayMiniTimeline(dayStart: Long, tasksThatDay: List<Task>) {
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
private fun TimelineTaskRow(
    task: Task,
    isSelected: Boolean,
    showTime: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCalendar: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit
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
            modifier = Modifier.weight(1f),
            onClick = onClick,
            onLongClick = onLongClick,
            onToggleCalendar = onToggleCalendar,
            onDelete = onDelete,
            onAddToCalendar = onAddToCalendar
        )
    }
}
