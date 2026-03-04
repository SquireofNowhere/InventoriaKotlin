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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.screens.dashboard.UnequipRepackDialog
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
    var expandedItemIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var collapsedGroupNames by rememberSaveable { mutableStateOf(setOf<String>()) }
    var itemToUnequip by remember { mutableStateOf<InventoryItem?>(null) }
    var containerName by remember { mutableStateOf<String?>(null) }
    
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

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

    var draggedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var currentPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    LaunchedEffect(fromCollectionId) {
        if (fromCollectionId != 0L) {
            viewModel.setCollectionId(fromCollectionId)
        }
    }

    LaunchedEffect(currentPointerPosition, draggedItem) {
        if (draggedItem != null) {
            val target = itemBounds.entries.find { (id, rect) ->
                id != draggedItem?.id && rect.contains(currentPointerPosition)
            }?.key
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
                            SortOption.values().forEach { option ->
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
                            GroupOption.values().forEach { option ->
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                                    label = { Text("Filters Active") },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
                
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
                            val displayItems = if (searchQuery.isNotBlank() || uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()) {
                                uiState.filteredItems
                            } else {
                                uiState.filteredItems.filter { it.parentId == null }
                            }

                            items(displayItems, key = { it.id }) { item ->
                                InventoryItemRow(
                                    item = item,
                                    allItems = uiState.items,
                                    depth = 0,
                                    expandedItemIds = expandedItemIds,
                                    draggedItemId = draggedItem?.id,
                                    dropTargetId = dropTargetId,
                                    collectionItemIds = uiState.collectionItemIds,
                                    isSelectionMode = fromCollectionId != 0L,
                                    onToggleExpand = { id ->
                                        expandedItemIds = if (expandedItemIds.contains(id)) expandedItemIds - id else expandedItemIds + id
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
                                    onDragStart = { itm, pos -> 
                                        draggedItem = itm
                                        dragOffset = Offset.Zero
                                        currentPointerPosition = pos
                                    },
                                    onDrag = { delta ->
                                        dragOffset += delta
                                        currentPointerPosition += delta
                                    },
                                    onDragEnd = {
                                        if (draggedItem != null) {
                                            viewModel.moveItem(draggedItem!!.id, dropTargetId)
                                        }
                                        draggedItem = null
                                        dragOffset = Offset.Zero
                                        currentPointerPosition = Offset.Zero
                                        dropTargetId = null
                                    },
                                    onPositioned = { id, rect -> itemBounds[id] = rect },
                                    isSearchMode = searchQuery.isNotBlank() || uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()
                                )
                            }
                        } else {
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
                                            allItems = uiState.items,
                                            depth = 0,
                                            expandedItemIds = expandedItemIds,
                                            draggedItemId = draggedItem?.id,
                                            dropTargetId = dropTargetId,
                                            collectionItemIds = uiState.collectionItemIds,
                                            isSelectionMode = fromCollectionId != 0L,
                                            onToggleExpand = { id ->
                                                expandedItemIds = if (expandedItemIds.contains(id)) expandedItemIds - id else expandedItemIds + id
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
                                            onDragStart = { itm, pos -> 
                                                draggedItem = itm
                                                dragOffset = Offset.Zero
                                                currentPointerPosition = pos
                                            },
                                            onDrag = { delta ->
                                                dragOffset += delta
                                                currentPointerPosition += delta
                                            },
                                            onDragEnd = {
                                                if (draggedItem != null) {
                                                    viewModel.moveItem(draggedItem!!.id, dropTargetId)
                                                }
                                                draggedItem = null
                                                dragOffset = Offset.Zero
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

            draggedItem?.let { item ->
                Surface(
                    modifier = Modifier
                        .size(width = 260.dp, height = 64.dp)
                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheetContent(
    uiState: InventoryUiState,
    onToggleCategory: (String) -> Unit,
    onToggleCollection: (Long) -> Unit,
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

        Text("Hide Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCategories.forEach { category ->
                val isHidden = uiState.hiddenCategories.contains(category)
                FilterChip(
                    selected = !isHidden,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category) },
                    leadingIcon = { if (!isHidden) Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)) else Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp)) }
                )
            }
            val isUncatHidden = uiState.hiddenCategories.contains("Uncategorized")
            FilterChip(
                selected = !isUncatHidden,
                onClick = { onToggleCategory("Uncategorized") },
                label = { Text("Uncategorized") },
                leadingIcon = { if (!isUncatHidden) Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)) else Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp)) }
            )
        }

        Divider()

        Text("Hide Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCollections.forEach { collection ->
                val isHidden = uiState.hiddenCollections.contains(collection.id)
                FilterChip(
                    selected = !isHidden,
                    onClick = { onToggleCollection(collection.id) },
                    label = { Text(collection.name) },
                    leadingIcon = { if (!isHidden) Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)) else Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    allItems: List<InventoryItem>,
    depth: Int,
    expandedItemIds: Set<Long>,
    draggedItemId: Long?,
    dropTargetId: Long?,
    collectionItemIds: Set<Long>,
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
    val isContainer = item.storage || children.isNotEmpty()
    val isBeingDragged = draggedItemId == item.id
    val isPotentialDropTarget = dropTargetId == item.id && draggedItemId != item.id
    val isInCollection = collectionItemIds.contains(item.id)

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
                        if (isContainer && !isSearchMode) onToggleExpand(item.id) else onItemClick(item.id)
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
                if (isContainer && !isSearchMode) {
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
                    Icon(
                        imageVector = if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                        contentDescription = null,
                        tint = if (item.storage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isContainer) FontWeight.Bold else FontWeight.Normal
                    )
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

        if (isExpanded && !isSearchMode) {
            children.forEach { child ->
                InventoryItemRow(
                    item = child,
                    allItems = allItems,
                    depth = depth + 1,
                    expandedItemIds = expandedItemIds,
                    draggedItemId = draggedItemId,
                    dropTargetId = dropTargetId,
                    collectionItemIds = collectionItemIds,
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
