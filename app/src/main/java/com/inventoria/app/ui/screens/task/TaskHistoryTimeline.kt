package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.components.CardFrame
import com.inventoria.app.ui.components.carveShapes
import com.inventoria.app.ui.components.tickMinutes
import com.inventoria.app.util.layoutOverlaps
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Task History's flat view, one day at a time, on a fixed time scale: a task's card is as tall as
 * the task was long ([BASE_HOUR_HEIGHT] per hour at 100% zoom, the same for every card on every day)
 * and sits at the clock time it actually started -- to the minute: a card never covers time its task was not
 * running. Tasks that ran at the same time sit side by side, and one that ran entirely inside
 * another is nested in it, taking a notch out of the parent's card so nothing draws over anything
 * else (see [layoutOverlaps] and CarvedShape). Overlaps are worked out from the real times only.
 *
 * Pinch (or the zoom buttons) scales the hour height; finer gridlines and labels appear as you zoom
 * in. Zoom changes only the vertical scale -- the layout is settled on minutes, so it never
 * rearranges.
 *
 * Two things keep a fixed scale usable. Only the stretch of the day that has tasks in it is drawn
 * (from the hour before the first to the hour after the last), and any quiet stretch of
 * [COLLAPSE_GAP_MINUTES] or more between tasks folds down to a one-line band saying how long it
 * was, so a morning and an evening entry don't drag a mostly-empty afternoon between them. A task too
 * short to fit its name is drawn as a thin bar without one; tap it for the details.
 *
 * Only tasks that start on [dayStart] are drawn here, clipped at midnight -- the same "belongs to
 * the day it started" rule the rest of History uses.
 */
/** Height of an hour at 100% zoom; the timeline is drawn at this times the zoom (see TimelineZoom). */
private val BASE_HOUR_HEIGHT = 96.dp
private val GUTTER_WIDTH = 40.dp
private val COLLAPSED_GAP_HEIGHT = 32.dp
private const val COLLAPSE_GAP_MINUTES = 120
/** Thinnest a card is drawn, so a task of a minute or two still shows. */
private val MIN_DRAWN_HEIGHT = 3.dp

/** Room a card needs for its name row; shorter ones are a bar without text. */
private val NAME_ROW_HEIGHT = 22.dp

/** A task nests in a bigger one only if it started this long after it -- a title row at 100% zoom.
 * In minutes, not dp, so the layout does not change shape as you zoom. */
private val NEST_AFTER_MINUTES = NAME_ROW_HEIGHT.value / BASE_HOUR_HEIGHT.value * 60f

/** The top stretch of a card where its name and time sit; a notch reaching into it narrows them. */
private val TEXT_BAND = 40.dp

/** A system-calendar event's card only carries its open and hide buttons when it is this tall. */
private val CALENDAR_BUTTONS_MIN_HEIGHT = 32.dp

/** A stretch of the day that is drawn, aligned to whole hours. */
private data class DrawnRange(val startMinute: Int, val endMinute: Int)

private class DayScale(val ranges: List<DrawnRange>, val hourHeight: Float) {
    private val tops = ArrayList<Float>(ranges.size)
    val totalHeight: Dp

    init {
        var y = 0f
        ranges.forEachIndexed { index, range ->
            if (index > 0) y += COLLAPSED_GAP_HEIGHT.value
            tops.add(y)
            y += (range.endMinute - range.startMinute) / 60f * hourHeight
        }
        totalHeight = y.dp
    }

    /** Vertical position, in dp from the top of the timeline, of [minute]. */
    fun y(minute: Float): Float {
        val index = ranges.indexOfLast { minute >= it.startMinute }.coerceAtLeast(0)
        val range = ranges[index]
        val clamped = minute.coerceIn(range.startMinute.toFloat(), range.endMinute.toFloat())
        return tops[index] + (clamped - range.startMinute) / 60f * hourHeight
    }

    fun top(rangeIndex: Int): Float = tops[rangeIndex]

    /** Where the folded band before [rangeIndex] (>= 1) starts. */
    fun gapTop(rangeIndex: Int): Float = tops[rangeIndex] - COLLAPSED_GAP_HEIGHT.value
}

/** [task]'s span within its day, in minutes from midnight -- clipped to the day. */
private fun spanMinutes(task: Task, dayStart: Long): Pair<Float, Float> {
    val start = ((task.startTime - dayStart) / 60_000f).coerceIn(0f, 24 * 60f)
    val end = ((task.startTime + task.duration - dayStart) / 60_000f).coerceIn(start, 24 * 60f)
    return start to end
}

private fun buildScale(tasks: List<Task>, dayStart: Long, hourHeight: Float): DayScale {
    val wanted = tasks.map { task ->
        val (start, end) = spanMinutes(task, dayStart)
        val lo = (floor(start / 60f) * 60).toInt()
        val hi = maxOf((ceil(end / 60f) * 60).toInt(), lo + 60)
        DrawnRange(lo, hi)
    }.sortedBy { it.startMinute }
    val merged = mutableListOf<DrawnRange>()
    wanted.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.startMinute - last.endMinute < COLLAPSE_GAP_MINUTES) {
            merged[merged.lastIndex] = DrawnRange(last.startMinute, maxOf(last.endMinute, range.endMinute))
        } else {
            merged.add(range)
        }
    }
    return DayScale(merged, hourHeight)
}

/** A task with its span within the day, in minutes from midnight. */
private data class PlacedTask(val task: Task, val startMinute: Float, val endMinute: Float)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryDayTimeline(
    dayStart: Long,
    tasks: List<Task>,
    selectedTaskIds: Set<String>,
    taskTypeNames: Map<String, String>,
    onClick: (Task) -> Unit,
    onLongClick: (Task) -> Unit,
    onOpenCalendar: (Task) -> Unit,
    onHideCalendarItem: (Task) -> Unit,
    zoom: Float = 1f
) {
    if (tasks.isEmpty()) return
    val hourHeight = (BASE_HOUR_HEIGHT * zoom).value
    val scale = remember(tasks, dayStart, hourHeight) { buildScale(tasks, dayStart, hourHeight) }
    // Who overlaps whom is settled on the minutes alone, so zooming never rearranges the cards.
    val slots = remember(tasks, dayStart) {
        val placed = tasks.map { task ->
            val (start, end) = spanMinutes(task, dayStart)
            PlacedTask(task, start, end)
        }
        layoutOverlaps(placed, start = { it.startMinute }, end = { it.endMinute }, nestAfter = NEST_AFTER_MINUTES)
    }
    val tick = tickMinutes(hourHeight)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val gapColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(scale.totalHeight)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val gutter = GUTTER_WIDTH.toPx()
            val stroke = 1.dp.toPx()
            scale.ranges.forEachIndexed { index, range ->
                for (m in range.startMinute..range.endMinute step tick) {
                    val y = (scale.top(index) + (m - range.startMinute) / 60f * hourHeight).dp.toPx()
                    val color = if (m % 60 == 0) gridColor else gridColor.copy(alpha = 0.45f)
                    drawLine(color, Offset(gutter, y), Offset(size.width, y), stroke)
                }
                if (index > 0) {
                    drawRect(
                        color = gapColor,
                        topLeft = Offset(gutter, scale.gapTop(index).dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - gutter, COLLAPSED_GAP_HEIGHT.toPx())
                    )
                }
            }
        }

        scale.ranges.forEachIndexed { index, range ->
            for (m in range.startMinute..range.endMinute step tick) {
                Text(
                    text = "%02d:%02d".format((m / 60) % 24, m % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(GUTTER_WIDTH)
                        .offset(y = (scale.top(index) + (m - range.startMinute) / 60f * hourHeight).dp - 7.dp)
                        .padding(end = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            if (index > 0) {
                val previous = scale.ranges[index - 1]
                val gapMinutes = range.startMinute - previous.endMinute
                Box(
                    Modifier
                        .offset(y = scale.gapTop(index).dp)
                        .padding(start = GUTTER_WIDTH)
                        .fillMaxWidth()
                        .height(COLLAPSED_GAP_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${formatGap(gapMinutes)} with nothing tracked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(start = GUTTER_WIDTH)
        ) {
            val frames = remember(slots, scale, maxWidth) {
                slots.map {
                    val top = scale.y(it.item.startMinute)
                    CardFrame(
                        left = maxWidth * it.left,
                        top = top.dp,
                        width = maxWidth * (it.right - it.left),
                        height = maxOf((scale.y(it.item.endMinute) - top).dp, MIN_DRAWN_HEIGHT)
                    )
                }
            }
            val shapes = remember(slots, frames) { carveShapes(slots.map { it.parent }, frames) }
            slots.forEachIndexed { index, slot ->
                val placedTask = slot.item
                val frame = frames[index]
                HistoryTaskCard(
                    task = placedTask.task,
                    isSelected = placedTask.task.id in selectedTaskIds,
                    typeName = placedTask.task.taskTypeId?.let { taskTypeNames[it] },
                    modifier = Modifier
                        .offset(x = frame.left, y = frame.top)
                        .width(frame.width)
                        .height(frame.height)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    height = frame.height,
                    shape = shapes[index],
                    endInset = shapes[index].endInset(TEXT_BAND, frame.width),
                    onClick = { onClick(placedTask.task) },
                    onLongClick = { onLongClick(placedTask.task) },
                    onOpenCalendar = { onOpenCalendar(placedTask.task) },
                    onHideCalendarItem = { onHideCalendarItem(placedTask.task) }
                )
            }
        }
    }
}

private fun formatGap(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/**
 * One task on the timeline. What it shows depends on the room it has: the name always; the time
 * span and duration once it is tall enough for a second line; the type and points on a third. The
 * calendar save and delete buttons the list cards carry are not here -- the detail dialog a tap
 * opens has both, and long-press selects for bulk actions -- except on system-calendar events,
 * whose "open" and "hide" have no other home.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTaskCard(
    task: Task,
    isSelected: Boolean,
    typeName: String?,
    height: Dp,
    shape: Shape,
    endInset: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenCalendar: () -> Unit,
    onHideCalendarItem: () -> Unit
) {
    val kindColor = Color(task.kind.colorValue)
    val isCalendarTask = task.id.startsWith("cal_")
    val secondLine = height >= 44.dp
    val thirdLine = height >= 64.dp
    Surface(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else kindColor.copy(alpha = 0.18f),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(end = endInset), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else kindColor)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                if (height >= NAME_ROW_HEIGHT) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    } else if (task.savedToCalendar && !isCalendarTask) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.EventAvailable, "Saved to calendar", Modifier.size(12.dp), tint = Success)
                    }
                }
                if (secondLine) {
                    Text(
                        text = "${formatTimeOfDay(task.startTime)}–${formatTimeOfDay(task.startTime + task.duration)} · ${formatDetailedDuration(task.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (thirdLine) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (typeName != null) {
                            TaskTypeLabel(typeName)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = "${formatPoints(task.points)} pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositivePoints(task.points)) Success else Color(0xFFFF4D4D)
                        )
                    }
                }
            }
            if (isCalendarTask && height >= CALENDAR_BUTTONS_MIN_HEIGHT) {
                val buttonSize = if (height >= 44.dp) 32.dp else 28.dp
                IconButton(onClick = onOpenCalendar, modifier = Modifier.size(buttonSize)) {
                    Icon(Icons.Default.EventAvailable, "Open in Calendar", tint = Success, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onHideCalendarItem, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Remove from Inventoria (keeps the calendar event)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
