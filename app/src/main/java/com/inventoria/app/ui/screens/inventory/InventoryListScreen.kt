
package com.inventoria.app.ui.screens.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") }
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
            } else if (uiState.filteredItems.isEmpty()) {
                 Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items found")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredItems) { item ->
                        InventoryItemCard(
                            name = item.name,
                            quantity = item.quantity,
                            location = item.location,
                            price = item.price,
                            onClick = { onItemClick(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryItemCard(
    name: String,
    quantity: Int,
    location: String,
    price: Double?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Location: $location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Qty: $quantity",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (quantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                if (price != null) {
                    val formattedPrice = String.format("%.2f", price)
                    Text(
                        text = "$$formattedPrice",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
