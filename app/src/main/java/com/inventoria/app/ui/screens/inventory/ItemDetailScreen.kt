package com.inventoria.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onEditItem: (Long) -> Unit,
    onLocationClick: (Double, Double) -> Unit,
    onNavigateToItemDetail: (Long) -> Unit, 
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val item = uiState.item
                    if (item != null) {
                        IconButton(onClick = viewModel::toggleEquip) {
                            Icon(
                                imageVector = if (item.isEquipped) Icons.Default.AccessibilityNew else Icons.Default.Accessibility,
                                contentDescription = if (item.isEquipped) "Unequip" else "Equip"
                            )
                        }
                        IconButton(onClick = { onEditItem(item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "Unknown error")
            }
        } else {
            val item = uiState.item!!
            val parentItem = uiState.parentItem
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header section
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(PurplePrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (item.isStorage) Icons.Default.Inventory else Icons.Default.Category,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    item.category?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Info Grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailInfoCard(
                        modifier = Modifier.weight(1f),
                        label = "Quantity",
                        value = item.quantity.toString(),
                        icon = Icons.Default.ProductionQuantityLimits,
                        color = if (item.quantity <= 0) Warning else Success
                    )
                    DetailInfoCard(
                        modifier = Modifier.weight(1f),
                        label = "Price",
                        value = item.price?.let { NumberFormat.getCurrencyInstance().format(it) } ?: "N/A",
                        icon = Icons.Default.AttachMoney,
                        color = Success
                    )
                }

                // Location Row
                if (item.isEquipped) {
                    DetailItemRow(label = "Location", value = "Equipped (With You)", icon = Icons.Default.AccessibilityNew)
                } else if (parentItem != null) {
                    DetailItemRow(
                        label = "Inside Container", 
                        value = parentItem.name, 
                        icon = Icons.Default.Inventory,
                        onClick = { onNavigateToItemDetail(parentItem.id) }
                    )
                } else if (item.latitude != null && item.longitude != null) {
                    DetailItemRow(
                        label = "Location", 
                        value = item.location, 
                        icon = Icons.Default.LocationOn,
                        onClick = { onLocationClick(item.latitude, item.longitude) }
                    )
                } else {
                    DetailItemRow(label = "Location", value = item.location, icon = Icons.Default.LocationOn)
                }
                
                item.description?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Custom Fields Section
                if (item.customFields.isNotEmpty()) {
                    Divider()
                    Text(
                        text = "Custom Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    item.customFields.forEach { (key, value) ->
                        DetailItemRow(label = key, value = value, icon = Icons.Default.Label)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onEditItem(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Item")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete '${uiState.item?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.deleteItem { onNavigateBack() }
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailInfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DetailItemRow(
    label: String, 
    value: String, 
    icon: ImageVector, 
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    
    Row(
        modifier = rowModifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        if (onClick != null) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = "Action", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
