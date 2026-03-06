@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.inventoria.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.ui.components.UnequipRepackDialog
import com.inventoria.app.ui.theme.PurplePrimary
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.ui.theme.Warning as ThemeWarning
import java.io.File
import java.text.NumberFormat

@Composable
fun ItemDetailScreen(
    onNavigateBack: () -> Unit,
    onEditItem: (Long) -> Unit,
    onLocationClick: (Double, Double) -> Unit,
    onNavigateToItemDetail: (Long) -> Unit, 
    onNavigateToCollection: (Long) -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnlinkDialog by remember { mutableStateOf<Long?>(null) }
    var showUnequipDialog by remember { mutableStateOf(false) }
    var showContainerPicker by remember { mutableStateOf(false) }

    val item = uiState.item ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            if (item.storage) {
                ExtendedFloatingActionButton(
                    onClick = { /* TODO: Add item to this container */ },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add Item Inside") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Info", "Connections", "History")
            
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ItemInfoTabContent(
                    item = item, 
                    onEditItem = onEditItem, 
                    onLocationClick = onLocationClick,
                    onUnequipClick = { showUnequipDialog = true }
                )
                1 -> ConnectionsTabContent(
                    item = item,
                    uiState = uiState,
                    onNavigateToItemDetail = onNavigateToItemDetail,
                    onNavigateToCollection = onNavigateToCollection,
                    onMoveClick = { showContainerPicker = true },
                    onRemoveLink = { leaderId -> showUnlinkDialog = leaderId }
                )
                2 -> Text("History coming soon...", modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete '${item.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(onDeleted = onNavigateBack)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnlinkDialog != null) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = null },
            title = { Text("Remove Link") },
            text = { Text("Are you sure you want to remove the link to this leader item?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeLink(showUnlinkDialog!!)
                        showUnlinkDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnequipDialog) {
        UnequipRepackDialog(
            itemName = item.name,
            containerName = uiState.lastParentName,
            onDismiss = { showUnequipDialog = false },
            onUnequipOnly = {
                viewModel.toggleEquip(repack = false)
                showUnequipDialog = false
            },
            onRepack = {
                viewModel.toggleEquip(repack = true)
                showUnequipDialog = false
            }
        )
    }

    if (showContainerPicker) {
        Dialog(onDismissRequest = { showContainerPicker = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Move to Container",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            ListItem(
                                headlineContent = { Text("No Container (Open Space)") },
                                leadingContent = { Icon(Icons.Default.Close, null) },
                                modifier = Modifier.clickable {
                                    viewModel.moveToContainer(null)
                                    showContainerPicker = false
                                }
                            )
                        }
                        
                        items(uiState.availableContainers) { container ->
                            ListItem(
                                headlineContent = { Text(container.name) },
                                supportingContent = { Text(container.location) },
                                leadingContent = { Icon(Icons.Default.Inventory, null) },
                                modifier = Modifier.clickable {
                                    viewModel.moveToContainer(container.id)
                                    showContainerPicker = false
                                }
                            )
                        }
                    }
                    
                    TextButton(
                        onClick = { showContainerPicker = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun ItemInfoTabContent(
    item: InventoryItem,
    onEditItem: (Long) -> Unit,
    onLocationClick: (Double, Double) -> Unit,
    onUnequipClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Section
        if (item.imageUrl != null) {
            val imageModel = if (item.imageUrl!!.startsWith("http")) {
                item.imageUrl
            } else {
                File(item.imageUrl!!)
            }
            
            AsyncImage(
                model = imageModel,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.ic_launcher_foreground)
            )
        }

        // Basic Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (item.description?.isNotBlank() == true) {
                    Text(item.description!!, style = MaterialTheme.typography.bodyLarge)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quantity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${item.quantity}", style = MaterialTheme.typography.titleMedium)
                    }
                    if (item.price != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(NumberFormat.getCurrencyInstance().format(item.price), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusColor = if (item.quantity > 0) Success else ThemeWarning
            val statusText = if (item.quantity > 0) "In Stock" else "Out of Stock"
            
            AssistChip(
                onClick = {},
                label = { Text(statusText) },
                colors = AssistChipDefaults.assistChipColors(labelColor = statusColor),
                border = AssistChipDefaults.assistChipBorder(borderColor = statusColor.copy(alpha = 0.5f))
            )

            if (item.storage) {
                AssistChip(
                    onClick = {},
                    label = { Text("Storage Container") },
                    leadingIcon = { Icon(Icons.Default.Inventory, null, Modifier.size(18.dp)) }
                )
            }
        }

        // Location Info
        val lat = item.latitude
        val lon = item.longitude
        if (item.equipped) {
            DetailItemRow(
                label = "Current Location", 
                value = "Equipped (With You)", 
                iconPainter = painterResource(R.drawable.mobile_24),
                onClick = onUnequipClick
            )
        } else if (lat != null && lon != null) {
            DetailItemRow(
                label = "Resolved Location", 
                value = item.location, 
                icon = Icons.Default.LocationOn,
                onClick = { onLocationClick(lat, lon) }
            )
        } else {
            DetailItemRow(label = "Primary Location", value = item.location, icon = Icons.Default.LocationOn)
        }

        // Custom Fields Section
        if (item.customFields.isNotEmpty()) {
            Divider()
            Text(
                text = "Custom Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            item.customFields.forEach { (key, value) ->
                DetailItemRow(label = key, value = value, icon = Icons.Default.Label)
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onEditItem(item.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Edit Main Details")
        }
    }
}

@Composable
fun ConnectionsTabContent(
    item: InventoryItem,
    uiState: ItemDetailUiState,
    onNavigateToItemDetail: (Long) -> Unit,
    onNavigateToCollection: (Long) -> Unit,
    onMoveClick: () -> Unit,
    onRemoveLink: (Long) -> Unit
) {
    val parentItem = uiState.parentItem
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Physical Container Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Physical Container", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onMoveClick) {
                    Text(if (parentItem != null) "Change" else "Add")
                }
            }
            if (parentItem != null) {
                Card(
                    onClick = { onNavigateToItemDetail(parentItem.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    ListItem(
                        headlineContent = { Text(parentItem.name) },
                        supportingContent = { Text("Storage Container", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                    )
                }
            } else {
                Text("Not inside any container.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Directed Links Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Follower Links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.links.isNotEmpty()) {
                uiState.links.forEach { link ->
                    val isFollower = link.followerId == item.id
                    val otherId = if (isFollower) link.leaderId else link.followerId
                    val otherName = uiState.linkNames[otherId] ?: "Unknown Item"
                    
                    Card(
                        onClick = { onNavigateToItemDetail(otherId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                    ) {
                        ListItem(
                            headlineContent = { Text(otherName) },
                            supportingContent = { Text(if (isFollower) "Following this leader" else "Leading this item") },
                            leadingContent = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            trailingContent = {
                                if (isFollower) {
                                    IconButton(onClick = { onRemoveLink(link.leaderId) }) {
                                        Icon(Icons.Default.LinkOff, contentDescription = "Unlink")
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                Text("No directed links active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Categories section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val categories = item.category?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            if (categories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        AssistChip(
                            onClick = { },
                            label = { Text(cat) },
                            leadingIcon = { Icon(Icons.Default.Label, null, Modifier.size(18.dp)) }
                        )
                    }
                }
            } else {
                Text("Uncategorized", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Collections section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.collections.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uiState.collections) { collection ->
                        AssistChip(
                            onClick = { onNavigateToCollection(collection.id) },
                            label = { Text(collection.name) },
                            leadingIcon = { Text(collection.icon ?: "📦") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(collection.color).copy(alpha = 0.1f),
                                labelColor = Color(collection.color)
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = Color(collection.color).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            } else {
                Text("Not part of any collections.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Tags section
        if (item.tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItemRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        
        if (onClick != null) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
