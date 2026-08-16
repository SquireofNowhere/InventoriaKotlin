package com.inventoria.app.ui.screens.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.screens.collections.CollectionsScreen
import com.inventoria.app.ui.screens.collections.CollectionsViewModel
import com.inventoria.app.ui.screens.map.InventoryMapScreen
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import java.text.NumberFormat

enum class InventorySegment(val label: String) {
    ITEMS("Items"),
    COLLECTIONS("Collections"),
    MAP("Map")
}

/**
 * One tab for everything inventory. Items, Collections and Map used to be three of the seven
 * bottom-nav destinations; they're one now, switched between locally.
 *
 * The switcher is plain [rememberSaveable] state rather than a nested nav graph, deliberately.
 * A nested graph would report its *leaf* route to the shell, which would mean rewriting the
 * showNavigation guard that keeps item_location_map chrome-free, and it would need its own
 * saveState/restoreState dance -- the same mechanism whose misuse is documented at length on
 * InventoriaApp's switchToTab. Local state has no back stack to corrupt, and because it's backed
 * by this destination's NavBackStackEntry it still survives tab switches, drill-downs, rotation
 * and process death.
 *
 * The one thing a nested graph would have given for free is back-to-Items, hence the BackHandler.
 *
 * Items and Map share a single InventoryListViewModel, so the map shows whatever the list is
 * currently filtered to. (They were separate instances when Map was its own tab.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryHubScreen(
    inventoryViewModel: InventoryListViewModel,
    collectionsViewModel: CollectionsViewModel,
    hubViewModel: InventoryHubViewModel,
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onCollectionClick: (Long) -> Unit,
    onCreateCollection: () -> Unit
) {
    var segment by rememberSaveable { mutableStateOf(InventorySegment.ITEMS) }

    BackHandler(enabled = segment != InventorySegment.ITEMS) {
        segment = InventorySegment.ITEMS
    }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            InventorySegment.values().forEachIndexed { index, seg ->
                SegmentedButton(
                    selected = seg == segment,
                    onClick = { segment = seg },
                    shape = SegmentedButtonDefaults.itemShape(index, InventorySegment.values().size)
                ) {
                    Text(seg.label)
                }
            }
        }

        if (segment == InventorySegment.ITEMS) {
            InventoryStatsRow(hubViewModel)
        }

        Box(Modifier.weight(1f)) {
            when (segment) {
                InventorySegment.ITEMS -> InventoryListScreen(
                    viewModel = inventoryViewModel,
                    onAddItem = onAddItem,
                    onItemClick = onItemClick,
                    onEditItem = onEditItem,
                    // Null: inside a tab there is nothing above this to go back to.
                    onNavigateBack = null
                )
                InventorySegment.COLLECTIONS -> CollectionsScreen(
                    viewModel = collectionsViewModel,
                    onNavigateToCollectionDetail = onCollectionClick,
                    onNavigateToCreateCollection = onCreateCollection
                )
                InventorySegment.MAP -> InventoryMapScreen(
                    viewModel = inventoryViewModel,
                    initialLocation = null,
                    onItemClick = onItemClick,
                    onNavigateBack = null
                )
            }
        }
    }
}

@Composable
private fun InventoryStatsRow(hubViewModel: InventoryHubViewModel) {
    val totalItems by hubViewModel.totalItems.collectAsState()
    val totalValue by hubViewModel.totalValue.collectAsState()
    val showTotalValue by hubViewModel.showTotalValue.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InventoryStatCard(
            modifier = Modifier.weight(1f),
            title = "Total Items",
            value = totalItems.toString(),
            icon = Icons.Default.Inventory,
            color = PurplePrimary
        )
        if (showTotalValue) {
            InventoryStatCard(
                modifier = Modifier.weight(1f),
                title = "Total Value",
                value = NumberFormat.getCurrencyInstance().format(totalValue),
                icon = Icons.Default.AttachMoney,
                color = Success
            )
        }
    }
}

@Composable
private fun InventoryStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier.shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
