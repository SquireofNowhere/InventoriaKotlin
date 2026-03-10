package com.inventoria.app.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning
import java.text.NumberFormat

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
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeName by remember { mutableStateOf("") }

    val isSelectionMode = uiState.selectedItemIds.isNotEmpty()

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
        Column(modifier = Modifier.padding(padding)) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (uiState.groupOption == GroupOption.NONE) {
                        items(uiState.filteredItems, key = { it.id }) { item ->
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
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleEquip: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent

    Box {
        ListItem(
            headlineContent = { Text(item.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = { 
                Column {
                    Text(item.location, style = MaterialTheme.typography.bodySmall)
                    if (item.category != null) {
                        Text(item.category!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                    onLongClick = { showContextMenu = true }
                )
                .background(backgroundColor)
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isSelected) "Deselect" else "Select") },
                leadingIcon = { Icon(if (isSelected) Icons.Default.Close else Icons.Default.CheckCircle, contentDescription = null) },
                onClick = {
                    onToggleSelection()
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onEdit()
                    showContextMenu = false
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
                    showContextMenu = false
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    showContextMenu = false
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
