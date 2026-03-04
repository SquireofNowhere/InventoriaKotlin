package com.inventoria.app.ui.screens.collections

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionWithItems
import com.inventoria.app.data.model.InventoryCollectionReadiness

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAddItems: (Long) -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val collectionWithItems by viewModel.collectionWithItems.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val packResult by viewModel.packResult.collectAsState(initial = null)
    val availableContainers by viewModel.availableContainers.collectAsState()

    var showPackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnequipDialog by remember { mutableStateOf(false) }
    var containerNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    LaunchedEffect(showUnequipDialog, collectionWithItems) {
        if (showUnequipDialog && collectionWithItems != null) {
            val lastParentIds = collectionWithItems!!.items
                .filter { it.equipped && it.lastParentId != null }
                .map { it.lastParentId!! }
                .toSet()
            
            val names = mutableSetOf<String>()
            lastParentIds.forEach { id ->
                viewModel.getContainerName(id)?.let { names.add(it) }
            }
            containerNames = names
        }
    }

    LaunchedEffect(packResult) {
        packResult?.let {
            snackbarHostState.showSnackbar(it.message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(collectionWithItems?.collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddItems(collectionId) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Items") }
            )
        }
    ) { padding ->
        if (collectionWithItems == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val data = collectionWithItems!!
            val collection = data.collection

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Info
                item {
                    CollectionHeader(collection = collection, readiness = readiness)
                }

                // Action Buttons
                item {
                    ActionButtons(
                        onPack = { showPackDialog = true },
                        onUnpack = { viewModel.unpackCollection() },
                        onEquip = { viewModel.equipCollection() },
                        onUnequip = { showUnequipDialog = true },
                        isAnyPacked = (readiness?.packedItems ?: 0) > 0,
                        isAnyEquipped = (readiness?.equippedItems ?: 0) > 0
                    )
                }

                // Items List
                item {
                    Text(
                        text = "Items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (data.items.isEmpty()) {
                    item {
                        Text(
                            text = "No items in this collection yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(data.items, key = { it.id }) { item ->
                        CollectionItemRow(
                            item = item,
                            onClick = { onNavigateToItemDetail(item.id) },
                            onRemove = { viewModel.removeItem(item.id) }
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showPackDialog) {
        PackToContainerDialog(
            containers = availableContainers,
            onDismiss = { showPackDialog = false },
            onConfirm = { containerId ->
                viewModel.packIntoContainer(containerId)
                showPackDialog = false
            }
        )
    }

    if (showUnequipDialog) {
        CollectionUnequipRepackDialog(
            containerNames = containerNames,
            onDismiss = { showUnequipDialog = false },
            onUnequipOnly = {
                viewModel.unequipCollection(repack = false)
                showUnequipDialog = false
            },
            onRepack = {
                viewModel.unequipCollection(repack = true)
                showUnequipDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Collection?") },
            text = { Text("Are you sure you want to delete this collection? This will not delete the items themselves.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection()
                    onNavigateBack()
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
fun CollectionUnequipRepackDialog(
    containerNames: Set<String>,
    onDismiss: () -> Unit,
    onUnequipOnly: () -> Unit,
    onRepack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unequip Collection") },
        text = { 
            val containerText = when {
                containerNames.isEmpty() -> " into their original containers"
                containerNames.size == 1 -> " back to ${containerNames.first()}"
                containerNames.size <= 3 -> " back to ${containerNames.joinToString(", ")}"
                else -> " back to their respective containers"
            }
            Text("Would you like to repack items$containerText or leave them at your current location?") 
        },
        confirmButton = {
            TextButton(onClick = onRepack) {
                Text("Repack")
            }
        },
        dismissButton = {
            TextButton(onClick = onUnequipOnly) {
                Text("Leave Here")
            }
        }
    )
}

@Composable
fun CollectionHeader(collection: InventoryCollection, readiness: InventoryCollectionReadiness?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(collection.color).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(collection.color).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = collection.icon ?: "📦", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = collection.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (collection.tags.isNotEmpty()) {
                        Text(
                            text = collection.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (!collection.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = collection.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Readiness Progress
            val progress = readiness?.readinessPercentage ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${progress.toInt()}% Ready",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (readiness != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${readiness.availableItems}/${readiness.totalItems} Available", style = MaterialTheme.typography.bodySmall)
                    Text("${readiness.packedItems} Packed", style = MaterialTheme.typography.bodySmall)
                    Text("${readiness.equippedItems} Equipped", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ActionButtons(
    onPack: () -> Unit,
    onUnpack: () -> Unit,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
    isAnyPacked: Boolean,
    isAnyEquipped: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            label = if (isAnyPacked) "Unpack All" else "Pack All",
            icon = if (isAnyPacked) Icons.Default.Unarchive else Icons.Default.Archive,
            onClick = if (isAnyPacked) onUnpack else onPack,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            label = if (isAnyEquipped) "Unequip All" else "Equip All",
            icon = if (isAnyEquipped) Icons.Default.Backpack else Icons.Default.Person,
            onClick = if (isAnyEquipped) onUnequip else onEquip,
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}

@Composable
fun ActionButton(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColorFor(containerColor)),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CollectionItemRow(
    item: InventoryItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() }.clip(MaterialTheme.shapes.small),
        headlineContent = { Text(item.name) },
        supportingContent = {
            Text(
                text = when {
                    item.equipped -> "Equipped"
                    item.parentId != null -> "Packed"
                    else -> "At Location: ${item.location}"
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    item.equipped -> Icons.Default.Person
                    item.parentId != null -> Icons.Default.Archive
                    else -> Icons.Default.Inventory2
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun PackToContainerDialog(
    containers: List<InventoryItem>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Container") },
        text = {
            if (containers.isEmpty()) {
                Text("No containers found. Please mark an item as a storage container first.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(containers) { container ->
                        ListItem(
                            modifier = Modifier.clickable { onConfirm(container.id) },
                            headlineContent = { Text(container.name) },
                            supportingContent = { Text(container.location) },
                            leadingContent = { Icon(Icons.Default.Inventory, contentDescription = null) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
