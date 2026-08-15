package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskTypeStats
import com.inventoria.app.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel
) {
    // Includes already-finished (paused) segments of still-active sessions too, not just fully
    // stopped ones -- otherwise a session paused mid-way (e.g. lunch break) silently excluded its
    // already-worked portion from stats until the whole session eventually stopped.
    val allTasks by viewModel.allFinishedTasks.collectAsState()
    
    val personalScore by viewModel.personalScoreLifetime.collectAsState()
    val socialScore by viewModel.socialScoreLifetime.collectAsState()
    val totalScore by viewModel.totalScoreLifetime.collectAsState()
    val taskTypeStats by viewModel.taskTypeStats.collectAsState()
    val scoreBreakdown by viewModel.scoreBreakdownToday.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedKindForDetail by remember { mutableStateOf<TaskKind?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Productivity Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SummaryCard(
                totalScore = totalScore,
                personal = personalScore,
                social = socialScore,
                modifier = Modifier.padding(16.dp)
            )

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Impact") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Ledger") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("By Type") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Today") }
                )
            }

            when (selectedTab) {
                0 -> ImpactBreakdownTab(
                    allTasks = allTasks,
                    onKindClick = { selectedKindForDetail = it }
                )
                1 -> TaskLedgerTab(completedTasks = allTasks)
                2 -> ByTypeTab(stats = taskTypeStats)
                3 -> TodayScoringTab(
                    breakdowns = scoreBreakdown,
                    dampen = { raw -> viewModel.previewDampen(raw) }
                )
            }
        }
    }

    selectedKindForDetail?.let { kind ->
        val kindTasks = allTasks.filter { it.kind == kind }
        TaskDetailListDialog(
            kind = kind,
            tasks = kindTasks,
            onDismiss = { selectedKindForDetail = null }
        )
    }
}

@Composable
private fun SummaryCard(
    totalScore: Int,
    personal: Int,
    social: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Lifetime Productivity Score",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (totalScore >= 0) "+$totalScore" else totalScore.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (totalScore >= 0) Success else MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreColumn("Personal", personal)
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant))
                ScoreColumn("Social", social)
            }
        }
    }
}

@Composable
private fun ScoreColumn(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (score >= 0) "+$score" else score.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (score >= 0) Success else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ImpactBreakdownTab(
    allTasks: List<Task>,
    onKindClick: (TaskKind) -> Unit
) {
    val breakdown = remember(allTasks) {
        allTasks.groupBy { it.kind }
            .mapValues { (_, tasks) -> tasks.sumOf { it.score } }
            .toList()
            .sortedByDescending { it.second }
    }

    if (breakdown.isEmpty()) {
        EmptyStatsView("No tasks recorded yet.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(breakdown) { (kind, score) ->
                StatItemRow(kind = kind, score = score, onClick = { onKindClick(kind) })
            }
        }
    }
}

/**
 * Averaged view of each Task Type. Sorted best-average first, with types that have no tasks yet
 * pushed to the bottom -- they carry a null average and would otherwise sort as if neutral.
 */
@Composable
private fun ByTypeTab(stats: Map<String, TaskTypeStats>) {
    val rows = remember(stats) {
        stats.values.sortedWith(
            compareByDescending<TaskTypeStats> { it.taskCount > 0 }
                .thenByDescending { it.averagePoints ?: 0.0 }
                .thenBy { it.name.lowercase() }
        )
    }

    if (rows.isEmpty()) {
        EmptyStatsView("No task types yet.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.typeId }) { row -> TaskTypeStatRow(stats = row) }
        }
    }
}

@Composable
private fun TaskTypeStatRow(stats: TaskTypeStats) {
    val accent = stats.mostUsedKind?.let { Color(it.colorValue) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val avg = stats.averagePoints

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    avg == null -> Icons.Default.BarChart
                    avg > 0 -> Icons.Default.TrendingUp
                    avg < 0 -> Icons.Default.TrendingDown
                    else -> Icons.Default.BarChart
                }
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stats.name, fontWeight = FontWeight.Bold)
                Text(
                    text = buildString {
                        append(if (stats.taskCount == 1) "1 task" else "${stats.taskCount} tasks")
                        stats.mostUsedKind?.let { append(" - mostly ${it.displayName}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stats.averageLabel,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        avg == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        avg > 0 -> MaterialTheme.colorScheme.primary
                        avg < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    "avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Today's score with the arithmetic left showing, because the number on the Tasks screen is the
 * output of five terms and one non-obvious curve, and nothing anywhere explained the gap between
 * "I earned 1200 points of Peacock today" and "my Personal score says +5".
 *
 * Deliberately scoped to today: dampening is an aggregation-time rule applied to a single day's
 * tracked total, so there is no dampened lifetime figure to show, and the card above this one is
 * the plain historical sum.
 */
@Composable
private fun TodayScoringTab(
    breakdowns: List<CategoryScoreBreakdown>,
    dampen: (Int) -> Int
) {
    if (breakdowns.isEmpty()) {
        EmptyStatsView("Nothing scored today yet.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DampeningCurveCard(breakdowns = breakdowns, dampen = dampen) }
        items(breakdowns, key = { it.category.name }) { breakdown ->
            CategoryBreakdownCard(breakdown)
        }
        item {
            Text(
                text = "Completed Todos bypass dampening and add their full value. Overdue and " +
                    "procrastination penalties are subtracted after it. Lifetime totals above are " +
                    "the plain historical sum -- only a single day's tracked total is dampened.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The diminishing curve itself, with each category's raw total marked on it -- the shape is the
 * explanation, so it's drawn by sampling the real [dampen] rather than an approximation of it. */
@Composable
private fun DampeningCurveCard(
    breakdowns: List<CategoryScoreBreakdown>,
    dampen: (Int) -> Int
) {
    val maxRaw = breakdowns.maxOfOrNull { abs(it.rawTracked) } ?: 0
    // Always show enough curve for the flattening to be visible, even on a quiet day.
    val axisMax = maxOf(60, maxRaw + 10)
    val ceiling = 5f
    val curveColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dampening Curve", fontWeight = FontWeight.Bold)
            Text(
                text = "Raw tracked points (horizontal) against what they contribute (vertical). " +
                    "Approaches ${ceiling.toInt()} without reaching it, so more effort always " +
                    "scores more -- just less and less.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val w = size.width
                val h = size.height
                drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
                // The ceiling the curve is asymptotic to.
                drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 2f)

                val steps = 60
                var previous: Offset? = null
                for (i in 0..steps) {
                    val raw = axisMax * i / steps
                    val x = w * i / steps
                    val y = h - (dampen(raw) / ceiling).coerceIn(0f, 1f) * h
                    val point = Offset(x, y)
                    previous?.let { drawLine(curveColor, it, point, strokeWidth = 4f) }
                    previous = point
                }

                breakdowns.forEach { breakdown ->
                    val raw = abs(breakdown.rawTracked)
                    if (raw > 0) {
                        val x = w * raw / axisMax
                        val y = h - (abs(breakdown.dampenedTracked) / ceiling).coerceIn(0f, 1f) * h
                        drawLine(markerColor.copy(alpha = 0.5f), Offset(x, h), Offset(x, y), strokeWidth = 2f)
                        drawCircle(markerColor, radius = 6f, center = Offset(x, y))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "0 to $axisMax raw pts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: CategoryScoreBreakdown) {
    val label = breakdown.category.name.lowercase().replaceFirstChar { it.uppercase() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (breakdown.total >= 0) "+${breakdown.total}" else breakdown.total.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (breakdown.total >= 0) Success else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            ScoreTermRow(
                "Tracked, raw",
                breakdown.rawTracked,
                detail = "${breakdown.trackedTaskCount} task${if (breakdown.trackedTaskCount == 1) "" else "s"}"
            )
            ScoreTermRow("After dampening", breakdown.dampenedTracked, emphasis = true)
            if (breakdown.dampeningAbsorbed != 0) {
                Text(
                    text = "Dampening absorbed ${abs(breakdown.dampeningAbsorbed)} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (breakdown.todoPoints != 0) ScoreTermRow("Completed todos", breakdown.todoPoints)
            if (breakdown.overduePenalty != 0) ScoreTermRow("Overdue todos", -breakdown.overduePenalty)
            if (breakdown.todoProcrastinationPenalty != 0) {
                ScoreTermRow("Todo procrastination", -breakdown.todoProcrastinationPenalty)
            }
            if (breakdown.taskProcrastinationPenalty != 0) {
                ScoreTermRow("Task procrastination", -breakdown.taskProcrastinationPenalty)
            }
        }
    }
}

@Composable
private fun ScoreTermRow(label: String, value: Int, detail: String? = null, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (detail != null) "$label ($detail)" else label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (value >= 0) "+$value" else value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            color = when {
                value > 0 -> Success
                value < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StatItemRow(
    kind: TaskKind,
    score: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(kind.colorValue).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    score > 0 -> Icons.Default.TrendingUp
                    score < 0 -> Icons.Default.TrendingDown
                    else -> Icons.Default.BarChart
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(kind.colorValue))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kind.displayName.split(" • ").last(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = kind.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (score >= 0) "+$score" else score.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (score >= 0) Success else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun TaskLedgerTab(completedTasks: List<Task>) {
    var entriesLimit by remember { mutableIntStateOf(50) }
    
    val productivityTasksWithBalance = remember(completedTasks) {
        var currentBalance = 0
        completedTasks.sortedBy { it.startTime }
            .map { task ->
                currentBalance += task.score
                task to currentBalance
            }
            .reversed()
    }

    val visibleEntries = productivityTasksWithBalance.take(entriesLimit)
    val hasMore = productivityTasksWithBalance.size > entriesLimit

    if (productivityTasksWithBalance.isEmpty()) {
        EmptyStatsView("The ledger is empty.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { LedgerHeader() }
            
            items(visibleEntries) { (task, balance) ->
                TransactionRow(task = task, runningBalance = balance)
            }
            
            if (hasMore) {
                item {
                    TextButton(
                        onClick = { entriesLimit += 50 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show More")
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Activity", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall)
        // "Kind", not "Type" -- Task Type is now a separate concept (see TaskType.kt) and the two
        // must not read as the same column.
        Text("Kind", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
        Text("Impact", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall)
        Text("Balance", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TransactionRow(task: Task, runningBalance: Int) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(task.startTime))
    val perTaskValue = task.score
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                val label = task.kind.displayName.split(" • ").last()
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(task.kind.colorValue).copy(alpha = 0.1f)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color(task.kind.colorValue),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Text(
                text = if (perTaskValue >= 0) "+$perTaskValue" else perTaskValue.toString(),
                modifier = Modifier.weight(0.6f),
                color = when {
                    perTaskValue > 0 -> Success
                    perTaskValue < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            
            Text(
                text = if (runningBalance >= 0) "+$runningBalance" else runningBalance.toString(),
                modifier = Modifier.weight(0.7f),
                color = if (runningBalance >= 0) Success else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
private fun TaskDetailListDialog(
    kind: TaskKind,
    tasks: List<Task>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskKindChip(kind = kind)
                Spacer(Modifier.width(8.dp))
                Text("History")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks.sortedByDescending { it.startTime }) { task ->
                    TaskEntryRow(task = task)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TaskEntryRow(task: Task) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(task.startTime))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(task.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (task.score >= 0) "+${task.score}" else task.score.toString(),
                    color = if (task.score >= 0) Success else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDetailedDuration(task.duration), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyStatsView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
