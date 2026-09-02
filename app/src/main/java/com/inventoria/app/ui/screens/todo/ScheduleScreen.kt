package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.task.TaskKindDropdownMenu
import com.inventoria.app.ui.screens.task.TaskTypeDropdownMenu
import com.inventoria.app.ui.screens.task.TaskTypeLabel
import com.inventoria.app.ui.screens.task.taskTypeColor
import com.inventoria.app.ui.screens.task.todoPriorityTierColor
import com.inventoria.app.util.currentMinuteOfDay
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import com.inventoria.app.util.packIntoLanes
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Vertical scale of the day timeline. 64dp an hour puts a 15-minute block at 16dp -- still a
 * legible bar -- and the whole day at 1536dp, about three screens of scrolling. */
private val HOUR_HEIGHT = 64.dp
private val GUTTER_WIDTH = 44.dp

/**
 * The Schedule segment: a week strip to pick a day, and that day as one 24-hour timeline.
 *
 * Schedule blocks are painted flat and translucent across the full width, as if drawn on the
 * calendar paper itself -- they are what the time was *for*. Tracked task segments sit in front
 * as solid cards -- what the time was *used* for. Tasks never reach the right edge: a strip down
 * that side belongs to the blocks alone, so whatever plan a task is covering still shows as a
 * colour beside it. Todos due at a time are hairlines across everything.
 *
 * Blocks are created by tapping an empty hour (or the FAB) and edited by tapping the block. Todos
 * here are read-mostly: tapping one ticks it off, editing stays on the Todos segment (see
 * ScheduleViewModel's KDoc). Tasks are display only -- the tracker owns them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val day by viewModel.day.collectAsState()
    val weekDays by viewModel.weekDays.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val pendingBlock by viewModel.pendingBlock.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val taskTypeNames by viewModel.taskTypeNamesById.collectAsState()
    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }
    // Same once-a-minute ticker TodoScreen runs: moves the now-line and grows a running task.
    val nowMinuteOfDay by produceState(currentMinuteOfDay()) {
        while (true) {
            delay(60_000)
            value = currentMinuteOfDay()
        }
    }

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
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startAddingBlock() }) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule block")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WeekStrip(
                weekStart = weekStart,
                weekDays = weekDays,
                selectedDay = selectedDay,
                todayStart = todayStart,
                onSelect = { viewModel.selectDay(it) },
                onShiftWeek = { viewModel.shiftWeek(it) }
            )
            if (day.allDayTodos.isNotEmpty()) {
                AllDayTodoStrip(day.allDayTodos, onToggle = { viewModel.toggleTodoComplete(it) })
            }
            DayTimeline(
                day = day,
                isToday = selectedDay == todayStart,
                nowMinuteOfDay = nowMinuteOfDay,
                onTapEmptyMinute = { minute -> viewModel.startAddingBlock((minute / 60) * 60) },
                taskTypeNames = taskTypeNames,
                onBlockClick = { viewModel.startEditingBlock(it) },
                onTodoClick = { viewModel.toggleTodoComplete(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    pendingBlock?.let { block ->
        ScheduleBlockDialog(
            block = block,
            isNew = block.id.isBlank(),
            taskTypes = taskTypes,
            onDismiss = { viewModel.dismissDialog() },
            onDelete = { viewModel.deleteBlock(block) },
            onSave = { title, kind, taskTypeId, dayStart, start, end, repeatWeekly, notes ->
                viewModel.saveBlock(title, kind, taskTypeId, dayStart, start, end, repeatWeekly, notes)
            }
        )
    }
}

// ---- Week strip -------------------------------------------------------------------------------

@Composable
private fun WeekStrip(
    weekStart: Long,
    weekDays: List<WeekDayMarker>,
    selectedDay: Long,
    todayStart: Long,
    onSelect: (Long) -> Unit,
    onShiftWeek: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onShiftWeek(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
            }
            Text(
                text = weekRangeLabel(weekStart, weekDays.lastOrNull()?.dayStart ?: weekStart),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { onShiftWeek(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { marker ->
                WeekDayCell(
                    marker = marker,
                    selected = marker.dayStart == selectedDay,
                    isToday = marker.dayStart == todayStart,
                    onClick = { onSelect(marker.dayStart) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** "September 2026", or "Aug – Sep 2026" for a week that straddles a month boundary. */
private fun weekRangeLabel(first: Long, last: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = last }
    val sameMonth = a.get(Calendar.MONTH) == b.get(Calendar.MONTH) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    return if (sameMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(first))
    } else {
        val short = SimpleDateFormat("MMM", Locale.getDefault())
        "${short.format(Date(first))} – ${short.format(Date(last))} ${b.get(Calendar.YEAR)}"
    }
}

@Composable
private fun WeekDayCell(
    marker: WeekDayMarker,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val weekday = remember(marker.dayStart) {
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(marker.dayStart))
    }
    val dayNumber = remember(marker.dayStart) {
        Calendar.getInstance().apply { timeInMillis = marker.dayStart }.get(Calendar.DAY_OF_MONTH).toString()
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            weekday,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                    else if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                dayNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || isToday) FontWeight.Bold else null,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        // Three dots, one per kind of thing on the day, in the same colours the lanes use below:
        // blocks in primary, tasks in secondary, todos in tertiary. Empty space when there is
        // nothing, so the cells stay the same height.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
            if (marker.hasBlocks) MarkerDot(MaterialTheme.colorScheme.primary)
            if (marker.hasTasks) MarkerDot(MaterialTheme.colorScheme.secondary)
            if (marker.hasTodos) MarkerDot(MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun MarkerDot(color: Color) {
    Box(
        Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ---- All-day todos ----------------------------------------------------------------------------

/** Todos due on the day with no time of their own. They have no place on an hour grid, so they
 * sit in a strip above it -- one chip each, tinted by priority tier, tap to tick off. */
@Composable
private fun AllDayTodoStrip(todos: List<Todo>, onToggle: (Todo) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "All day",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )
        todos.forEach { todo ->
            val done = todo.state == TodoState.COMPLETE
            val tier = todoPriorityTierColor(todo.priority)
            val alarmIcon: (@Composable () -> Unit)? = if (todo.reminderOffsetMinutes != null && !done) {
                { Icon(Icons.Default.Alarm, contentDescription = "Alarm set", modifier = Modifier.size(14.dp)) }
            } else null
            AssistChip(
                onClick = { onToggle(todo) },
                label = {
                    Text(
                        todo.title,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (done) "Completed" else "Not completed",
                        modifier = Modifier.size(16.dp),
                        tint = tier
                    )
                },
                trailingIcon = alarmIcon,
                colors = AssistChipDefaults.assistChipColors(containerColor = tier.copy(alpha = 0.12f))
            )
        }
    }
}

// ---- Day timeline -----------------------------------------------------------------------------

/** The right-edge strip tasks never cover, where the block underneath always shows through. */
private val PEEK_STRIP_WIDTH = 10.dp

@Composable
private fun DayTimeline(
    day: ScheduleDay,
    isToday: Boolean,
    nowMinuteOfDay: Int,
    onTapEmptyMinute: (Int) -> Unit,
    taskTypeNames: Map<String, String>,
    onBlockClick: (ScheduleBlock) -> Unit,
    onTodoClick: (Todo) -> Unit,
    modifier: Modifier
) {
    val scrollState = rememberScrollState()
    val hourHeightPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    // Land somewhere useful once, rather than at 00:00: an hour before now on today, a working
    // morning otherwise. Not repeated on day changes -- you were probably looking at an hour.
    LaunchedEffect(Unit) {
        val targetMinute = if (isToday) (nowMinuteOfDay - 60).coerceAtLeast(0) else 7 * 60
        scrollState.scrollTo((targetMinute / 60f * hourHeightPx).roundToInt())
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.error

    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(HOUR_HEIGHT * 24)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val gutter = GUTTER_WIDTH.toPx()
                val stroke = 1.dp.toPx()
                for (hour in 0..24) {
                    val y = hour * hourHeightPx
                    drawLine(gridColor, Offset(gutter, y), Offset(size.width, y), stroke)
                }
            }
            Column(Modifier.width(GUTTER_WIDTH)) {
                for (hour in 0 until 24) {
                    Box(
                        Modifier
                            .height(HOUR_HEIGHT)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 4.dp)
                                .offset(y = (-7).dp)
                        )
                    }
                }
            }
            DayLane(
                day = day,
                nowMinuteOfDay = nowMinuteOfDay,
                onTapEmptyMinute = onTapEmptyMinute,
                onBlockClick = onBlockClick,
                onTodoClick = onTodoClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = GUTTER_WIDTH)
            )
            if (isToday) {
                val y = HOUR_HEIGHT * (nowMinuteOfDay / 60f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = y - 1.dp)
                        .padding(start = GUTTER_WIDTH - 4.dp)
                        .height(2.dp)
                        .background(nowColor)
                )
                Box(
                    Modifier
                        .offset(x = GUTTER_WIDTH - 8.dp, y = y - 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(nowColor)
                )
            }
        }
    }
}

/**
 * The single lane, back to front: flat blocks across the full width, then task cards (lane-packed
 * among themselves, stopping short of the right-edge strip), then todo hairlines over everything.
 * Compose draws children in order, so this ordering is the layering.
 */
@Composable
private fun DayLane(
    day: ScheduleDay,
    nowMinuteOfDay: Int,
    onTapEmptyMinute: (Int) -> Unit,
    onBlockClick: (ScheduleBlock) -> Unit,
    onTodoClick: (Todo) -> Unit,
    modifier: Modifier
) {
    val hourHeightPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    // Blocks, tasks and todo markers each consume their own taps before they reach this, so
    // whatever arrives here really did land on empty paper.
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onTapEmptyMinute(((offset.y / hourHeightPx) * 60).toInt().coerceIn(0, 24 * 60 - 1))
            }
        }
    ) {
        val fullWidth = maxWidth
        val taskAreaWidth = fullWidth - PEEK_STRIP_WIDTH

        if (day.blocks.isEmpty() && day.timedTodos.isEmpty() && day.tasks.isEmpty()) {
            Text(
                "Tap an hour to block it out",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        // Blocks: the plan, painted on the paper. Full width, so the peek strip is simply the part
        // of a block nothing else is allowed to cover.
        day.blocks.forEach { block ->
            val top = HOUR_HEIGHT * (block.startMinuteOfDay / 60f)
            val height = HOUR_HEIGHT * ((block.endMinuteOfDay - block.startMinuteOfDay) / 60f)
            FlatScheduleBlock(
                block = block,
                typeName = block.taskTypeId?.let { taskTypeNames[it] },
                modifier = Modifier
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(maxOf(height, 20.dp)),
                onClick = { onBlockClick(block) }
            )
        }

        // Tasks: what actually happened, solid and in front. A running one ends "now", resolved
        // here on the minute ticker so a live session visibly grows down the day.
        val resolved = remember(day.tasks, nowMinuteOfDay) {
            day.tasks.map { it to (it.endMinute ?: nowMinuteOfDay.toFloat().coerceAtLeast(it.startMinute)) }
        }
        val slots = remember(resolved) {
            packIntoLanes(resolved, start = { it.first.startMinute }, end = { it.second })
        }
        slots.forEach { slot ->
            val (segment, endMinute) = slot.item
            val width = taskAreaWidth / slot.laneCount
            TaskSegmentCard(
                task = segment.task,
                isRunning = segment.endMinute == null,
                modifier = Modifier
                    .offset(x = width * slot.lane, y = HOUR_HEIGHT * (segment.startMinute / 60f))
                    .width(width)
                    .height(maxOf(HOUR_HEIGHT * ((endMinute - segment.startMinute) / 60f), 16.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }

        // Todos: a deadline is a moment, so a hairline across everything at that minute.
        day.timedTodos.forEach { todo ->
            val minute = todo.deadlineMinuteOfDay ?: return@forEach
            TodoDueMarker(
                todo = todo,
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * (minute / 60f) - 1.dp)
                    .fillMaxWidth(),
                onClick = { onTodoClick(todo) }
            )
        }
    }
}

/**
 * A designated stretch of time, drawn as if on the calendar paper: a faint wash of its kind's
 * colour edge to edge, a solid hairline at its top so the boundary reads even under a task, the
 * title and span in the top-left, and the right-edge peek strip in a stronger tint of the same
 * colour. Tasks stop short of that strip, so when one covers this block the strip still says
 * what the hour was meant for.
 */
@Composable
private fun FlatScheduleBlock(block: ScheduleBlock, typeName: String?, modifier: Modifier, onClick: () -> Unit) {
    val kindColor = Color(block.kind.colorValue)
    Box(
        modifier
            .background(kindColor.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopStart),
            thickness = 1.5.dp,
            color = kindColor.copy(alpha = 0.7f)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(PEEK_STRIP_WIDTH)
                .fillMaxHeight()
                .background(kindColor.copy(alpha = 0.65f))
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 3.dp, end = PEEK_STRIP_WIDTH + 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (block.repeatWeekly) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Repeats weekly",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMinuteOfDay(block.startMinuteOfDay)} – ${formatMinuteOfDay(block.endMinuteOfDay)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // The activity the hour is for, in the type's own colour -- the same chip a todo
                // row and a tracker card use, so a typed block reads as the plan for a typed task.
                val typeId = block.taskTypeId
                if (typeName != null && typeId != null) {
                    Spacer(Modifier.width(6.dp))
                    TaskTypeLabel(typeName, iconSize = 10.dp, color = taskTypeColor(typeId))
                }
            }
        }
    }
}

/** A todo due at a time of day: a hairline across the whole day at that minute, in the todo's
 * priority-tier colour, with a small label hanging under it. A deadline is a moment, not a span,
 * which is why this is a line and not a box. */
@Composable
private fun TodoDueMarker(todo: Todo, modifier: Modifier, onClick: () -> Unit) {
    val tier = todoPriorityTierColor(todo.priority)
    val done = todo.state == TodoState.COMPLETE
    Column(modifier.clickable(onClick = onClick)) {
        HorizontalDivider(thickness = 2.dp, color = tier.copy(alpha = if (done) 0.4f else 1f))
        Surface(
            shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
            color = tier.copy(alpha = 0.15f)
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (done) "Completed" else "Not completed",
                    modifier = Modifier.size(12.dp),
                    tint = tier
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${formatMinuteOfDay(todo.deadlineMinuteOfDay ?: 0)}  ${todo.title}",
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (todo.reminderOffsetMinutes != null && !done) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = "Alarm set",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Used time: a solid card in the task's kind colour, in front of the flat blocks because this
 * actually happened. Text flips to black on the lighter kinds (Banana, Tangerine). Consumes its
 * tap so touching a task never reads as "add a block here". */
@Composable
private fun TaskSegmentCard(task: Task, isRunning: Boolean, modifier: Modifier) {
    val kindColor = Color(task.kind.colorValue)
    val textColor = if (kindColor.luminance() > 0.5f) Color.Black else Color.White
    Surface(
        modifier = modifier.clickable(onClick = {}),
        shape = RoundedCornerShape(6.dp),
        color = kindColor.copy(alpha = 0.92f),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp)) {
            Text(
                task.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isRunning) {
                Text(
                    "running",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

// ---- Block dialog -----------------------------------------------------------------------------

@Composable
private fun ScheduleBlockDialog(
    block: ScheduleBlock,
    isNew: Boolean,
    taskTypes: List<TaskType>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, TaskKind, String?, Long, Int, Int, Boolean, String) -> Unit
) {
    var title by remember { mutableStateOf(block.title) }
    var kind by remember { mutableStateOf(block.kind) }
    var taskTypeId by remember { mutableStateOf(block.taskTypeId) }
    var dayStart by remember { mutableStateOf(block.dayStart) }
    var startMinute by remember { mutableStateOf(block.startMinuteOfDay) }
    var endMinute by remember { mutableStateOf(block.endMinuteOfDay) }
    var repeatWeekly by remember { mutableStateOf(block.repeatWeekly) }
    var notes by remember { mutableStateOf(block.notes) }
    val context = LocalContext.current
    val spanValid = endMinute > startMinute
    val weekdayName = remember(dayStart) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(dayStart)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Block" else "Edit Block") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is this time for?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                // Colour only -- a block never scores. The chip is the familiar way to pick one.
                TaskKindDropdownMenu(selectedKind = kind, onKindSelected = { kind = it })
                // The activity this hour is for. Carried onto the task when Today's Now card
                // starts one from the block; left unset, the task takes the title's learned type.
                TaskTypeDropdownMenu(
                    selectedTypeId = taskTypeId,
                    taskTypes = taskTypes,
                    onTypeSelected = { taskTypeId = it }
                )
                PickerRow(
                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = "${getDayLabel(dayStart)} · ${formatSimpleDate(dayStart)}",
                    onClick = { showDatePicker(context, dayStart) { dayStart = it } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerRow(
                        icon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = "From ${formatMinuteOfDay(startMinute)}",
                        onClick = {
                            showTimePicker(context, startMinute) { picked ->
                                startMinute = picked
                                // Keep the block an hour long when the start jumps past the end.
                                if (endMinute <= picked) endMinute = minOf(picked + 60, 24 * 60)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PickerRow(
                        icon = null,
                        text = "To ${formatMinuteOfDay(endMinute)}",
                        onClick = {
                            // 00:00 as an end can only mean the end of the day.
                            showTimePicker(context, if (endMinute == 24 * 60) 0 else endMinute) { picked ->
                                endMinute = if (picked == 0) 24 * 60 else picked
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!spanValid) {
                    Text(
                        "The end has to come after the start",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Repeat weekly")
                        Text(
                            if (repeatWeekly) "Every $weekdayName from this date on" else "Just this day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = repeatWeekly, onCheckedChange = { repeatWeekly = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, kind, taskTypeId, dayStart, startMinute, endMinute, repeatWeekly, notes) },
                enabled = title.isNotBlank() && spanValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun PickerRow(
    icon: (@Composable () -> Unit)?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
