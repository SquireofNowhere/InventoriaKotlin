package com.inventoria.app.ui.screens.collections

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.model.InventoryCollectionType
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCollectionScreen(
    collectionId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditCollectionViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = collectionId != null && collectionId != 0L
    var showIconColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is AddEditCollectionViewModel.UiEvent.SaveSuccess -> onNavigateBack()
                is AddEditCollectionViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (!isEditing) "Create Collection" else "Edit Collection", fontWeight = FontWeight.Bold) },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon and Color Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(viewModel.color).copy(alpha = 0.2f))
                        .clickable { showIconColorPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(viewModel.icon, fontSize = 40.sp)
                }
            }

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Collection Name") },
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

            OutlinedTextField(
                value = viewModel.tags,
                onValueChange = { viewModel.tags = it },
                label = { Text("Tags (Comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Collection Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InventoryCollectionType.entries.forEach { type ->
                    FilterChip(
                        selected = viewModel.collectionType == type,
                        onClick = { viewModel.collectionType = type },
                        label = { Text(type.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = viewModel.requiresSameLocation,
                    onCheckedChange = { viewModel.requiresSameLocation = it }
                )
                Text("Require items to be in the same location")
            }

            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.onSave() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (!isEditing) "Create Collection" else "Update Collection")
            }
        }
    }

    if (showIconColorPicker) {
        IconColorPickerDialog(
            currentIcon = viewModel.icon,
            currentColor = viewModel.color,
            onDismiss = { showIconColorPicker = false },
            onConfirm = { icon, color ->
                viewModel.icon = icon
                viewModel.color = color
                showIconColorPicker = false
            }
        )
    }
}

/** A curated emoji + a curated palette rather than a full picker -- collections are a small,
 * personal set of things (kits, outfits, gear), not a place that needs millions of emoji or an
 * arbitrary hue wheel. [currentColor] is only used to highlight the selection; there is no free
 * colour entry, so it will always be one of [COLLECTION_COLOR_OPTIONS]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconColorPickerDialog(
    currentIcon: String,
    currentColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var icon by remember { mutableStateOf(currentIcon) }
    var color by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon & Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(color).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 32.sp)
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLLECTION_ICON_OPTIONS.forEach { option ->
                        val selected = option == icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(color).copy(alpha = 0.25f) else Color.Transparent)
                                .then(
                                    if (selected) Modifier.border(2.dp, Color(color), CircleShape)
                                    else Modifier
                                )
                                .clickable { icon = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option, fontSize = 22.sp)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLLECTION_COLOR_OPTIONS.forEach { option ->
                        val selected = option == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(option))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = option }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(icon, color) }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val COLLECTION_ICON_OPTIONS = listOf(
    "📦", "🎒", "🧳", "⚙️", "🏕️", "👔", "🚨", "🎮",
    "🔧", "🏠", "📷", "🎨", "⚽", "💊", "🎣", "🧰"
)

private val COLLECTION_COLOR_OPTIONS = listOf(
    0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
    0xFF00ACC1.toInt(), 0xFF2196F3.toInt(), 0xFF5E35B1.toInt(), 0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFF212121.toInt()
)
