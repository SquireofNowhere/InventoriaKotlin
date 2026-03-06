@file:OptIn(ExperimentalMaterial3Api::class)

package com.inventoria.app.ui.screens.collections

import androidx.compose.animation.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionWithItems
import com.inventoria.app.data.model.InventoryCollectionReadiness
import com.inventoria.app.ui.components.UnequipRepackDialog
import com.inventoria.app.ui.screens.inventory.InventoryItemRow
import kotlin.math.roundToInt

@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onNavigateBack: () -> Unit,
    onEditCollection: (Long) -> Unit,
    onNavigateToAddItems: (Long) -> Unit,
    onNavigateToItemDetail: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val collectionWithItems by viewModel.collectionWithItems.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val packResult by viewModel.packResult.collectAsState(initial = null)
    val availableContainers by viewModel.availableContainers.collectAsState()

    var showPackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }
    var showUnequipCollectionDialog by remember { mutableStateOf(false) }
    var containerNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentContainerName by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // Drag and Drop State
    var draggedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var currentPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    
    var showLinkPrompt by remember { mutableStateOf<Pair<InventoryItem, InventoryItem>?>(null) }
    var showChoicePrompt by remember { mutableStateOf<Pair<InventoryItem, InventoryItem>?>(null) }

    val density = LocalDensity.current
    val ghostWidthPx = with(density) { 260.dp.toPx() }
    val ghostHeightPx = with(density) { 64.dp.toPx() }

    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    LaunchedEffect(showUnequipCollectionDialog, collectionWithItems) {
        if (showUnequipCollectionDialog && collectionWithItems != null) {
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

    LaunchedEffect(itemToUnequip) {
        if (itemToUnequip?.lastParentId != null) {
            currentContainerName = viewModel.getContainerName(itemToUnequip!!.lastParentId!!)
        } else {
            currentContainerName = null
        }
    }

    LaunchedEffect(packResult) {
        packResult?.let {
            snackbarHostState.showSnackbar(it.message)
        }
    }

    // Update drop target while dragging
    LaunchedEffect(currentPointerPosition, draggedItem) {
        if (draggedItem != null) {
            val target = itemBounds.entries.find { (id, rect) ->
                id != draggedItem?.id && rect.contains(currentPointerPosition)
            }?.key
            if (target != null && target != dropTargetId) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            dropTargetId = target
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
                    IconButton(onClick = { onEditCollection(collectionId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { coords ->
                    containerOffset = coords.positionInWindow()
                }
        ) {
            if (collectionWithItems == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val data = collectionWithItems!!
                val collection = data.collection

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                            onUnequip = { showUnequipCollectionDialog = true },
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

                    if (uiState.filteredItems.isEmpty()) {
                        item {
                            Text(
                                text = "No items in this collection yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        // Show items in collection. Preserve hierarchy if parent is ALSO in collection.
                        // Otherwise, show as root.
                        val rootItems = uiState.filteredItems.filter { item ->
                            val parentId = item.parentId
                            parentId == null || !uiState.collectionItemIds.contains(parentId)
                        }
                        
                        items(rootItems, key = { it.id }) { item ->
                            InventoryItemRow(
                                item = item,
                                allItems = uiState.filteredItems,
                                matchedItemIds = uiState.matchedItemIds,
                                depth = 0,
                                expandedItemIds = uiState.expandedItemIds,
                                draggedItemId = draggedItem?.id,
                                dropTargetId = dropTargetId,
                                collectionItemIds = uiState.collectionItemIds,
                                linkedItemIds = uiState.linkedItemIds,
                                onToggleExpand = { id -> viewModel.toggleItemExpansion(id) },
                                onItemClick = { id -> onNavigateToItemDetail(id) },
                                onToggleEquip = { id ->
                                    val itm = uiState.items.find { it.id == id }
                                    if (itm?.equipped == true && itm.lastParentId != null) {
                                        itemToUnequip = itm
                                    } else {
                                        viewModel.toggleEquip(id)
                                    }
                                },
                                onDragStart = { itm, windowPos ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggedItem = itm
                                    currentPointerPosition = windowPos
                                },
                                onDrag = { delta -> currentPointerPosition += delta },
                                onDragEnd = {
                                    if (draggedItem != null) {
                                        if (dropTargetId != null) {
                                            val target = uiState.items.find { it.id == dropTargetId }
                                            if (target != null) {
                                                if (draggedItem!!.storage && target.storage) {
                                                    showChoicePrompt = draggedItem!! to target
                                                } else if (target.storage) {
                                                    viewModel.moveItem(draggedItem!!.id, dropTargetId)
                                                } else {
                                                    showLinkPrompt = draggedItem!! to target
                                                }
                                            }
                                        } else {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.moveItem(draggedItem!!.id, null)
                                        }
                                    }
                                    draggedItem = null
                                    currentPointerPosition = Offset.Zero
                                    dropTargetId = null
                                },
                                onPositioned = { id, rect -> itemBounds[id] = rect },
                                isSearchMode = false
                            )
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // Ghost Preview
            draggedItem?.let { item ->
                Surface(
                    modifier = Modifier
                        .size(width = 260.dp, height = 64.dp)
                        .offset { 
                            IntOffset(
                                (currentPointerPosition.x - containerOffset.x - ghostWidthPx / 2).roundToInt(), 
                                (currentPointerPosition.y - containerOffset.y - ghostHeightPx / 2).roundToInt()
                            ) 
                        }
                        .zIndex(100f)
                        .graphicsLayer { alpha = 0.9f; scaleX = 1.05f; scaleY = 1.05f },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
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

    if (showUnequipCollectionDialog) {
        CollectionUnequipRepackDialog(
            containerNames = containerNames,
            onDismiss = { showUnequipCollectionDialog = false },
            onUnequipOnly = {
                viewModel.unequipCollection(repack = false)
                showUnequipCollectionDialog = false
            },
            onRepack = {
                viewModel.unequipCollection(repack = true)
                showUnequipCollectionDialog = false
            }
        )
    }

    itemToUnequip?.let { item ->
        UnequipRepackDialog(
            itemName = item.name,
            containerName = currentContainerName,
            onDismiss = { itemToUnequip = null },
            onUnequipOnly = {
                viewModel.toggleEquip(item.id, repack = false)
                itemToUnequip = null
            },
            onRepack = {
                viewModel.toggleEquip(item.id, repack = true)
                itemToUnequip = null
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

    showLinkPrompt?.let { (follower, leader) ->
        AlertDialog(
            onDismissRequest = { showLinkPrompt = null },
            title = { Text("Link Items?") },
            text = { Text("Do you want to link '${follower.name}' to follow '${leader.name}'? '${follower.name}' will automatically update its location whenever '${leader.name}' moves.") },
            confirmButton = {
                Button(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.linkItem(follower.id, leader.id)
                    showLinkPrompt = null
                }) {
                    Text("Link Items")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkPrompt = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showChoicePrompt?.let { (dragged, target) ->
        AlertDialog(
            onDismissRequest = { showChoicePrompt = null },
            title = { Text("Move or Link?") },
            text = { Text("Do you want to put '${dragged.name}' inside '${target.name}', or just link them so they follow each other?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveItem(dragged.id, target.id)
                    showChoicePrompt = null
                }) {
                    Text("Store Inside")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.linkItem(dragged.id, target.id)
                    showChoicePrompt = null
                }) {
                    Text("Link Only")
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
            
            val desc = collection.description
            if (!desc.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium)
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
