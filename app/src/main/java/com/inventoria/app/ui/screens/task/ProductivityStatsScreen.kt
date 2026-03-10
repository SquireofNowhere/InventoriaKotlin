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
import com.inventoria.app.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val allTasks = remember(completedSessions) { completedSessions.flatten() }
    
    val personalScore by viewModel.personalScore.collectAsState()
    val socialScore by viewModel.socialScore.collectAsState()
    val totalScore by viewModel.totalScore.collectAsState()
    
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
            }

            when (selectedTab) {
                0 -> ImpactBreakdownTab(
                    allTasks = allTasks,
                    onKindClick = { selectedKindForDetail = it }
                )
                1 -> TaskLedgerTab(completedTasks = allTasks)
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
        Text("Type", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
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
