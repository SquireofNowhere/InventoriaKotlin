package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.Success
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductivityStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskTrackerViewModel = hiltViewModel()
) {
    val completedSessions by viewModel.completedSessions.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    
    val completedTasksOnly = completedSessions.flatten()
    val totalPersonal = completedTasksOnly.filter { it.kind.category == TaskCategory.PERSONAL }.sumOf { it.score }
    val totalSocial = completedTasksOnly.filter { it.kind.category == TaskCategory.SOCIAL }.sumOf { it.score }
    val totalScore = totalPersonal + totalSocial

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var selectedKindForDetail by remember { mutableStateOf<TaskKind?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    if (showBottomSheet && selectedKindForDetail != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                showBottomSheet = false
                selectedKindForDetail = null
            },
            sheetState = sheetState
        ) {
            TaskDetailList(
                kind = selectedKindForDetail!!,
                tasks = completedTasksOnly.filter { it.kind == selectedKindForDetail }
            )
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Productivity Stats", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Impact Breakdown") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Task Ledger") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SummaryCard(
                totalScore = totalScore,
                personal = totalPersonal,
                social = totalSocial,
                modifier = Modifier.padding(16.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> ImpactBreakdownTab(completedTasksOnly) { kind ->
                        selectedKindForDetail = kind
                        showBottomSheet = true
                    }
                    1 -> TaskLedgerTab(completedTasksOnly)
                }
            }
        }
    }
}

@Composable
private fun ImpactBreakdownTab(allTasks: List<Task>, onKindClick: (TaskKind) -> Unit) {
    val breakdown = allTasks
        .groupBy { it.kind }
        .mapValues { entry -> entry.value.sumOf { it.score } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Lifetime Impact by Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (breakdown.isEmpty()) {
            item {
                EmptyStatsView("No impact data yet.")
            }
        }

        items(breakdown) { (kind, score) ->
            StatItemRow(kind, score, onClick = { onKindClick(kind) })
        }
    }
}

@Composable
private fun TaskLedgerTab(completedTasks: List<Task>) {
    var entriesLimit by remember { mutableIntStateOf(50) }
    
    val productivityTasksWithBalance = remember(completedTasks) {
        val sorted = completedTasks
            .sortedBy { it.startTime }
        
        var currentBalance = 0
        sorted.map { task ->
            currentBalance += task.score
            task to currentBalance
        }.reversed()
    }

    val visibleEntries = productivityTasksWithBalance.take(entriesLimit)
    val hasMore = productivityTasksWithBalance.size > entriesLimit

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Individual Task Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            LedgerHeader()
        }

        if (productivityTasksWithBalance.isEmpty()) {
            item {
                EmptyStatsView("No transactions found.")
            }
        }

        items(visibleEntries) { (task, balance) ->
            TransactionRow(task, balance)
        }
        
        if (hasMore) {
            item {
                ShowMoreButton(onClick = { entriesLimit += 50 })
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShowMoreButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .height(100.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                Text("Show More History")
            }
        }
    }
}

@Composable
private fun LedgerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Task / Date", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Category", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Impact", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Text("Balance", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun TransactionRow(task: Task, runningBalance: Int) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(task.startTime))
    val perTaskValue = task.kind.productivityValue
    val percentage = calculatePercentageOfDay(task.duration, task.startTime)

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
                    text = "$dateStr • $percentage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(
                modifier = Modifier.weight(0.8f),
                contentAlignment = Alignment.Center
            ) {
                val label = if (task.kind.displayName.contains(" • ")) {
                    task.kind.displayName.split(" • ").last()
                } else {
                    task.kind.displayName.substringAfter(" ").trim()
                }
                Surface(
                    color = Color(task.kind.colorValue).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color(task.kind.colorValue),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Text(
                text = if (perTaskValue >= 0) "+$perTaskValue" else "$perTaskValue",
                modifier = Modifier.weight(0.6f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = if (perTaskValue > 0) Success else if (perTaskValue < 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (runningBalance >= 0) "+$runningBalance" else "$runningBalance",
                modifier = Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                color = if (runningBalance >= 0) Success else Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun TaskDetailList(kind: TaskKind, tasks: List<Task>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(kind.colorValue))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${kind.displayName.split(" • ").last()} History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks.sortedByDescending { it.startTime }) { task ->
                TaskEntryRow(task)
            }
        }
    }
}

@Composable
private fun TaskEntryRow(task: Task) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(task.startTime))
    val percentage = calculatePercentageOfDay(task.duration, task.startTime)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(task.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("$dateStr • $percentage", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            
            Text(
                text = if (task.score >= 0) "+${task.score}" else "${task.score}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (task.score > 0) Success else if (task.score < 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryCard(totalScore: Int, personal: Int, social: Int, modifier: Modifier = Modifier) {
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
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Lifetime Productivity Score",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (totalScore >= 0) "+$totalScore" else "$totalScore",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (totalScore >= 0) Success else Color(0xFFF44336)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
            text = if (score >= 0) "+$score" else "$score",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (score >= 0) Success else Color(0xFFF44336)
        )
    }
}

@Composable
private fun StatItemRow(kind: TaskKind, score: Int, onClick: () -> Unit) {
    val isPositive = score > 0
    val isNegative = score < 0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(kind.colorValue).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.TrendingUp else if (isNegative) Icons.Default.TrendingDown else Icons.Default.BarChart,
                    contentDescription = null,
                    tint = Color(kind.colorValue),
                    modifier = Modifier.size(20.dp)
                )
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
                    text = if (score >= 0) "+$score" else "$score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (score > 0) Success else if (score < 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.History,
                    contentDescription = "Show details",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
