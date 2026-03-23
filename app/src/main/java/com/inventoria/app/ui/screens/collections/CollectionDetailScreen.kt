package com.inventoria.app.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionReadiness
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.screens.inventory.InventoryItemRow
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onNavigateBack: () -> Unit,
    onEditCollection: (Long) -> Unit,
    onNavigateToAddItems: (Long) -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    viewModel: CollectionDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val collectionWithItems by viewModel.collectionWithItems.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val availableContainers by viewModel.availableContainers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }
    var showUnequipCollectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    LaunchedEffect(Unit) {
        viewModel.packResult.collectLatest { result ->
            snackbarHostState.showSnackbar(result.message)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(collectionWithItems?.collection?.name ?: "Collection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditCollection(collectionId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val collection = collectionWithItems?.collection
        if (collection == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
            item {
                CollectionHeader(collection, readiness)
            }

            item {
                ActionButtons(
                    onPack = { showPackDialog = true },
                    onUnpack = { viewModel.unpackCollection() },
                    onEquip = { viewModel.equipCollection() },
                    onUnequip = { showUnequipCollectionDialog = true },
                    isAnyPacked = (readiness?.packedItems ?: 0) > 0,
                    isAnyEquipped = (readiness?.equippedItems ?: 0) > 0
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onNavigateToAddItems(collectionId) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Items")
                    }
                }
            }

            if (uiState.filteredItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No items in this collection", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    InventoryItemRow(
                        item = item,
                        onClick = { onNavigateToItemDetail(item.id) },
                        onToggleEquip = {
                            if (item.equipped) {
                                itemToUnequip = item
                            } else {
                                viewModel.toggleEquip(item.id)
                            }
                        }
                    )
                }
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Collection") },
            text = { Text("Are you sure you want to delete this collection? The items themselves will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(onNavigateBack)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    itemToUnequip?.let { item ->
        var containerName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(item.lastParentId) {
            item.lastParentId?.let {
                containerName = viewModel.getContainerName(it)
            }
        }

        com.inventoria.app.ui.components.UnequipRepackDialog(
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

    if (showUnequipCollectionDialog) {
        val containerNames = remember(uiState.filteredItems) {
            uiState.filteredItems.mapNotNull { it.getDisplayLocation() }.toSet()
        }
        CollectionUnequipRepackDialog(
            containerNames = containerNames,
            onDismiss = { showUnequipCollectionDialog = false },
            onUnequipOnly = {
                viewModel.unequipCollection(false)
                showUnequipCollectionDialog = false
            },
            onRepack = {
                viewModel.unequipCollection(true)
                showUnequipCollectionDialog = false
            }
        )
    }
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
                    Text(collection.icon ?: "📦", fontSize = 32.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(collection.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (collection.tags.isNotEmpty()) {
                        Text(
                            text = collection.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            collection.description?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            val progress = readiness?.readinessPercentage ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text("${progress.toInt()}% Ready", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            readiness?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${it.availableItems}/${it.totalItems} Available", style = MaterialTheme.typography.bodySmall)
                    Text("${it.packedItems} Packed", style = MaterialTheme.typography.bodySmall)
                    Text("${it.equippedItems} Equipped", style = MaterialTheme.typography.bodySmall)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor)
        ),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PackToContainerDialog(
    containers: List<InventoryItem>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pack to Container") },
        text = {
            if (containers.isEmpty()) {
                Text("No containers found. Please mark an item as a storage container first.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(containers) { container ->
                        ListItem(
                            headlineContent = { Text(container.name) },
                            supportingContent = { Text(container.location) },
                            leadingContent = { Icon(Icons.Default.Inventory, contentDescription = null) },
                            modifier = Modifier.clickable { onConfirm(container.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
            TextButton(onClick = onRepack) { Text("Repack") }
        },
        dismissButton = {
            TextButton(onClick = onUnequipOnly) { Text("Leave here") }
        }
    )
}
