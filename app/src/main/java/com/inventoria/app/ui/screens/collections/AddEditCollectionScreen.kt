package com.inventoria.app.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.data.model.InventoryCollectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCollectionScreen(
    collectionId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditCollectionViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(collectionId) {
        if (collectionId != null && collectionId != 0L) {
            viewModel.loadCollection(collectionId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AddEditCollectionViewModel.UiEvent.SaveSuccess -> onNavigateBack()
                is AddEditCollectionViewModel.UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (collectionId == null) "Create Collection" else "Edit Collection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onSave() }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.icon,
                    onValueChange = { if (it.length <= 2) viewModel.icon = it },
                    label = { Text("Icon") },
                    modifier = Modifier.width(80.dp),
                    placeholder = { Text("📦") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Theme Color", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFFC107, 0xFFF44336, 0xFF9C27B0)
                        for (colorInt in colors) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorInt.toInt()))
                                    .clickable { viewModel.color = colorInt.toInt() }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (viewModel.color == colorInt.toInt()) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = viewModel.collectionType.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    for (type in InventoryCollectionType.values()) {
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                viewModel.collectionType = type
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.tags,
                onValueChange = { viewModel.tags = it },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("travel, tech, essentials") }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = viewModel.requiresSameLocation,
                    onCheckedChange = { viewModel.requiresSameLocation = it }
                )
                Text("Requires same location for all items")
            }
            
            Button(
                onClick = { viewModel.onSave() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Collection")
            }
        }
    }
}
