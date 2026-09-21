package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.BorderStroke
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
    val scoreBreakdownToday by viewModel.scoreBreakdownToday.collectAsState()
    val scoreBreakdownLifetime by viewModel.scoreBreakdownLifetime.collectAsState()

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
                    text = { Text("Scoring") }
                )
            }

            when (selectedTab) {
                0 -> ImpactBreakdownTab(
                    allTasks = allTasks,
                    onKindClick = { selectedKindForDetail = it }
                )
                1 -> TaskLedgerTab(completedTasks = allTasks)
                2 -> ByTypeTab(stats = taskTypeStats)
                3 -> ScoringTab(
                    today = scoreBreakdownToday,
                    lifetime = scoreBreakdownLifetime
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
    totalScore: Double,
    personal: Double,
    social: Double,
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
                text = formatPoints(totalScore),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPositivePoints(totalScore)) Success else MaterialTheme.colorScheme.error
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
private fun ScoreColumn(label: String, score: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = formatPoints(score),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPositivePoints(score)) Success else MaterialTheme.colorScheme.error
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
            .mapValues { (_, tasks) -> tasks.sumOf { it.points } }
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
 * The arithmetic behind the score, left showing: one card per category for today, then the same
 * terms summed over all time. Both use the same terms and the same scale -- tracked hours count as
 * points at the Kind's value per hour, so an hour of a +3 Kind is worth one +3 Todo -- so the
 * lifetime figure is just today's rules applied to every day. The one term lifetime can't include
 * is the overdue penalty, a live daily charge that can't be replayed for days already over.
 */
@Composable
private fun ScoringTab(
    today: List<CategoryScoreBreakdown>,
    lifetime: List<CategoryScoreBreakdown>
) {
    if (today.isEmpty() && lifetime.isEmpty()) {
        EmptyStatsView("Nothing scored yet.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(today, key = { "today-" + it.category.name }) { breakdown ->
            CategoryBreakdownCard(breakdown)
        }
        item {
            Text(
                text = "Tracked time scores the Kind's value per hour, so an hour of a +3 Kind is worth " +
                    "the same as one completed +3 Todo. A task that crosses midnight is split between " +
                    "the two days by how much of it fell on each.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Text("Lifetime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(lifetime, key = { "lifetime-" + it.category.name }) { breakdown ->
            CategoryBreakdownCard(breakdown)
        }
        item {
            Text(
                text = "Lifetime uses the same terms over every day. Overdue penalties aren't part of it: " +
                    "they're a live charge for todos that are late right now. Procrastination penalties " +
                    "follow your current settings, so changing them re-scores your history.",
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
                    text = formatPoints(breakdown.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPositivePoints(breakdown.total)) Success else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            ScoreTermRow(
                "Tracked time",
                breakdown.trackedPoints,
                detail = "${breakdown.trackedTaskCount} task${if (breakdown.trackedTaskCount == 1) "" else "s"}"
            )
            if (breakdown.todoPoints != 0) ScoreTermRow("Completed todos", breakdown.todoPoints.toDouble())
            if (breakdown.overduePenalty != 0) ScoreTermRow("Overdue todos", -breakdown.overduePenalty.toDouble())
            if (breakdown.todoProcrastinationPenalty != 0) {
                ScoreTermRow("Todo procrastination", -breakdown.todoProcrastinationPenalty.toDouble())
            }
            if (breakdown.taskProcrastinationPenalty != 0.0) {
                ScoreTermRow("Task procrastination", -breakdown.taskProcrastinationPenalty)
            }
        }
    }
}

@Composable
private fun ScoreTermRow(label: String, value: Double, detail: String? = null, emphasis: Boolean = false) {
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
            text = formatPoints(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            color = when {
                value > 0.05 -> Success
                value < -0.05 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StatItemRow(
    kind: TaskKind,
    score: Double,
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
                    score > 0.05 -> Icons.Default.TrendingUp
                    score < -0.05 -> Icons.Default.TrendingDown
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
                    text = formatPoints(score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositivePoints(score)) Success else MaterialTheme.colorScheme.error
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
        var currentBalance = 0.0
        completedTasks.sortedBy { it.startTime }
            .map { task ->
                currentBalance += task.points
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
private fun TransactionRow(task: Task, runningBalance: Double) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(task.startTime))
    val perTaskValue = task.points
    
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
                text = formatPoints(perTaskValue),
                modifier = Modifier.weight(0.6f),
                color = when {
                    perTaskValue > 0.05 -> Success
                    perTaskValue < -0.05 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            
            Text(
                text = formatPoints(runningBalance),
                modifier = Modifier.weight(0.7f),
                color = if (isPositivePoints(runningBalance)) Success else MaterialTheme.colorScheme.error,
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
                    text = formatPoints(task.points),
                    color = if (isPositivePoints(task.points)) Success else MaterialTheme.colorScheme.error,
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
