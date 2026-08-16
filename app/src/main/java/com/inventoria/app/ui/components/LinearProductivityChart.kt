package com.inventoria.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import java.util.Calendar

/**
 * A 24-hour timeline of today's tracked sessions: each task is a coloured bar positioned by its
 * start/end within the day, with the elapsed part of the day shaded and a line at the current time.
 *
 * Overlapping sessions are clustered and lane-packed so concurrent tasks stack vertically instead
 * of painting over each other -- a cluster's height is divided by however many tracks that cluster
 * needs, so an hour with three simultaneous tasks shows three thin bars while the rest of the day
 * stays full height.
 *
 * NOTE: the palette here (white track, black elapsed shade, white now-line and hour labels) assumes
 * this is drawn on a saturated background -- it is near-invisible on a plain light surface. Keep it
 * inside a gradient/coloured container, or parameterise the colours before moving it somewhere flat.
 */
@Composable
fun LinearProductivityChart(
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    currentTime: Long = System.currentTimeMillis()
) {
    val todayStart = remember(currentTime) {
        Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayDuration = 24 * 60 * 60 * 1000L

    val segments = remember(tasks, todayStart, currentTime) {
        val todayTasks = tasks.filter {
            val end = it.endTime ?: currentTime
            it.startTime < todayStart + dayDuration && end > todayStart
        }

        val initialSegments = todayTasks.map { task ->
            val start = maxOf(task.startTime, todayStart)
            val end = minOf(task.endTime ?: currentTime, todayStart + dayDuration)
            val startRatio = (start - todayStart).toFloat() / dayDuration.toFloat()
            val endRatio = (end - todayStart).toFloat() / dayDuration.toFloat()

            ProductivityChartSegment(
                color = Color(task.kind.colorValue),
                startRatio = startRatio,
                endRatio = endRatio
            )
        }.sortedBy { it.startRatio }

        val clusters = mutableListOf<MutableList<ProductivityChartSegment>>()
        var currentCluster = mutableListOf<ProductivityChartSegment>()
        var currentClusterEnd = -1f

        for (segment in initialSegments) {
            if (segment.startRatio >= currentClusterEnd) {
                if (currentCluster.isNotEmpty()) clusters.add(currentCluster)
                currentCluster = mutableListOf(segment)
                currentClusterEnd = segment.endRatio
            } else {
                currentCluster.add(segment)
                currentClusterEnd = maxOf(currentClusterEnd, segment.endRatio)
            }
        }
        if (currentCluster.isNotEmpty()) clusters.add(currentCluster)

        for (cluster in clusters) {
            val tracks = mutableListOf<MutableList<ProductivityChartSegment>>()
            for (segment in cluster) {
                var placed = false
                for (i in tracks.indices) {
                    val track = tracks[i]
                    if (segment.startRatio >= track.last().endRatio) {
                        track.add(segment)
                        segment.trackIndex = i
                        placed = true
                        break
                    }
                }
                if (!placed) {
                    segment.trackIndex = tracks.size
                    tracks.add(mutableListOf(segment))
                }
            }
            val maxTracks = tracks.size
            for (segment in cluster) segment.maxTracks = maxTracks
        }
        initialSegments
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Background Track
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    size = size
                )

                // Passed time indicator (slightly darker)
                val passedRatio = (currentTime - todayStart).toFloat() / dayDuration.toFloat()
                drawRect(
                    color = Color.Black.copy(alpha = 0.1f),
                    size = Size(width * passedRatio, height)
                )

                // Task segments
                segments.forEach { segment ->
                    val trackHeight = height / segment.maxTracks
                    val yOffset = segment.trackIndex * trackHeight
                    drawRect(
                        color = segment.color,
                        topLeft = Offset(width * segment.startRatio, yOffset),
                        size = Size(width * (segment.endRatio - segment.startRatio), trackHeight)
                    )
                }

                // Current time line
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(width * passedRatio, 0f),
                    end = Offset(width * passedRatio, height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Hour marks (6h intervals)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { time ->
                Text(
                    text = time,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private data class ProductivityChartSegment(
    val color: Color,
    val startRatio: Float,
    val endRatio: Float,
    var trackIndex: Int = 0,
    var maxTracks: Int = 1
)
