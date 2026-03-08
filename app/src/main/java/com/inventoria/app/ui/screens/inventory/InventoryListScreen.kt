package com.inventoria.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onNavigateBack: () -> Unit,
    fromCollectionId: Long = 0L,
    viewModel: InventoryListViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (fromCollectionId != 0L) "Collection Items" else "Inventory", fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        val isFiltered = uiState.hiddenCategories.isNotEmpty() || uiState.hiddenCollections.isNotEmpty()
                        BadgedBox(
                            badge = { if (isFiltered) Badge() }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (fromCollectionId == 0L) {
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
                                onClick = { onItemClick(item.id) },
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
                                    onClick = { onItemClick(item.id) },
                                    onToggleEquip = { viewModel.toggleEquip(item.id) }
                                )
                            }
                        }
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
                onToggleCategory = { viewModel.toggleCategoryVisibility(it) },
                onToggleCollection = { viewModel.toggleCollectionVisibility(it) },
                onSetHardFilter = { viewModel.setHardFilterEnabled(it) },
                onSetInvertFilter = { viewModel.setInvertFilterEnabled(it) },
                onClearAll = { viewModel.clearFilters() }
            )
        }
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    onClick: () -> Unit,
    onToggleEquip: () -> Unit
) {
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.storage) Icons.Default.Inventory else Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
        modifier = Modifier.clickable { onClick() }
    )
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
            Text("Hard Filter (Match ANY hidden)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.isInvertFilterEnabled, onCheckedChange = onSetInvertFilter)
            Text("Invert Filter (Show ONLY hidden)", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        Text("Categories", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.allCategories.forEach { category ->
                FilterChip(
                    selected = category !in uiState.hiddenCategories,
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
                    selected = collection.id !in uiState.hiddenCollections,
                    onClick = { onToggleCollection(collection.id) },
                    label = { Text(collection.name) }
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}
