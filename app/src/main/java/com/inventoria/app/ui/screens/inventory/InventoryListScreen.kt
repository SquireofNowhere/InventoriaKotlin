package com.inventoria.app.ui.screens.inventory

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var expandedItemIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    // Drag and Drop State
    var draggedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var currentPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    
    // Bounds tracking for hover detection
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    // Detect drop target based on pointer position
    LaunchedEffect(currentPointerPosition, draggedItem) {
        if (draggedItem != null) {
            val target = itemBounds.entries.find { (id, rect) ->
                id != draggedItem?.id && rect.contains(currentPointerPosition)
            }?.key
            
            // ANY item is a valid drop target now. Dynamic container logic.
            dropTargetId = target
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItem,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Item")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                    val displayItems = if (searchQuery.isNotBlank()) {
                        uiState.filteredItems
                    } else {
                        uiState.items.filter { it.parentId == null }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayItems, key = { it.id }) { item ->
                            InventoryItemRow(
                                item = item,
                                allItems = uiState.items,
                                depth = 0,
                                expandedItemIds = expandedItemIds,
                                draggedItemId = draggedItem?.id,
                                dropTargetId = dropTargetId,
                                onToggleExpand = { id ->
                                    expandedItemIds = if (expandedItemIds.contains(id)) expandedItemIds - id else expandedItemIds + id
                                },
                                onItemClick = onItemClick,
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
                                isSearchMode = searchQuery.isNotBlank()
                            )
                        }
                    }
                }
            }

            // Ghost Preview following the finger
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
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    allItems: List<InventoryItem>,
    depth: Int,
    expandedItemIds: Set<Long>,
    draggedItemId: Long?,
    dropTargetId: Long?,
    onToggleExpand: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
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

    var rowRect by remember { mutableStateOf(Rect.Zero) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isBeingDragged -> Color.Transparent
            isPotentialDropTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            depth == 0 -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "row_background"
    )

    val borderStroke = if (isPotentialDropTarget) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
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
                .padding(horizontal = if (isPotentialDropTarget) 4.dp else 0.dp)
                .pointerInput(item.id) {
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
                .clickable { 
                    if (isContainer && !isSearchMode) onToggleExpand(item.id) else onItemClick(item.id)
                },
            color = backgroundColor,
            shape = if (isPotentialDropTarget) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp),
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
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                    contentDescription = null,
                    tint = if (item.storage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

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
                
                IconButton(onClick = { onItemClick(item.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    onToggleExpand = onToggleExpand,
                    onItemClick = onItemClick,
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
