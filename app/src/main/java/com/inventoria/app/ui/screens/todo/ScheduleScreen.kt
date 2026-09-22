package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
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
import com.inventoria.app.ui.screens.task.taskCategoryColor
import com.inventoria.app.ui.screens.task.todoPriorityTierColor
import com.inventoria.app.util.currentMinuteOfDay
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import com.inventoria.app.ui.components.CardFrame
import com.inventoria.app.ui.components.TimelineZoomControls
import com.inventoria.app.ui.components.carveShapes
import com.inventoria.app.ui.components.pinchToZoom
import com.inventoria.app.ui.components.rememberTimelineZoom
import com.inventoria.app.ui.components.tickMinutes
import com.inventoria.app.util.layoutOverlaps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Vertical scale of the day timeline at 100% zoom. 64dp an hour puts a 15-minute block at 16dp -- still
 * a legible bar -- and the whole day at 1536dp, about three screens of scrolling. Pinching or the zoom
 * buttons scale it (see TimelineZoom). */
private val BASE_HOUR_HEIGHT = 64.dp
private val GUTTER_WIDTH = 44.dp

/** The day list's item count and the index that represents "today" at the screen's first
 * composition -- comfortably wide (~270 years each way) that no one will ever scroll past it, and
 * cheap regardless of width since LazyColumn only ever composes what's actually on screen. */
private const val DAY_INDEX_ANCHOR = 100_000
private const val DAY_INDEX_COUNT = 200_001

/** A day item's header -- the date label plus the all-day strip -- is always this tall, whether or
 * not that day actually has an all-day todo to show: a constant lets the zoom math (see
 * ScheduleScreen's zoomAround) work out where a day's canvas starts without depending on data
 * that might not have loaded yet, at the cost of a little empty space on a light day. */
private val DATE_LABEL_HEIGHT = 32.dp
private val ALL_DAY_STRIP_HEIGHT = 40.dp
private val DAY_HEADER_HEIGHT = DATE_LABEL_HEIGHT + ALL_DAY_STRIP_HEIGHT

/**
 * The Schedule segment: a week strip to pick a day, and a vertical list of 24-hour timelines below
 * it that scrolls forever in both directions rather than paging a single selected day -- the
 * timeline continues seamlessly across midnight, and scrolling into the next or previous day is
 * just... scrolling. [DAY_INDEX_COUNT] items exist so LazyColumn has a stable, indexable range to
 * scroll within, but it only ever composes what's actually near the viewport -- at this screen's
 * hour heights that's at most two day items at once, the current one and a sliver of its neighbour.
 *
 * Schedule blocks are painted flat and translucent across the full width, as if drawn on the
 * calendar paper itself -- they are what the time was *for*. Tracked task segments sit in front
 * as solid cards on the right half of the timeline, past a divider line -- what the time was
 * *used* for -- so the block underneath still shows through on the left. Todos due at a time are
 * hairlines across everything.
 *
 * Blocks are created by tapping an empty hour (or the FAB) and edited by tapping the block. Todos
 * here are read-mostly: tapping one ticks it off, editing stays on the Todos segment (see
 * ScheduleViewModel's KDoc). Tasks are display only here too -- tapping one hands off to the
 * tracker's own task edit screen via [onOpenTaskDetail] rather than opening anything locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel, onOpenTaskDetail: (String) -> Unit) {
    val weekDays by viewModel.weekDays.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val pendingBlock by viewModel.pendingBlock.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val taskTypeNames by viewModel.taskTypeNamesById.collectAsState()
    val rawData by viewModel.rawData.collectAsState()
    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }
    // Same once-a-minute ticker TodoScreen runs: moves the now-line and grows a running task.
    val nowMinuteOfDay by produceState(currentMinuteOfDay()) {
        while (true) {
            delay(60_000)
            value = currentMinuteOfDay()
        }
    }

    fun dayForIndex(index: Int): Long = plusDays(todayStart, index - DAY_INDEX_ANCHOR)
    fun indexForDay(day: Long): Int = DAY_INDEX_ANCHOR + daysBetween(todayStart, day)

    val zoom = rememberTimelineZoom("schedule")
    val hourHeight = BASE_HOUR_HEIGHT * zoom.scale
    val tick = tickMinutes(hourHeight.value)
    val density = LocalDensity.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = DAY_INDEX_ANCHOR)
    val scope = rememberCoroutineScope()

    suspend fun scrollToDay(dayStart: Long, animate: Boolean) {
        val index = indexForDay(dayStart)
        val offset = if (dayStart == todayStart) {
            val headerPx = with(density) { DAY_HEADER_HEIGHT.toPx() }
            val minute = (nowMinuteOfDay - 60).coerceAtLeast(0)
            (headerPx + minute / 60f * with(density) { hourHeight.toPx() }).roundToInt()
        } else 0
        if (animate) listState.animateScrollToItem(index, offset) else listState.scrollToItem(index, offset)
    }

    LaunchedEffect(Unit) { scrollToDay(todayStart, animate = false) }
    // A WeekStrip tap or the Today button: scroll there. Deliberately one-directional -- the
    // listener below feeds the *other* way, updating selectedDay as the user scrolls, and must
    // not loop back into asking for a scroll of its own.
    LaunchedEffect(listState) {
        viewModel.scrollToDayRequests.collect { day -> scrollToDay(day, animate = true) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> viewModel.trackVisibleDay(dayForIndex(index)) }
    }

    // Keeps whatever's under a pinch (or, from the zoom buttons, the middle of the screen) where
    // it is: find which day item focalY falls in, work out the minute of that day under it at the
    // old scale, then scroll so that same day+minute lands under it again at the new scale.
    var viewportHeight by remember { mutableIntStateOf(0) }
    fun zoomAround(focalY: Float, change: () -> Unit) {
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { focalY >= it.offset && focalY < it.offset + it.size } ?: return
        val oldHourHeightPx = with(density) { hourHeight.toPx() }
        change()
        val newHourHeightPx = with(density) { (BASE_HOUR_HEIGHT * zoom.scale).toPx() }
        val headerPx = with(density) { DAY_HEADER_HEIGHT.toPx() }
        val localY = focalY - item.offset
        if (localY < headerPx) return // pinched the header -- nothing timed to preserve there
        val minuteAtFocal = (localY - headerPx) / oldHourHeightPx * 60f
        val newLocalY = headerPx + minuteAtFocal / 60f * newHourHeightPx
        val targetOffset = (newLocalY - (focalY - item.offset)).roundToInt().coerceAtLeast(0)
        scope.launch { listState.scrollToItem(item.index, targetOffset) }
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
                onSelect = { viewModel.goToDay(it) },
                onShiftWeek = { viewModel.shiftWeek(it) }
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewportHeight = it.height }
                    .pinchToZoom { factor, focalY -> zoomAround(focalY) { zoom.zoomBy(factor) } }
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(count = DAY_INDEX_COUNT, key = { index -> dayForIndex(index) }) { index ->
                        val dayStart = dayForIndex(index)
                        val scheduleDay = remember(dayStart, rawData) { viewModel.buildDay(dayStart, rawData) }
                        Column(Modifier.fillMaxWidth()) {
                            DayHeaderLabel(dayStart, isToday = dayStart == todayStart)
                            // Always shown, even with nothing due -- so every day item reserves the
                            // same header height (see DAY_HEADER_HEIGHT) and the canvas below always
                            // starts at the same offset, which the zoom/scroll math above counts on.
                            AllDayTodoStrip(scheduleDay.allDayTodos, todayStart = todayStart, onToggle = { viewModel.toggleTodoComplete(it) })
                            DayCanvas(
                                day = scheduleDay,
                                isToday = dayStart == todayStart,
                                todayStart = todayStart,
                                nowMinuteOfDay = nowMinuteOfDay,
                                hourHeight = hourHeight,
                                tick = tick,
                                onTapEmptyMinute = { minute -> viewModel.startAddingBlock(dayStart, (minute / 60) * 60) },
                                taskTypeNames = taskTypeNames,
                                onBlockClick = { viewModel.startEditingBlock(it) },
                                onTodoClick = { viewModel.toggleTodoComplete(it) },
                                onTaskClick = { onOpenTaskDetail(it.id) }
                            )
                        }
                    }
                }
                TimelineZoomControls(
                    state = zoom,
                    onZoomOut = { zoomAround(viewportHeight / 2f) { zoom.stepOut() } },
                    onZoomIn = { zoomAround(viewportHeight / 2f) { zoom.stepIn() } },
                    onReset = { zoomAround(viewportHeight / 2f) { zoom.reset() } },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }
        }
    }

    pendingBlock?.let { block ->
        ScheduleBlockDialog(
            block = block,
            isNew = block.id.isBlank(),
            taskTypes = taskTypes,
            onDismiss = { viewModel.dismissDialog() },
            onDelete = { viewModel.deleteBlock(block) },
            onSave = { title, kind, taskTypeId, dayStart, start, end, repeat, notes ->
                viewModel.saveBlock(title, kind, taskTypeId, dayStart, start, end, repeat, notes)
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
 * sit in a strip above it -- one chip each, tinted by priority tier, tap to tick off. A fixed
 * height ([ALL_DAY_STRIP_HEIGHT]) regardless of whether there's anything in it, unlike its old
 * single-day self: every day item in the scroller reserves the same header height either way (see
 * [DAY_HEADER_HEIGHT]), which the zoom/scroll math in ScheduleScreen counts on. */
@Composable
private fun AllDayTodoStrip(todos: List<Todo>, todayStart: Long, onToggle: (Todo) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ALL_DAY_STRIP_HEIGHT)
            .padding(horizontal = 12.dp)
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
            // Same "overdue" rule as TodoRow: an all-day todo shown here is only ever on the exact
            // day it's due, so it's overdue precisely when that day is already behind us.
            val isOverdue = !done && todo.deadline != null && todo.deadline!! < todayStart
            val tier = if (isOverdue) MaterialTheme.colorScheme.error else taskCategoryColor(todo.kind.category)
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

/** The top stretch of a task card where its name sits; a notch reaching into it narrows the text. */
private val TASK_TEXT_BAND = 34.dp

/** Room a task card needs for its name; shorter ones are a bar without text. */
private val TASK_TITLE_HEIGHT = 18.dp

/** A task nests in a bigger one only if it started this long after it -- a title row at 100% zoom. */
private val TASK_NEST_AFTER_MINUTES = TASK_TITLE_HEIGHT.value / BASE_HOUR_HEIGHT.value * 60f

/** Thinnest a task card is drawn, so a task of a minute or two still shows. */
private val MIN_TASK_HEIGHT = 3.dp

/** A todo due-marker's rendered height (hairline + its label row) -- when two are due close
 * enough together that they'd otherwise land within this of each other, the lane below cascades
 * the later one down by roughly this much instead of letting their labels overlap. */
private val TODO_MARKER_HEIGHT = 22.dp

/** A day item's header in the vertical scroller: "Today" / "Yesterday" / "Tomorrow" close by,
 * else a plain date -- the same rule [getDayLabel] already gives the block dialog. Fixed-height
 * (see [DATE_LABEL_HEIGHT]) so the zoom math above can account for it without measuring it. */
@Composable
private fun DayHeaderLabel(dayStart: Long, isToday: Boolean) {
    Text(
        text = getDayLabel(dayStart),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (isToday) FontWeight.Bold else null,
        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(DATE_LABEL_HEIGHT)
            .padding(horizontal = 12.dp)
            .wrapContentHeight()
    )
}

/** One day's 24-hour grid -- gridlines, hour labels, the blocks/tasks/todos lane, and the now-line
 * on today. Unlike the old single-day screen this has no scrolling or zoom controls of its own:
 * it's one item in the caller's LazyColumn, which is what actually scrolls, and hourHeight/zoom are
 * hoisted there too so every day item shares the same scale. */
@Composable
private fun DayCanvas(
    day: ScheduleDay,
    isToday: Boolean,
    todayStart: Long,
    nowMinuteOfDay: Int,
    hourHeight: Dp,
    tick: Int,
    onTapEmptyMinute: (Int) -> Unit,
    taskTypeNames: Map<String, String>,
    onBlockClick: (ScheduleBlock) -> Unit,
    onTodoClick: (Todo) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.error

    Box(
        Modifier
            .fillMaxWidth()
            .height(hourHeight * 24)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val gutter = GUTTER_WIDTH.toPx()
            val stroke = 1.dp.toPx()
            val hourHeightPx = hourHeight.toPx()
            for (minute in 0..24 * 60 step tick) {
                val y = minute / 60f * hourHeightPx
                val color = if (minute % 60 == 0) gridColor else gridColor.copy(alpha = 0.45f)
                drawLine(color, Offset(gutter, y), Offset(size.width, y), stroke)
            }
            // Splits the day into a blocks side and a tasks side, behind everything else.
            val mid = gutter + (size.width - gutter) / 2f
            drawLine(gridColor, Offset(mid, 0f), Offset(mid, size.height), stroke)
        }
        // 24:00 has no label of its own; the day ends at the last hour.
        for (minute in 0 until 24 * 60 step tick) {
            Text(
                text = "%02d:%02d".format(minute / 60, minute % 60),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(GUTTER_WIDTH)
                    .offset(y = hourHeight * (minute / 60f) - 7.dp)
                    .padding(end = 4.dp),
                textAlign = TextAlign.End
            )
        }
        DayLane(
            day = day,
            todayStart = todayStart,
            nowMinuteOfDay = nowMinuteOfDay,
            hourHeight = hourHeight,
            onTapEmptyMinute = onTapEmptyMinute,
            taskTypeNames = taskTypeNames,
            onBlockClick = onBlockClick,
            onTodoClick = onTodoClick,
            onTaskClick = onTaskClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = GUTTER_WIDTH)
        )
        if (isToday) {
            val y = hourHeight * (nowMinuteOfDay / 60f)
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

/**
 * The single lane, back to front: flat blocks across the full width, then task cards confined to
 * the right half (laid out among themselves by real time, stopping short of the right-edge strip), then todo
 * hairlines over everything. Compose draws children in order, so this ordering is the layering.
 */
@Composable
private fun DayLane(
    day: ScheduleDay,
    todayStart: Long,
    nowMinuteOfDay: Int,
    hourHeight: Dp,
    onTapEmptyMinute: (Int) -> Unit,
    taskTypeNames: Map<String, String>,
    onBlockClick: (ScheduleBlock) -> Unit,
    onTodoClick: (Todo) -> Unit,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    // Blocks, tasks and todo markers each consume their own taps before they reach this, so
    // whatever arrives here really did land on empty paper.
    BoxWithConstraints(
        modifier.pointerInput(hourHeightPx) {
            detectTapGestures { offset ->
                onTapEmptyMinute(((offset.y / hourHeightPx) * 60).toInt().coerceIn(0, 24 * 60 - 1))
            }
        }
    ) {
        val fullWidth = maxWidth
        val taskLaneStart = fullWidth / 2
        val taskAreaWidth = (fullWidth - taskLaneStart) - PEEK_STRIP_WIDTH

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
            val top = hourHeight * (block.startMinuteOfDay / 60f)
            val height = hourHeight * ((block.endMinuteOfDay - block.startMinuteOfDay) / 60f)
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
            // Settled on the minutes alone, so zooming never rearranges the cards.
            layoutOverlaps(
                resolved,
                start = { it.first.startMinute },
                end = { it.second },
                nestAfter = TASK_NEST_AFTER_MINUTES
            )
        }
        val frames = remember(slots, taskAreaWidth, hourHeight) {
            slots.map { slot ->
                val (segment, endMinute) = slot.item
                CardFrame(
                    left = taskAreaWidth * slot.left,
                    top = hourHeight * (segment.startMinute / 60f),
                    width = taskAreaWidth * (slot.right - slot.left),
                    height = maxOf(hourHeight * ((endMinute - segment.startMinute) / 60f), MIN_TASK_HEIGHT)
                )
            }
        }
        val shapes = remember(slots, frames) { carveShapes(slots.map { it.parent }, frames) }
        slots.forEachIndexed { index, slot ->
            val segment = slot.item.first
            val frame = frames[index]
            TaskSegmentCard(
                task = segment.task,
                isRunning = segment.endMinute == null,
                showText = frame.height >= TASK_TITLE_HEIGHT,
                shape = shapes[index],
                endInset = shapes[index].endInset(TASK_TEXT_BAND, frame.width),
                modifier = Modifier
                    .offset(x = taskLaneStart + frame.left, y = frame.top)
                    .width(frame.width)
                    .height(frame.height)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                onClick = { onTaskClick(segment.task) }
            )
        }

        // Todos: a deadline is a moment, so a hairline across everything at that minute -- except
        // when several land close together at the current zoom, where stacking them all at their
        // exact minute would bury every label but the last under one another. A crowded cluster
        // cascades downward instead, one marker's height at a time, the same idea the lane above
        // already uses for overlapping tasks.
        val markerHeightPx = with(density) { TODO_MARKER_HEIGHT.toPx() }
        val todoOffsetPx = remember(day.timedTodos, hourHeightPx) {
            var nextFreeY = Float.NEGATIVE_INFINITY
            day.timedTodos
                .filter { it.deadlineMinuteOfDay != null }
                .sortedBy { it.deadlineMinuteOfDay }
                .associateWith { todo ->
                    val naturalY = hourHeightPx * (todo.deadlineMinuteOfDay!! / 60f)
                    val y = maxOf(naturalY, nextFreeY)
                    nextFreeY = y + markerHeightPx
                    y
                }
        }
        day.timedTodos.forEach { todo ->
            val minute = todo.deadlineMinuteOfDay ?: return@forEach
            val done = todo.state == TodoState.COMPLETE
            // Same overdue/late-today rule as TodoRow: a whole day behind us, or today's own
            // moment already passed on the clock.
            val isOverdue = !done && todo.deadline != null && todo.deadline!! < todayStart
            val isLateToday = !done && todo.deadline == todayStart && minute < nowMinuteOfDay
            val y = with(density) { (todoOffsetPx.getValue(todo)).toDp() }
            TodoDueMarker(
                todo = todo,
                isDue = isOverdue || isLateToday,
                modifier = Modifier
                    .offset(y = y - 1.dp)
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
                if (block.repeatDaily || block.repeatWeekly) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = if (block.repeatDaily) "Repeats daily" else "Repeats weekly",
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
private fun TodoDueMarker(todo: Todo, isDue: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tier = if (isDue) MaterialTheme.colorScheme.error else taskCategoryColor(todo.kind.category)
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
 * actually happened. Text flips to black on the lighter kinds (Banana, Tangerine). Tapping one
 * opens that task's edit screen on the Task Tracker tab. */
@Composable
private fun TaskSegmentCard(
    task: Task,
    isRunning: Boolean,
    showText: Boolean,
    shape: Shape,
    endInset: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val kindColor = Color(task.kind.colorValue)
    val textColor = if (kindColor.luminance() > 0.5f) Color.Black else Color.White
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = kindColor.copy(alpha = 0.92f),
        shadowElevation = 1.dp
    ) {
        if (showText) Column(Modifier.padding(start = 5.dp, top = 3.dp, end = 5.dp + endInset, bottom = 3.dp)) {
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
    onSave: (String, TaskKind, String?, Long, Int, Int, BlockRepeat, String) -> Unit
) {
    var title by remember { mutableStateOf(block.title) }
    var kind by remember { mutableStateOf(block.kind) }
    var taskTypeId by remember { mutableStateOf(block.taskTypeId) }
    var dayStart by remember { mutableStateOf(block.dayStart) }
    var startMinute by remember { mutableStateOf(block.startMinuteOfDay) }
    var endMinute by remember { mutableStateOf(block.endMinuteOfDay) }
    var repeat by remember {
        mutableStateOf(
            when {
                block.repeatDaily -> BlockRepeat.DAILY
                block.repeatWeekly -> BlockRepeat.WEEKLY
                else -> BlockRepeat.NONE
            }
        )
    }
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Repeat")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockRepeat.values().forEach { option ->
                            FilterChip(
                                selected = repeat == option,
                                onClick = { repeat = option },
                                label = {
                                    Text(
                                        when (option) {
                                            BlockRepeat.NONE -> "Never"
                                            BlockRepeat.DAILY -> "Daily"
                                            BlockRepeat.WEEKLY -> "Weekly"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Text(
                        when (repeat) {
                            BlockRepeat.NONE -> "Just this day"
                            BlockRepeat.DAILY -> "Every day from this date on"
                            BlockRepeat.WEEKLY -> "Every $weekdayName from this date on"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                onClick = { onSave(title, kind, taskTypeId, dayStart, startMinute, endMinute, repeat, notes) },
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
