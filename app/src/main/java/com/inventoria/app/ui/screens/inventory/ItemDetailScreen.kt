package com.inventoria.app.ui.screens.inventory

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inventoria.app.data.model.InventoryItem
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
    onAddItemInside: (Long) -> Unit,
    onNavigateToCollection: (Long) -> Unit,
    viewModel: ItemDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.item?.name ?: "Item Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.item?.let { item ->
                        IconButton(onClick = { onEditItem(item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val item = uiState.item
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) CircularProgressIndicator()
                else Text(uiState.error ?: "Item not found")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image Gallery Section
            if (item.imageUrls.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            AsyncImage(
                                model = item.getPrimaryImage(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        if (item.imageUrls.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().height(80.dp)
                            ) {
                                items(item.imageUrls) { url ->
                                    val isProfile = url == item.profilePictureUrl
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setProfilePicture(url) }
                                            .background(if (isProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(if (isProfile) 2.dp else 0.dp)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isProfile) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp).align(Alignment.TopEnd).padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Header Section
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (item.equipped) {
                            Spacer(Modifier.width(8.dp))
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Equipped") },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            )
                        }
                    }
                    
                    item.category?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Stats Section
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox(modifier = Modifier.weight(1f), label = "Quantity", value = item.quantity.toString())
                    item.price?.let {
                        StatBox(modifier = Modifier.weight(1f), label = "Price", value = NumberFormat.getCurrencyInstance().format(it))
                    }
                }
            }

            // Description
            item.description?.takeIf { it.isNotEmpty() }?.let {
                item {
                    Column {
                        Text("Description", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Location
            item {
                Column {
                    Text("Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (item.latitude != null && item.longitude != null) {
                                onLocationClick(item.latitude!!, item.longitude!!)
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.location, style = MaterialTheme.typography.bodyMedium)
                                if (item.latitude != null && item.longitude != null) {
                                    Text("View on Map", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Actions
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            if (item.equipped) {
                                itemToUnequip = item
                            } else {
                                viewModel.toggleEquip()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (item.equipped) Icons.Default.Backpack else Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (item.equipped) "Unequip" else "Equip")
                    }
                    OutlinedButton(
                        onClick = { showMoveDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Move")
                    }
                }
            }

            // Containers / Children
            if (item.storage) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Items Inside", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { onAddItemInside(item.id) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Item Inside")
                        }
                    }
                }
                if (uiState.children.isEmpty()) {
                    item {
                        Text("Empty container", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(uiState.children) { child ->
                        ListItem(
                            headlineContent = { Text(child.name) },
                            supportingContent = { Text("Qty: ${child.quantity}") },
                            leadingContent = { Icon(if (child.storage) Icons.Default.Inventory else Icons.Default.Category, contentDescription = null) },
                            modifier = Modifier.clickable { onNavigateToItemDetail(child.id) }
                        )
                    }
                }
            }

            // Linked Items
            if (uiState.links.isNotEmpty()) {
                item {
                    Text("Linked Items", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.links) { link ->
                    val isLeader = link.leaderId == item.id
                    val otherItemId = if (isLeader) link.followerId else link.leaderId
                    val otherItemName = uiState.linkNames[otherItemId] ?: "Unknown Item"
                    
                    ListItem(
                        headlineContent = { Text(otherItemName) },
                        supportingContent = { Text(if (isLeader) "Follows this item" else "Leads this item") },
                        leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeLink(link.followerId, link.leaderId) }) {
                                Icon(Icons.Default.LinkOff, contentDescription = "Remove Link", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.clickable { onNavigateToItemDetail(otherItemId) }
                    )
                }
            }
            
            // Collections
            if (uiState.collections.isNotEmpty()) {
                item {
                    Text("Collections", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.collections) { collection ->
                    ListItem(
                        headlineContent = { Text(collection.name) },
                        leadingContent = { Text(collection.icon ?: "📦", fontSize = 24.sp) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onNavigateToCollection(collection.id) }
                    )
                }
            }

            // Custom Fields
            if (item.customFields.isNotEmpty()) {
                item {
                    Text("Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                item.customFields.forEach { (k, v) ->
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v)
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete '${uiState.item?.name}'?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteItem(onNavigateBack) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMoveDialog) {
        MoveItemDialog(
            containers = uiState.availableContainers,
            onDismiss = { showMoveDialog = false },
            onMove = { viewModel.moveToContainer(it); showMoveDialog = false }
        )
    }

    itemToUnequip?.let { item ->
        com.inventoria.app.ui.components.UnequipRepackDialog(
            itemName = item.name,
            containerName = uiState.lastParentName,
            onDismiss = { itemToUnequip = null },
            onUnequipOnly = {
                viewModel.toggleEquip(false)
                itemToUnequip = null
            },
            onRepack = {
                viewModel.toggleEquip(true)
                itemToUnequip = null
            }
        )
    }
}

@Composable
fun StatBox(modifier: Modifier, label: String, value: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MoveItemDialog(
    containers: List<InventoryItem>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Container") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("None (Base Level)") },
                        leadingContent = { Icon(Icons.Default.LocationOff, contentDescription = null) },
                        modifier = Modifier.clickable { onMove(null) }
                    )
                }
                items(containers) { container ->
                    ListItem(
                        headlineContent = { Text(container.name) },
                        supportingContent = { Text(container.location) },
                        leadingContent = { Icon(Icons.Default.Inventory, contentDescription = null) },
                        modifier = Modifier.clickable { onMove(container.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
