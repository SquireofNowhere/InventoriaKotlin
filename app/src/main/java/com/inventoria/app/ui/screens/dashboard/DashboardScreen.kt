
package com.inventoria.app.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.theme.*
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToInventory: () -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToItemDetail: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = shimmerTranslate.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Dashboard",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { GradientHeaderCard() }
            
            item {
                StatisticsSection(
                    uiState = uiState,
                    shimmerOffset = translateAnim.value
                )
            }
            
            item {
                QuickActionsSection(
                    onViewInventory = onNavigateToInventory,
                    onAddItem = onNavigateToAddItem
                )
            }
            
            if (uiState.recentItems.isNotEmpty()) {
                item {
                    Text(
                        "Recent Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(uiState.recentItems) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = { onNavigateToItemDetail(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun GradientHeaderCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(PurplePrimary, PurpleAccent, Success),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 500f)
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Welcome to Inventoria",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manage your inventory with ease",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun StatisticsSection(uiState: DashboardUiState, shimmerOffset: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Items",
                value = uiState.totalItems.toString(),
                icon = Icons.Default.Inventory,
                color = PurplePrimary,
                shimmerOffset = shimmerOffset
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Value",
                value = NumberFormat.getCurrencyInstance().format(uiState.totalValue),
                icon = Icons.Default.AttachMoney,
                color = Success,
                shimmerOffset = shimmerOffset
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Low Stock",
                value = uiState.lowStockCount.toString(),
                icon = Icons.Default.Warning,
                color = Warning,
                shimmerOffset = shimmerOffset
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Out of Stock",
                value = uiState.outOfStockCount.toString(),
                icon = Icons.Default.Error,
                color = Error,
                shimmerOffset = shimmerOffset
            )
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color, shimmerOffset: Float) {
    Card(
        modifier = modifier.shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)).padding(6.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun QuickActionsSection(onViewInventory: () -> Unit, onAddItem: () -> Unit) {
    Column {
        Text("Quick Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                QuickActionCard(title = "View Inventory", icon = Icons.Default.List, color = PurplePrimary, onClick = onViewInventory)
            }
            item {
                QuickActionCard(title = "Add Item", icon = Icons.Default.Add, color = PurpleSecondary, onClick = onAddItem)
            }
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick).shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
fun RecentItemCard(item: InventoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text = item.location, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "Qty: ${item.quantity}", fontWeight = FontWeight.Medium)
        }
    }
}
