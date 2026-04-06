package com.inventoria.app.ui.screens.task

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProductivityPieChart(
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    currentTime: Long = System.currentTimeMillis(),
    strokeWidthDp: Int = 12
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
            
            val startAngle = ((start - todayStart).toFloat() / dayDuration.toFloat()) * 360f - 90f
            val sweepAngle = ((end - start).toFloat() / dayDuration.toFloat()) * 360f
            
            ChartSegment(
                color = Color(task.kind.colorValue),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                name = task.name
            )
        }.sortedBy { it.startAngle }

        val clusters = mutableListOf<MutableList<ChartSegment>>()
        var currentCluster = mutableListOf<ChartSegment>()
        var currentClusterEnd = -1000f

        for (segment in initialSegments) {
            if (segment.startAngle >= currentClusterEnd) {
                if (currentCluster.isNotEmpty()) {
                    clusters.add(currentCluster)
                }
                currentCluster = mutableListOf(segment)
                currentClusterEnd = segment.startAngle + segment.sweepAngle
            } else {
                currentCluster.add(segment)
                currentClusterEnd = maxOf(currentClusterEnd, segment.startAngle + segment.sweepAngle)
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        for (cluster in clusters) {
            val tracks = mutableListOf<MutableList<ChartSegment>>()
            for (segment in cluster) {
                var placed = false
                for (i in tracks.indices) {
                    val track = tracks[i]
                    val lastSegment = track.last()
                    val lastEndAngle = lastSegment.startAngle + lastSegment.sweepAngle
                    if (segment.startAngle >= lastEndAngle) {
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
            for (segment in cluster) {
                segment.maxTracks = maxTracks
            }
        }

        initialSegments
    }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "pie_animation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = strokeWidthDp.dp.toPx()
            val innerRadius = (size.minDimension - strokeWidth) / 2
            val arcSize = Size(innerRadius * 2, innerRadius * 2)
            val topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
            
            // 1. Background Track: Future/Remaining time in the day (Light Gray)
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.2f),
                radius = innerRadius,
                style = Stroke(width = strokeWidth)
            )

            // 2. Passed Time Track: "Blacked out" untracked time
            // Represents the time from midnight until NOW.
            val passedTimeSweep = ((currentTime - todayStart).toFloat() / dayDuration.toFloat()) * 360f
            drawArc(
                color = Color.Black.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = passedTimeSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                size = arcSize,
                topLeft = topLeft
            )

            // Hour marks for the 24h clock face
            for (i in 0 until 24) {
                val angle = (i * 15f - 90f) * (Math.PI / 180f).toFloat()
                val startRadius = innerRadius + (strokeWidth / 2)
                val endRadius = innerRadius - (strokeWidth / 2)
                
                val startOffset = Offset(
                    center.x + startRadius * cos(angle),
                    center.y + startRadius * sin(angle)
                )
                val endOffset = Offset(
                    center.x + endRadius * cos(angle),
                    center.y + endRadius * sin(angle)
                )
                
                drawOutlineHourMark(
                    startOffset, 
                    endOffset, 
                    if (i % 6 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    if (i % 6 == 0) Color.Gray else Color.LightGray.copy(alpha = 0.5f)
                )
            }

            // 3. Task Productivity Segments: Overlays the black "passed" track
            segments.forEach { segment ->
                val trackWidth = strokeWidth / segment.maxTracks
                val trackRadius = innerRadius - strokeWidth / 2f + (segment.trackIndex + 0.5f) * trackWidth
                val trackSize = Size(trackRadius * 2, trackRadius * 2)
                val trackTopLeft = Offset(center.x - trackRadius, center.y - trackRadius)

                drawArc(
                    color = segment.color,
                    startAngle = segment.startAngle,
                    sweepAngle = segment.sweepAngle * animationProgress,
                    useCenter = false,
                    style = Stroke(width = trackWidth, cap = StrokeCap.Round),
                    size = trackSize,
                    topLeft = trackTopLeft
                )
            }
        }

        var totalTrackedDegrees = 0.0
        var currentStart = -1000f
        var currentEnd = -1000f
        for (segment in segments) {
            if (segment.startAngle > currentEnd) {
                if (currentEnd > -1000f) {
                    totalTrackedDegrees += (currentEnd - currentStart)
                }
                currentStart = segment.startAngle
                currentEnd = segment.startAngle + segment.sweepAngle
            } else {
                currentEnd = maxOf(currentEnd, segment.startAngle + segment.sweepAngle)
            }
        }
        if (currentEnd > -1000f) {
            totalTrackedDegrees += (currentEnd - currentStart)
        }

        val percentage = minOf((totalTrackedDegrees / 360.0 * 100.0).toInt(), 100)
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "tracked",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DailyProductivityDialog(
    tasks: List<Task>,
    totalScore: Int,
    personalScore: Int,
    socialScore: Int,
    onDismiss: () -> Unit
) {
    var expandedBreakdown by remember { mutableStateOf(false) }
    val currentTime = System.currentTimeMillis()
    val todayStart = remember(currentTime) {
        Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val taskBreakdown = remember(tasks) {
        tasks.groupBy { it.kind }
            .map { (kind, kindTasks) ->
                val totalDuration = kindTasks.sumOf { 
                    val start = maxOf(it.startTime, todayStart)
                    val end = minOf(it.endTime ?: currentTime, todayStart + 24 * 60 * 60 * 1000L)
                    if (end > start) end - start else 0L
                }
                val totalPoints = kindTasks.sumOf { it.score }
                Triple(kind, totalDuration, totalPoints)
            }.sortedByDescending { it.second }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Overview",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProductivityPieChart(
                        tasks = tasks,
                        modifier = Modifier.size(240.dp),
                        strokeWidthDp = 24,
                        currentTime = currentTime
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScoreSummaryItem("Daily Score", totalScore, MaterialTheme.colorScheme.primary)
                    ScoreSummaryItem("Personal", personalScore, PurplePrimary)
                    ScoreSummaryItem("Social", socialScore, Success)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedBreakdown = !expandedBreakdown }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Activity Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (expandedBreakdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = expandedBreakdown) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (taskBreakdown.isEmpty()) {
                                    Text(
                                        text = "No activities tracked yet today.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                taskBreakdown.forEach { (kind, duration, points) ->
                                    BreakdownRow(kind, duration, points)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreSummaryItem(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (score >= 0) "+$score" else "$score",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
fun BreakdownRow(kind: TaskKind, duration: Long, points: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(kind.colorValue))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.displayName.substringAfter(" "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDetailedDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (points >= 0) "+$points" else "$points",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (points >= 0) Success else Color(0xFFFF4D4D)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutlineHourMark(
    start: Offset,
    end: Offset,
    width: Float,
    color: Color
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

data class ChartSegment(
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float,
    val name: String,
    var trackIndex: Int = 0,
    var maxTracks: Int = 1
)
