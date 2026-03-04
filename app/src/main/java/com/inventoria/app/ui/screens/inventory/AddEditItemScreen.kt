package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditItemScreen(
    onNavigateBack: () -> Unit,
    onPickLocation: () -> Unit,
    viewModel: AddEditItemViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is AddEditItemViewModel.UiEvent.SaveItem -> {
                    onNavigateBack()
                }
                is AddEditItemViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(message = event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Add / Edit Item") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onSaveClick) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Item Name *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.quantity,
                onValueChange = { viewModel.quantity = it },
                label = { Text("Quantity *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.price,
                onValueChange = { viewModel.price = it },
                label = { Text("Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Equip Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equip Item", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Item follows your GPS location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.isEquipped,
                    onCheckedChange = { viewModel.isEquipped = it }
                )
            }

            // Storage Item Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Storage Item", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Can contain other items (e.g., a bag or box)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.isStorage,
                    onCheckedChange = { viewModel.isStorage = it }
                )
            }

            // Parent Storage Selection
            var expanded by remember { mutableStateOf(false) }
            val selectedParent = uiState.storageItems.find { it.id == viewModel.parentId }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedParent?.name ?: "None (Standalone)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Inside Container") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None (Standalone)") },
                        onClick = {
                            viewModel.parentId = null
                            expanded = false
                        }
                    )
                    uiState.storageItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                viewModel.parentId = item.id
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Location Section
            Box(modifier = Modifier.fillMaxWidth()) {
                val isInheritingLocation = viewModel.parentId != null
                val equipped = viewModel.isEquipped
                
                OutlinedTextField(
                    value = when {
                        equipped -> "Following your location"
                        isInheritingLocation -> "Inherited from container"
                        else -> uiState.address
                    },
                    onValueChange = { /* Read-only */ },
                    label = { Text("Location") },
                    readOnly = true,
                    enabled = !isInheritingLocation && !equipped,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        when {
                            equipped -> Text("Item is equipped and will move with you.")
                            isInheritingLocation -> Text("Location is inherited from the container it's inside.")
                            uiState.address.isBlank() -> Text("Location is required *", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    trailingIcon = {
                        if (!isInheritingLocation && !equipped) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isResolvingAddress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                IconButton(onClick = onPickLocation) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Pick on Map", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.category,
                    onValueChange = { viewModel.category = it },
                    label = { Text("Categories") },
                    placeholder = { Text("e.g. Tools, Electronics, Home") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Separate multiple categories with commas") }
                )
                
                if (viewModel.parsedCategories.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.parsedCategories.forEach { cat ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Custom Fields Section
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Fields",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = viewModel::addCustomField) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Field")
                }
            }

            viewModel.customFields.forEachIndexed { index, customField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customField.key,
                        onValueChange = { viewModel.updateCustomField(index, it, customField.value) },
                        label = { Text("Label") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customField.value,
                        onValueChange = { viewModel.updateCustomField(index, customField.key, it) },
                        label = { Text("Value") },
                        modifier = Modifier.weight(1.5f)
                    )
                    IconButton(onClick = { viewModel.removeCustomField(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Field",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Item")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
