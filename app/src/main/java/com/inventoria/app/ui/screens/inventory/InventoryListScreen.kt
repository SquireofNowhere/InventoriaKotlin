@file:OptIn(ExperimentalMaterial3Api::class)

package com.inventoria.app.ui.screens.inventory

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.components.UnequipRepackDialog
import java.io.File
import kotlin.math.roundToInt

@Composable
fun InventoryListScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    fromCollectionId: Long = 0L,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var collapsedGroupNames by rememberSaveable { mutableStateOf(setOf<String>()) }
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }
    var containerName by remember { mutableStateOf<String?>(null) }
    
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Haptic feedback for a premium feel
    val haptics = LocalHapticFeedback.current

    // Clear collapsed groups when grouping mode changes
    LaunchedEffect(uiState.groupOption) {
        collapsedGroupNames = emptySet()
    }

    LaunchedEffect(itemToUnequip) {
        if (itemToUnequip?.lastParentId != null) {
            containerName = viewModel.getContainerName(itemToUnequip!!.lastParentId!!)
        } else {
            containerName = null
        }
    }

    // Drag and Drop State logic
    var draggedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var currentPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }
    
    var showLinkPrompt by remember { mutableStateOf<Pair<InventoryItem, InventoryItem>?>(null) }
    var showUnlinkPrompt by remember { mutableStateOf<Pair<InventoryItem, InventoryItem>?>(null) }
    var showChoicePrompt by remember { mutableStateOf<Pair<InventoryItem, InventoryItem>?>(null) }

    val density = LocalDensity.current
    val ghostWidthPx = with(density) { 260.dp.toPx() }
    val ghostHeightPx = with(density) { 64.dp.toPx() }

    LaunchedEffect(fromCollectionId) {
        if (fromCollectionId != 0L) {
            viewModel.setCollectionId(fromCollectionId)
        }
    }

    // Update drop target while dragging
    LaunchedEffect(currentPointerPosition, draggedItem) {
        if (draggedItem != null) {
            val target = itemBounds.entries.find { (id, rect) ->
                id != draggedItem?.id && rect.contains(currentPointerPosition)
            }?.key
            
            // Provide a tiny buzz when hovering over a new valid target
            if (target != null && target != dropTargetId) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            dropTargetId = target
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fromCollectionId != 0L) "Add to Collection" else "Inventory") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // TREE CONTROLS (EXPAND/COLLAPSE ALL)
                    if (uiState.groupOption == GroupOption.NONE) {
                        IconButton(onClick = {
                            if (uiState.expandedItemIds.isEmpty()) viewModel.expandAll() else viewModel.collapseAll()
                        }) {
                            Icon(
                                imageVector = if (uiState.expandedItemIds.isEmpty()) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                contentDescription = "Toggle Tree"
                            )
                        }
                    }
                    
                    IconButton(onClick = { showFilterSheet = true }) {
                        val isFiltered = uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()
                        BadgedBox(badge = { if (isFiltered) Badge() }) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = { if (uiState.sortOption == option) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) {
                            Icon(Icons.Default.GroupWork, "Group")
                        }
                        DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                            GroupOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        viewModel.setGroupOption(option)
                                        showGroupMenu = false
                                    },
                                    leadingIcon = { if (uiState.groupOption == option) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (fromCollectionId == 0L) {
                FloatingActionButton(
                    onClick = onAddItem,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add Item")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coords ->
                    containerOffset = coords.positionInWindow()
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.search(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Search inventory...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    shape = MaterialTheme.shapes.medium
                )
                
                // Active Filters Chips
                if (uiState.groupOption != GroupOption.NONE || uiState.sortOption != SortOption.DATE_DESC || uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.groupOption != GroupOption.NONE) {
                            item {
                                SuggestionChip(
                                    onClick = { viewModel.setGroupOption(GroupOption.NONE) },
                                    label = { Text("Grouped by: ${uiState.groupOption.displayName}") },
                                    icon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                        if (uiState.sortOption != SortOption.DATE_DESC) {
                            item {
                                SuggestionChip(
                                    onClick = { viewModel.setSortOption(SortOption.DATE_DESC) },
                                    label = { Text("Sorted by: ${uiState.sortOption.displayName}") },
                                    icon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                        if (uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()) {
                            item {
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.clearFilters() },
                                    label = { Text(if (uiState.isInvertFilterEnabled) "Inclusion Filters Active" else "Exclusion Filters Active") },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
                
                // Item List
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.items.isEmpty()) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No items found")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.groupOption == GroupOption.NONE) {
                            val displayItems = uiState.filteredItems.filter { it.parentId == null }

                            items(displayItems, key = { it.id }) { item ->
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
                                    isSelectionMode = fromCollectionId != 0L,
                                    onToggleExpand = { id ->
                                        viewModel.toggleItemExpansion(id)
                                    },
                                    onItemClick = { id ->
                                        if (fromCollectionId != 0L) {
                                            viewModel.toggleItemInCollection(id, fromCollectionId)
                                        } else {
                                            onItemClick(id)
                                        }
                                    },
                                    onToggleEquip = { id ->
                                        val itm = uiState.items.find { it.id == id }
                                        if (itm != null) {
                                            if (itm.equipped) {
                                                if (itm.lastParentId != null) {
                                                    itemToUnequip = itm
                                                } else {
                                                    viewModel.toggleEquip(id, repack = false)
                                                }
                                            } else {
                                                viewModel.toggleEquip(id)
                                            }
                                        }
                                    },
                                    onDragStart = { itm, windowPos -> 
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggedItem = itm
                                        currentPointerPosition = windowPos
                                    },
                                    onDrag = { delta ->
                                        currentPointerPosition += delta
                                    },
                                    onDragEnd = {
                                        if (draggedItem != null) {
                                            if (dropTargetId != null) {
                                                val target = uiState.items.find { it.id == dropTargetId }
                                                if (target != null) {
                                                    val existingLink = uiState.allLinks.find {
                                                        (it.followerId == draggedItem!!.id && it.leaderId == target.id) ||
                                                        (it.followerId == target.id && it.leaderId == draggedItem!!.id)
                                                    }
                                                    if (draggedItem!!.storage && target.storage) {
                                                        showChoicePrompt = draggedItem!! to target
                                                    } else if (target.storage) {
                                                        viewModel.moveItem(draggedItem!!.id, dropTargetId)
                                                    } else {
                                                        if (existingLink != null) {
                                                            showUnlinkPrompt = draggedItem!! to target
                                                        } else {
                                                            showLinkPrompt = draggedItem!! to target
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Drop in empty space -> Move to ROOT
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.moveItem(draggedItem!!.id, null)
                                            }
                                        }
                                        draggedItem = null
                                        currentPointerPosition = Offset.Zero
                                        dropTargetId = null
                                    },
                                    onPositioned = { id, rect -> itemBounds[id] = rect },
                                    isSearchMode = uiState.isFiltering
                                )
                            }
                        } else {
                            // Grouped View
                            uiState.groupedItems.forEach { (groupName, groupItems) ->
                                val isCollapsed = collapsedGroupNames.contains(groupName)
                                item(key = "header_$groupName") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                collapsedGroupNames = if (isCollapsed) {
                                                    collapsedGroupNames - groupName
                                                } else {
                                                    collapsedGroupNames + groupName
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = groupName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ) {
                                                Text("${groupItems.size}")
                                            }
                                        }
                                    }
                                }
                                
                                if (!isCollapsed) {
                                    items(groupItems, key = { "${groupName}_${it.id}" }) { item ->
                                        InventoryItemRow(
                                            item = item,
                                            allItems = groupItems,
                                            matchedItemIds = uiState.matchedItemIds,
                                            depth = 0,
                                            expandedItemIds = uiState.expandedItemIds,
                                            draggedItemId = draggedItem?.id,
                                            dropTargetId = dropTargetId,
                                            collectionItemIds = uiState.collectionItemIds,
                                            linkedItemIds = uiState.linkedItemIds,
                                            isSelectionMode = fromCollectionId != 0L,
                                            onToggleExpand = { id ->
                                                viewModel.toggleItemExpansion(id)
                                            },
                                            onItemClick = { id ->
                                                if (fromCollectionId != 0L) {
                                                    viewModel.toggleItemInCollection(id, fromCollectionId)
                                                } else {
                                                    onItemClick(id)
                                                }
                                            },
                                            onToggleEquip = { id ->
                                                val itm = uiState.items.find { it.id == id }
                                                if (itm != null) {
                                                    if (itm.equipped) {
                                                        if (itm.lastParentId != null) {
                                                            itemToUnequip = itm
                                                        } else {
                                                            viewModel.toggleEquip(id, repack = false)
                                                        }
                                                    } else {
                                                        viewModel.toggleEquip(id)
                                                    }
                                                }
                                            },
                                            onDragStart = { itm, windowPos -> 
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedItem = itm
                                                currentPointerPosition = windowPos
                                            },
                                            onDrag = { delta ->
                                                currentPointerPosition += delta
                                            },
                                            onDragEnd = {
                                                if (draggedItem != null) {
                                                    if (dropTargetId != null) {
                                                        val target = uiState.items.find { it.id == dropTargetId }
                                                        if (target != null) {
                                                            val existingLink = uiState.allLinks.find {
                                                                (it.followerId == draggedItem!!.id && it.leaderId == target.id) ||
                                                                (it.followerId == target.id && it.leaderId == draggedItem!!.id)
                                                            }
                                                            if (draggedItem!!.storage && target.storage) {
                                                                showChoicePrompt = draggedItem!! to target
                                                            } else if (target.storage) {
                                                                viewModel.moveItem(draggedItem!!.id, dropTargetId)
                                                            } else {
                                                                if (existingLink != null) {
                                                                    showUnlinkPrompt = draggedItem!! to target
                                                                } else {
                                                                    showLinkPrompt = draggedItem!! to target
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        viewModel.moveItem(draggedItem!!.id, null)
                                                    }
                                                }
                                                draggedItem = null
                                                currentPointerPosition = Offset.Zero
                                                dropTargetId = null
                                            },
                                            onPositioned = { id, rect -> itemBounds[id] = rect },
                                            isSearchMode = true 
                                        )
                                    }
                                }
                            }
                        }
                    }
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

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            FilterBottomSheetContent(
                uiState = uiState,
                onToggleCategory = viewModel::toggleCategoryVisibility,
                onToggleCollection = viewModel::toggleCollectionVisibility,
                onSetHardFilter = viewModel::setHardFilterEnabled,
                onSetInvertFilter = viewModel::setInvertFilterEnabled,
                onClearAll = viewModel::clearFilters
            )
        }
    }

    itemToUnequip?.let { item ->
        UnequipRepackDialog(
            itemName = item.name,
            containerName = containerName,
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

    showUnlinkPrompt?.let { (itemA, itemB) ->
        val link = uiState.allLinks.find {
            (it.followerId == itemA.id && it.leaderId == itemB.id) ||
            (it.followerId == itemB.id && it.leaderId == itemA.id)
        }
        AlertDialog(
            onDismissRequest = { showUnlinkPrompt = null },
            title = { Text("Unlink Items?") },
            text = { Text("Do you want to unlink '${itemA.name}' and '${itemB.name}'? They will no longer follow each other's movements.") },
            confirmButton = {
                Button(onClick = {
                    if (link != null) {
                        viewModel.unlinkItem(link.followerId, link.leaderId)
                    }
                    showUnlinkPrompt = null
                }) {
                    Text("Unlink Items")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkPrompt = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showChoicePrompt?.let { (dragged, target) ->
        val existingLink = uiState.allLinks.find {
            (it.followerId == dragged.id && it.leaderId == target.id) ||
            (it.followerId == target.id && it.leaderId == dragged.id)
        }
        AlertDialog(
            onDismissRequest = { showChoicePrompt = null },
            title = { Text(if (existingLink != null) "Move or Unlink?" else "Move or Link?") },
            text = { 
                val actionText = if (existingLink != null) "unlink them" else "link them so they follow each other"
                Text("Do you want to put '${dragged.name}' inside '${target.name}', or $actionText?") 
            },
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
                    if (existingLink != null) {
                        viewModel.unlinkItem(existingLink.followerId, existingLink.leaderId)
                    } else {
                        viewModel.linkItem(dragged.id, target.id)
                    }
                    showChoicePrompt = null
                }) {
                    Text(if (existingLink != null) "Unlink" else "Link Only")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheetContent(
    uiState: InventoryUiState,
    onToggleCategory: (String) -> Unit,
    onToggleCollection: (Long) -> Unit,
    onSetHardFilter: (Boolean) -> Unit,
    onSetInvertFilter: (Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter Inventory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearAll) {
                Text("Clear All")
            }
        }

        // Filter Logic Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hard Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Items must match ALL selected filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isHardFilterEnabled,
                        onCheckedChange = onSetHardFilter
                    )
                }
                
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Invert Filter (Show Only Selected)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Only show items matching selected filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isInvertFilterEnabled,
                        onCheckedChange = onSetInvertFilter
                    )
                }
            }
        }

        Divider()

        Text(
            if (uiState.isInvertFilterEnabled) "Include Categories" else "Hide Categories", 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCategories.forEach { category ->
                val isSelected = uiState.hiddenCategories.contains(category)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category) },
                    leadingIcon = { 
                        if (isSelected) {
                            Icon(if (uiState.isInvertFilterEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, Modifier.size(18.dp)) 
                        } else null
                    }
                )
            }
            val isUncatSelected = uiState.hiddenCategories.contains("Uncategorized")
            FilterChip(
                selected = isUncatSelected,
                onClick = { onToggleCategory("Uncategorized") },
                label = { Text("Uncategorized") },
                leadingIcon = { 
                    if (isUncatSelected) {
                        Icon(if (uiState.isInvertFilterEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, Modifier.size(18.dp))
                    } else null
                }
            )
        }

        Divider()

        Text(
            if (uiState.isInvertFilterEnabled) "Include Collections" else "Hide Collections", 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCollections.forEach { collection ->
                val isSelected = uiState.hiddenCollections.contains(collection.id)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleCollection(collection.id) },
                    label = { Text(collection.name) },
                    leadingIcon = { 
                        if (isSelected) {
                            Icon(if (uiState.isInvertFilterEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, Modifier.size(18.dp))
                        } else null
                    }
                )
            }
        }
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    allItems: List<InventoryItem>,
    matchedItemIds: Set<Long>,
    depth: Int,
    expandedItemIds: Set<Long>,
    draggedItemId: Long?,
    dropTargetId: Long?,
    collectionItemIds: Set<Long>,
    linkedItemIds: Set<Long>,
    isSelectionMode: Boolean = false,
    onToggleExpand: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
    onToggleEquip: (Long) -> Unit,
    onDragStart: (InventoryItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (Long, Rect) -> Unit,
    isSearchMode: Boolean
) {
    if (depth > 15) return

    val isExpanded = expandedItemIds.contains(item.id)
    val children = remember(item.id, allItems) { allItems.filter { it.parentId == item.id } }
    val hasChildren = children.isNotEmpty()
    val isContainer = item.storage || hasChildren
    val isBeingDragged = draggedItemId == item.id
    val isPotentialDropTarget = dropTargetId == item.id && draggedItemId != item.id
    val isInCollection = collectionItemIds.contains(item.id)
    val isLinked = linkedItemIds.contains(item.id)
    val isDirectMatch = matchedItemIds.contains(item.id) || !isSearchMode

    var rowRect by remember { mutableStateOf(Rect.Zero) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isBeingDragged -> Color.Transparent
            isPotentialDropTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            isInCollection && isSelectionMode -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            depth == 0 -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "row_background"
    )

    val borderStroke = if (isPotentialDropTarget) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else if (isInCollection && isSelectionMode) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                rowRect = coords.boundsInWindow()
                onPositioned(item.id, rowRect)
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isPotentialDropTarget || (isInCollection && isSelectionMode)) 4.dp else 0.dp)
                .padding(vertical = if (isInCollection && isSelectionMode) 2.dp else 0.dp)
                .alpha(if (isDirectMatch) 1f else 0.6f)
                .pointerInput(item.id) {
                    if (!isSelectionMode) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> 
                                onDragStart(item, rowRect.topLeft + offset) 
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            }
                        )
                    }
                }
                .clickable { 
                    if (isSelectionMode) {
                        onItemClick(item.id)
                    } else {
                        if (hasChildren) onToggleExpand(item.id) else onItemClick(item.id)
                    }
                },
            color = backgroundColor,
            shape = if (isPotentialDropTarget || (isInCollection && isSelectionMode)) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp),
            border = borderStroke
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(start = (depth * 20).dp)
                    .fillMaxWidth()
                    .alpha(if (isBeingDragged) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clickable { onToggleExpand(item.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isSelectionMode) {
                    Checkbox(
                        checked = isInCollection,
                        onCheckedChange = { onItemClick(item.id) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    if (item.imageUrl != null) {
                        val imageModel = if (item.imageUrl!!.startsWith("http")) {
                            item.imageUrl
                        } else {
                            File(item.imageUrl!!)
                        }
                        AsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.ic_launcher_foreground) // Placeholder
                        )
                    } else {
                        Icon(
                            imageVector = if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                            contentDescription = null,
                            tint = if (item.storage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isDirectMatch) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDirectMatch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isLinked) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Linked",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (item.location.isNotBlank() && depth == 0) {
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isContainer && children.isNotEmpty()) {
                    Text(
                        text = "${children.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "x${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (!isSelectionMode) {
                    IconButton(onClick = { onToggleEquip(item.id) }) {
                        Icon(
                            painter = if (item.equipped) painterResource(R.drawable.mobile_theft_24px) else painterResource(R.drawable.mobile_24px),
                            contentDescription = if (item.equipped) "Unequip" else "Equip",
                            modifier = Modifier.size(20.dp),
                            tint = if (item.equipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onItemClick(item.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (isExpanded) {
            children.forEach { child ->
                InventoryItemRow(
                    item = child,
                    allItems = allItems,
                    matchedItemIds = matchedItemIds,
                    depth = depth + 1,
                    expandedItemIds = expandedItemIds,
                    draggedItemId = draggedItemId,
                    dropTargetId = dropTargetId,
                    collectionItemIds = collectionItemIds,
                    linkedItemIds = linkedItemIds,
                    isSelectionMode = isSelectionMode,
                    onToggleExpand = onToggleExpand,
                    onItemClick = onItemClick,
                    onToggleEquip = onToggleEquip,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onPositioned = onPositioned,
                    isSearchMode = isSearchMode
                )
            }
        }
    }
}
