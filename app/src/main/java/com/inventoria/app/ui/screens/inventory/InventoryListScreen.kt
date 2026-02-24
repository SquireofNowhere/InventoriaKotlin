package com.inventoria.app.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryItem

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
    
    // Track expanded state for storage items
    var expandedItemIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search inventory...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                shape = MaterialTheme.shapes.medium
            )
            
            // Item List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                 Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items found")
                }
            } else {
                val displayItems = if (searchQuery.isNotBlank()) {
                    uiState.filteredItems
                } else {
                    // Build the tree only when not searching for a "project view" feel
                    uiState.items.filter { it.parentId == null }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayItems) { item ->
                        InventoryItemRow(
                            item = item,
                            allItems = uiState.items,
                            depth = 0,
                            expandedItemIds = expandedItemIds,
                            onToggleExpand = { id ->
                                expandedItemIds = if (expandedItemIds.contains(id)) {
                                    expandedItemIds - id
                                } else {
                                    expandedItemIds + id
                                }
                            },
                            onItemClick = onItemClick,
                            isSearchMode = searchQuery.isNotBlank()
                        )
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
    onToggleExpand: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
    isSearchMode: Boolean
) {
    val isExpanded = expandedItemIds.contains(item.id)
    val children = remember(item.id, allItems) {
        allItems.filter { it.parentId == item.id }
    }
    val hasChildren = children.isNotEmpty() || item.isStorage

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    if (item.isStorage && !isSearchMode) {
                        onToggleExpand(item.id)
                    } else {
                        onItemClick(item.id)
                    }
                },
            color = if (depth == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(start = (depth * 24).dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand/Collapse Icon or Spacer
                if (item.isStorage && !isSearchMode) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Type Icon
                Icon(
                    imageVector = if (item.isStorage) Icons.Default.Inventory else Icons.Default.Category,
                    contentDescription = null,
                    tint = if (item.isStorage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (item.isStorage) FontWeight.Bold else FontWeight.Normal
                        )
                        if (item.isEquipped) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.mobile_theft_24px),
                                contentDescription = "Equipped",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (item.location.isNotBlank() && !item.isStorage && depth == 0) {
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Info Section
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "x${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.quantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (item.isStorage && hasChildren && !isSearchMode) {
                        Text(
                            text = "${children.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(onClick = { onItemClick(item.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Render children recursively if expanded
        if (isExpanded && !isSearchMode) {
            children.forEach { child ->
                InventoryItemRow(
                    item = child,
                    allItems = allItems,
                    depth = depth + 1,
                    expandedItemIds = expandedItemIds,
                    onToggleExpand = onToggleExpand,
                    onItemClick = onItemClick,
                    isSearchMode = isSearchMode
                )
            }
        }
    }
}
