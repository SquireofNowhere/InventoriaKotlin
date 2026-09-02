package com.inventoria.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.util.getStartOfDay

/**
 * Today's tracked time by kind: a donut with one arc per kind, biggest first, and a legend.
 *
 * Not the same thing as ProductivityPieChart, which is a 24-hour clock ring positioned by time of
 * day. This answers "how much of today was what", not "when".
 *
 * Tasks are clipped to the current day the way LinearProductivityChart does -- a session that
 * started yesterday counts only its part after midnight, and a running one counts up to now.
 */
@Composable
fun KindBreakdownDonut(
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    currentTime: Long = System.currentTimeMillis()
) {
    val dayStart = remember(currentTime) { getStartOfDay(currentTime) }
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L

    val minutesByKind = remember(tasks, dayStart, currentTime) {
        val totals = mutableMapOf<TaskKind, Long>()
        tasks.forEach { task ->
            val end = task.endTime ?: currentTime
            if (task.startTime >= dayEnd || end <= dayStart) return@forEach
            val span = minOf(end, dayEnd) - maxOf(task.startTime, dayStart)
            if (span > 0) totals[task.kind] = (totals[task.kind] ?: 0L) + span
        }
        totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val totalMs = minutesByKind.sumOf { it.second }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.size(120.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                if (totalMs == 0L) {
                    drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    return@Canvas
                }
                // A small gap between arcs, only when there is more than one; a single kind is a
                // full ring.
                val gap = if (minutesByKind.size > 1) 2f else 0f
                var start = -90f
                minutesByKind.forEach { (kind, ms) ->
                    val sweep = 360f * ms / totalMs
                    val drawn = (sweep - gap).coerceAtLeast(0.5f)
                    drawArc(
                        color = Color(kind.colorValue),
                        startAngle = start + gap / 2,
                        sweepAngle = drawn,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDuration(totalMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "tracked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (minutesByKind.isEmpty()) {
                Text(
                    "Nothing tracked yet today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            minutesByKind.take(5).forEach { (kind, ms) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(kind.colorValue))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        // Same short form TaskKindChip shows: "Peak Performance", not the emoji.
                        kind.displayName.substringAfter(" • ", kind.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDuration(ms),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (minutesByKind.size > 5) {
                Text(
                    "+${minutesByKind.size - 5} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "2h 05m", "35m", "<1m". */
private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h %02dm".format(minutes)
        totalMinutes > 0 -> "${minutes}m"
        ms > 0 -> "<1m"
        else -> "0m"
    }
}
