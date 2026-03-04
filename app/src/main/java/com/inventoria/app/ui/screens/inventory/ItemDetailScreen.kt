package com.inventoria.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.screens.dashboard.UnequipRepackDialog
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showContainerPicker by remember { mutableStateOf(false) }
    var showUnequipDialog by remember { mutableStateOf(false) }

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
                        IconButton(onClick = {
                            if (item.equipped) {
                                if (item.lastParentId != null) {
                                    showUnequipDialog = true
                                } else {
                                    viewModel.toggleEquip(repack = false)
                                }
                            } else {
                                viewModel.toggleEquip()
                            }
                        }) {
                            Icon(
                                painter = if (item.equipped) painterResource(R.drawable.mobile_theft_24px) else painterResource(R.drawable.mobile_24px),
                                contentDescription = if (item.equipped) "Unequip" else "Equip"
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
                            if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
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

                // Tags section
                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text("#$tag") },
                                shape = CircleShape
                            )
                        }
                    }
                }

                // Collections section
                if (uiState.collections.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Part of Collections",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(uiState.collections) { collection ->
                                AssistChip(
                                    onClick = { /* Navigate to collection detail if needed */ },
                                    label = { Text(collection.name) },
                                    leadingIcon = { Text(collection.icon ?: "📦") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(collection.color).copy(alpha = 0.1f),
                                        labelColor = Color(collection.color)
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        borderColor = Color(collection.color).copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
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

                // Inconspicuous "Add to container" button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showContainerPicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (parentItem != null) Icons.Default.DriveFileMove else Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (parentItem != null) "Move to other" else "Add to container",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Location Row
                if (item.equipped) {
                    DetailItemRow(
                        label = "Location", 
                        value = "Equipped (With You)", 
                        iconPainter = painterResource(R.drawable.mobile_theft_24px)
                    )
                } else if (parentItem != null) {
                    DetailItemRow(
                        label = "Inside Container", 
                        value = parentItem.name, 
                        icon = Icons.Default.Inventory,
                        onClick = { onNavigateToItemDetail(parentItem.id) }
                    )
                } else {
                    val lat = item.latitude
                    val lon = item.longitude
                    if (lat != null && lon != null) {
                        DetailItemRow(
                            label = "Location", 
                            value = item.location, 
                            icon = Icons.Default.LocationOn,
                            onClick = { onLocationClick(lat, lon) }
                        )
                    } else {
                        DetailItemRow(label = "Location", value = item.location, icon = Icons.Default.LocationOn)
                    }
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

    // Container Picker Dialog
    if (showContainerPicker) {
        AlertDialog(
            onDismissRequest = { showContainerPicker = false },
            title = { Text("Select Container") },
            text = {
                val containers = uiState.availableContainers
                if (containers.isEmpty()) {
                    Text("No items available to use as a container.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        // Option to remove from current container
                        item {
                            ListItem(
                                headlineContent = { Text("None (Standalone)", fontWeight = FontWeight.Medium) },
                                leadingContent = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.clickable {
                                    viewModel.moveToContainer(null)
                                    showContainerPicker = false
                                }
                            )
                            Divider()
                        }

                        // Split into Existing Containers vs Regular Items
                        val (existing, regular) = containers.partition { it.storage }

                        if (existing.isNotEmpty()) {
                            item {
                                Text(
                                    "Existing Containers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp)
                                )
                            }
                            items(existing) { container ->
                                ListItem(
                                    headlineContent = { Text(container.name, fontWeight = FontWeight.Bold) },
                                    leadingContent = { Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    supportingContent = { Text(container.location, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.clickable {
                                        viewModel.moveToContainer(container.id)
                                        showContainerPicker = false
                                    }
                                )
                            }
                        }

                        if (regular.isNotEmpty()) {
                            item {
                                Text(
                                    "Other Items (Turn into container)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
                                )
                            }
                            items(regular) { other ->
                                ListItem(
                                    headlineContent = { Text(other.name) },
                                    leadingContent = { Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                                    supportingContent = { Text(other.location, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.clickable {
                                        viewModel.moveToContainer(other.id)
                                        showContainerPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContainerPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnequipDialog) {
        val itemName = uiState.item?.name ?: "Item"
        UnequipRepackDialog(
            itemName = itemName,
            containerName = uiState.lastParentName,
            onDismiss = { showUnequipDialog = false },
            onUnequipOnly = {
                viewModel.toggleEquip(repack = false)
                showUnequipDialog = false
            },
            onRepack = {
                viewModel.toggleEquip(repack = true)
                showUnequipDialog = false
            }
        )
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
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
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
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        } else if (iconPainter != null) {
            Icon(iconPainter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
