package com.inventoria.app.ui.screens.dashboard

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.Task
import com.inventoria.app.ui.components.UnequipRepackDialog
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToInventory: () -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }
    var containerName by remember { mutableStateOf<String?>(null) }

    val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTranslate.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    LaunchedEffect(itemToUnequip) {
        itemToUnequip?.let { item ->
            if (item.lastParentId != null) {
                containerName = viewModel.getContainerName(item.lastParentId!!)
            } else {
                containerName = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Inventoria", fontWeight = FontWeight.Bold) 
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GradientHeaderCard(uiState)
            }

            item {
                StatisticsSection(uiState, shimmerOffset)
            }

            item {
                QuickActionsSection(onNavigateToAddItem)
            }

            if (uiState.recentItems.isNotEmpty()) {
                item {
                    Text(
                        "Recent Items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.recentItems) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = { onNavigateToItemDetail(item.id) },
                        onToggleEquip = {
                            if (item.equipped) {
                                if (item.lastParentId == null) {
                                    viewModel.toggleEquip(item.id, false)
                                } else {
                                    itemToUnequip = item
                                }
                            } else {
                                viewModel.toggleEquip(item.id, false)
                            }
                        }
                    )
                }
            }
        }
    }

    itemToUnequip?.let { item ->
        UnequipRepackDialog(
            itemName = item.name,
            containerName = containerName,
            onDismiss = { itemToUnequip = null },
            onUnequipOnly = {
                viewModel.toggleEquip(item.id, false)
                itemToUnequip = null
            },
            onRepack = {
                viewModel.toggleEquip(item.id, true)
                itemToUnequip = null
            }
        )
    }
}

@Composable
fun GradientHeaderCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Welcome Back!",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Track your items and tasks efficiently.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                LinearProductivityChart(
                    tasks = uiState.tasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }
    }
}

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
            
            DashboardChartSegment(
                color = Color(task.kind.colorValue),
                startRatio = startRatio,
                endRatio = endRatio
            )
        }.sortedBy { it.startRatio }

        val clusters = mutableListOf<MutableList<DashboardChartSegment>>()
        var currentCluster = mutableListOf<DashboardChartSegment>()
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
            val tracks = mutableListOf<MutableList<DashboardChartSegment>>()
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

private data class DashboardChartSegment(
    val color: Color,
    val startRatio: Float,
    val endRatio: Float,
    var trackIndex: Int = 0,
    var maxTracks: Int = 1
)

@Composable
fun StatisticsSection(uiState: DashboardUiState, shimmerOffset: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Total Items",
            value = uiState.totalItems.toString(),
            icon = Icons.Default.Inventory,
            color = PurplePrimary,
            shimmerOffset = shimmerOffset
        )
        if (uiState.showTotalValue) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Value",
                value = NumberFormat.getCurrencyInstance().format(uiState.totalValue),
                icon = Icons.Default.AttachMoney,
                color = Success,
                shimmerOffset = shimmerOffset
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    shimmerOffset: Float
) {
    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun QuickActionsSection(onAddItem: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Add Item",
                icon = Icons.Default.Add,
                color = MaterialTheme.colorScheme.primary,
                onClick = onAddItem
            )
            // Add more quick actions as needed
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(36.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RecentItemCard(
    item: InventoryItem,
    onClick: () -> Unit,
    onToggleEquip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.getDisplayLocation(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.quantity != 1) {
                    Text(
                        "Qty: ${item.quantity}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onToggleEquip) {
                Icon(
                    painter = painterResource(
                        if (item.equipped) R.drawable.mobile_theft_24px else R.drawable.mobile_24px
                    ),
                    contentDescription = if (item.equipped) "Unequip" else "Equip",
                    tint = if (item.equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun UnequipRepackDialog(
    itemName: String,
    containerName: String?,
    onDismiss: () -> Unit,
    onUnequipOnly: () -> Unit,
    onRepack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unequip $itemName") },
        text = {
            val containerText = if (containerName != null) " back to $containerName" else " into its original container"
            Text("Would you like to repack this item$containerText or leave it at your current location?")
        },
        confirmButton = {
            TextButton(onClick = onRepack) {
                Text("Repack")
            }
        },
        dismissButton = {
            TextButton(onClick = onUnequipOnly) {
                Text("Leave here")
            }
        }
    )
}
