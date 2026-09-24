package com.inventoria.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.shared.model.InventoryItem
import com.inventoria.shared.remote.InventoriaSnapshot

@Composable
fun InventoryScreen(snapshot: InventoriaSnapshot) {
    var query by remember { mutableStateOf("") }
    val byId = remember(snapshot.items) { snapshot.items.associateBy { it.id } }
    val totalValue = snapshot.items.sumOf { it.getTotalValue() ?: 0.0 }
    val visible = remember(snapshot.items, query) {
        val q = query.trim().lowercase()
        snapshot.items
            .filter { item ->
                q.isEmpty() || listOfNotNull(item.name, item.location, item.category, item.description, item.sku, item.barcode)
                    .any { it.lowercase().contains(q) }
            }
            .sortedBy { it.name.lowercase() }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenTitle(
                "Inventory",
                "${snapshot.items.size} items · ${snapshot.items.count { it.equipped }} equipped" +
                    if (totalValue > 0) " · value ${formatMoney(totalValue)}" else ""
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name, location, tags, SKU…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (visible.isEmpty()) {
            item { EmptyState(if (query.isBlank()) "No items yet." else "Nothing matches \"$query\".") }
        }
        items(visible, key = { it.id }) { item -> ItemRow(item, item.parentId?.let(byId::get)) }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}

@Composable
private fun ItemRow(item: InventoryItem, parent: InventoryItem?) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name.ifBlank { "Unnamed item" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = when {
                    item.equipped -> "Equipped (on person)"
                    parent != null -> "In ${parent.name}"
                    else -> item.location.ifBlank { null }
                }
                val detail = listOfNotNull(where, item.getParsedTags().takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.storage) Pill("Container")
            Column(horizontalAlignment = Alignment.End) {
                Text("×${item.quantity}", style = MaterialTheme.typography.bodyLarge)
                item.getTotalValue()?.let {
                    Text(formatMoney(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
