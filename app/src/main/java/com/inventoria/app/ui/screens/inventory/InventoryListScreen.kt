package com.inventoria.app.ui.screens.inventory

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning
import java.text.NumberFormat
import kotlin.math.roundToInt

enum class DragAction { MOVE, LINK, NONE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryListScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    fromCollectionId: Long = 0L,
    viewModel: InventoryListViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeName by remember { mutableStateOf("") }

    val isSelectionMode = uiState.selectedItemIds.isNotEmpty()
    
    // Drag and Drop State
    val lazyListState = rememberLazyListState()
    var draggedItemId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverItemId by remember { mutableStateOf<Long?>(null) }
    var dragAction by remember { mutableStateOf(DragAction.NONE) }
    var isDraggingActive by remember { mutableStateOf(false) }
    var cumulativeDragAmount by remember { mutableStateOf(Offset.Zero) }
    
    // Context Menu State
    var contextMenuItemId by remember { mutableStateOf<Long?>(null) }

    val draggedItem = remember(draggedItemId, uiState.items) {
        uiState.items.find { it.id == draggedItemId }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedItemIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        if (uiState.selectedItemIds.size >= 2) {
                            IconButton(onClick = { 
                                mergeName = ""
                                showMergeDialog = true 
                            }) {
                                Icon(Icons.Default.Merge, contentDescription = "Merge & Rename")
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSelectedItems() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(if (fromCollectionId != 0L) "Collection Items" else "Inventory", fontWeight = FontWeight.Bold) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val nextOption = when (uiState.groupOption) {
                                GroupOption.NONE -> GroupOption.CATEGORY
                                GroupOption.CATEGORY -> GroupOption.LOCATION
                                GroupOption.LOCATION -> GroupOption.COLLECTION
                                GroupOption.COLLECTION -> GroupOption.NONE
                            }
                            viewModel.setGroupOption(nextOption)
                        }) {
                            Icon(
                                imageVector = when (uiState.groupOption) {
                                    GroupOption.CATEGORY -> Icons.Default.Category
                                    GroupOption.LOCATION -> Icons.Default.LocationOn
                                    GroupOption.COLLECTION -> Icons.Default.CollectionsBookmark
                                    else -> Icons.Default.GridView
                                },
                                contentDescription = "Cycle Grouping"
                            )
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            val isFiltered = uiState.activeTags.isNotEmpty() || uiState.activeCollections.isNotEmpty()
                            BadgedBox(
                                badge = { if (isFiltered) Badge() }
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (fromCollectionId == 0L && !isSelectionMode) {
                FloatingActionButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.search(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search items...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                viewModel.search("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (uiState.isFiltering || uiState.sortOption != SortOption.DATE_DESC || uiState.groupOption != GroupOption.NONE) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.groupOption != GroupOption.NONE) {
                            item {
                                SuggestionChip(
                                    onClick = { viewModel.setGroupOption(GroupOption.NONE) },
                                    label = { Text("Group: ${uiState.groupOption.displayName}") },
                                    icon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                        if (uiState.sortOption != SortOption.DATE_DESC) {
                            item {
                                SuggestionChip(
                                    onClick = { viewModel.setSortOption(SortOption.DATE_DESC) },
                                    label = { Text("Sort: ${uiState.sortOption.displayName}") },
                                    icon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                        if (uiState.activeTags.isNotEmpty() || uiState.activeCollections.isNotEmpty()) {
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No items found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(uiState.groupOption) {
                                if (uiState.groupOption != GroupOption.NONE) return@pointerInput
                                
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val item = lazyListState.layoutInfo.visibleItemsInfo.find { 
                                            offset.y.toInt() in it.offset..(it.offset + it.size) 
                                        }
                                        item?.let {
                                            val id = it.key as? Long
                                            if (id != null) {
                                                draggedItemId = id
                                                dragOffset = offset
                                                grabOffset = Offset(offset.x, offset.y - it.offset.toFloat())
                                                isDraggingActive = false 
                                                cumulativeDragAmount = Offset.Zero
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cumulativeDragAmount += dragAmount
                                        dragOffset += dragAmount
                                        
                                        if (cumulativeDragAmount.getDistance() > 15f) {
                                            isDraggingActive = true
                                        }
                                        
                                        if (isDraggingActive) {
                                            val hoverItem = lazyListState.layoutInfo.visibleItemsInfo.find {
                                                dragOffset.y.toInt() in it.offset..(it.offset + it.size)
                                            }
                                            val newHoverId = hoverItem?.key as? Long
                                            
                                            if (newHoverId != draggedItemId) {
                                                hoverItemId = newHoverId
                                                val target = uiState.items.find { it.id == newHoverId }
                                                dragAction = when {
                                                    target == null -> DragAction.NONE
                                                    target.storage -> DragAction.MOVE
                                                    else -> DragAction.LINK
                                                }
                                            } else {
                                                hoverItemId = null
                                                dragAction = DragAction.NONE
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (isDraggingActive) {
                                            if (draggedItemId != null && hoverItemId != null) {
                                                if (dragAction == DragAction.MOVE) {
                                                    viewModel.moveItem(draggedItemId!!, hoverItemId)
                                                    Toast.makeText(context, "Moved into container", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        draggedItemId = null
                                        hoverItemId = null
                                        dragAction = DragAction.NONE
                                        isDraggingActive = false
                                    },
                                    onDragCancel = {
                                        draggedItemId = null
                                        hoverItemId = null
                                        dragAction = DragAction.NONE
                                        isDraggingActive = false
                                    }
                                )
                            },
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        if (uiState.groupOption == GroupOption.NONE) {
                            items(uiState.filteredItems, key = { it.id }) { item ->
                                val depth = uiState.itemDepths[item.id] ?: 0
                                val hasChildren = uiState.itemHasChildren[item.id] ?: false
                                val isExpanded = item.id in uiState.expandedItemIds
                                val isMatched = uiState.matchedItemIds.contains(item.id)
                                val isHovered = hoverItemId == item.id

                                InventoryItemRow(
                                    item = item,
                                    depth = depth,
                                    hasChildren = hasChildren,
                                    isExpanded = isExpanded,
                                    isMatched = isMatched,
                                    isFiltering = uiState.isFiltering,
                                    isSelected = item.id in uiState.selectedItemIds,
                                    isDragged = draggedItemId == item.id && isDraggingActive,
                                    isHovered = isHovered && isDraggingActive,
                                    dragAction = if (isHovered) dragAction else DragAction.NONE,
                                    showContextMenu = contextMenuItemId == item.id,
                                    onDismissContextMenu = { contextMenuItemId = null },
                                    onClick = { 
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.id)
                                        } else {
                                            onItemClick(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        contextMenuItemId = item.id
                                    },
                                    onToggleExpansion = { viewModel.toggleItemExpansion(item.id) },
                                    onToggleSelection = { viewModel.toggleSelection(item.id) },
                                    onEdit = { onEditItem(item.id) },
                                    onDelete = { viewModel.deleteItem(item.id) },
                                    onToggleEquip = { viewModel.toggleEquip(item.id) }
                                )
                            }
                        } else {
                            uiState.groupedItems.forEach { (groupName, items) ->
                                item(key = "header_$groupName") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = groupName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                items(items, key = { "${groupName}_${it.id}" }) { item ->
                                    InventoryItemRow(
                                        item = item,
                                        isSelected = item.id in uiState.selectedItemIds,
                                        onClick = { 
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                onItemClick(item.id)
                                            }
                                        },
                                        onLongClick = {
                                            contextMenuItemId = item.id
                                        },
                                        onToggleSelection = { viewModel.toggleSelection(item.id) },
                                        onEdit = { onEditItem(item.id) },
                                        onDelete = { viewModel.deleteItem(item.id) },
                                        onToggleEquip = { viewModel.toggleEquip(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Drag Ghost
            if (draggedItem != null && isDraggingActive) {
                Box(
                    modifier = Modifier
                        .offset { 
                            IntOffset(
                                (dragOffset.x - grabOffset.x).roundToInt(), 
                                (dragOffset.y - grabOffset.y).roundToInt()
                            ) 
                        }
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                        .width(200.dp)
                        .alpha(0.9f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DragIndicator, contentDescription = null, tint = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text(draggedItem.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showMergeDialog) {
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Items") },
            text = {
                Column {
                    Text("Enter a new name for the merged item. Quantities will be added together and descriptions will be merged.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mergeName,
                        onValueChange = { mergeName = it },
                        label = { Text("New Item Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mergeName.isNotBlank()) {
                            viewModel.mergeSelectedItems(mergeName)
                            showMergeDialog = false
                        }
                    },
                    enabled = mergeName.isNotBlank()
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            FilterBottomSheetContent(
                uiState = uiState,
                onToggleCategory = { viewModel.toggleCategoryVisibility(it) },
                onToggleCollection = { viewModel.toggleCollectionVisibility(it) },
                onSetHardFilter = { viewModel.setHardFilterEnabled(it) },
                onSetInvertFilter = { viewModel.setInvertFilterEnabled(it) },
                onClearAll = { viewModel.clearFilters() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryItemRow(
    item: InventoryItem,
    depth: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    isMatched: Boolean = true,
    isFiltering: Boolean = false,
    isSelected: Boolean = false,
    isDragged: Boolean = false,
    isHovered: Boolean = false,
    dragAction: DragAction = DragAction.NONE,
    showContextMenu: Boolean = false,
    onDismissContextMenu: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleExpansion: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleEquip: () -> Unit = {}
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isHovered && dragAction == DragAction.MOVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHovered && dragAction == DragAction.LINK -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    val contentAlpha = if (isFiltering && !isMatched || isDragged) 0.4f else 1f
    val elevation by animateDpAsState(if (isHovered) 4.dp else 0.dp)

    Box(modifier = Modifier
        .alpha(contentAlpha)
        .shadow(elevation)
    ) {
        ListItem(
            headlineContent = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontWeight = if (isMatched && isFiltering) FontWeight.ExtraBold else FontWeight.SemiBold)
                    if (isHovered) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (dragAction == DragAction.MOVE) Icons.Default.MoveToInbox else Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            supportingContent = { 
                Column {
                    Text(item.location, style = MaterialTheme.typography.bodySmall)
                    if (item.category != null) {
                        Text(item.category!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) {
                        Spacer(modifier = Modifier.width((depth * 16).dp))
                    }
                    if (hasChildren) {
                        IconButton(onClick = onToggleExpansion, modifier = Modifier.size(24.dp)) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                         Spacer(modifier = Modifier.size(24.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            val primaryImage = item.getPrimaryImage()
                            if (primaryImage != null) {
                                AsyncImage(
                                    model = primaryImage,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = "Qty: ${item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.quantity > 0) Color.Unspecified else MaterialTheme.colorScheme.error
                        )
                        if (item.price != null) {
                            Text(
                                text = NumberFormat.getCurrencyInstance().format(item.price!! * item.quantity),
                                style = MaterialTheme.typography.labelSmall,
                                color = Success
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
            },
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(backgroundColor)
                .then(if (isHovered) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = onDismissContextMenu
        ) {
            DropdownMenuItem(
                text = { Text(if (isSelected) "Deselect" else "Select") },
                leadingIcon = { Icon(if (isSelected) Icons.Default.Close else Icons.Default.CheckCircle, contentDescription = null) },
                onClick = {
                    onToggleSelection()
                    onDismissContextMenu()
                }
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onEdit()
                    onDismissContextMenu()
                }
            )
            DropdownMenuItem(
                text = { Text(if (item.equipped) "Unequip" else "Equip") },
                leadingIcon = { 
                    Icon(
                        painter = painterResource(if (item.equipped) R.drawable.mobile_theft_24px else R.drawable.mobile_24px),
                        contentDescription = null
                    ) 
                },
                onClick = {
                    onToggleEquip()
                    onDismissContextMenu()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    onDismissContextMenu()
                }
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearAll) {
                Text("Clear All")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Filter Logic", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.isHardFilterEnabled, onCheckedChange = onSetHardFilter)
            Text("Hard Filter (Pass ALL selected)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.isInvertFilterEnabled, onCheckedChange = onSetInvertFilter)
            Text("Invert Filter (Block selected)", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        Text("Tags", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCategories.forEach { category ->
                FilterChip(
                    selected = category in uiState.activeTags,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Collections", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCollections.forEach { collection ->
                FilterChip(
                    selected = collection.id in uiState.activeCollections,
                    onClick = { onToggleCollection(collection.id) },
                    label = { Text(collection.name) }
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}
