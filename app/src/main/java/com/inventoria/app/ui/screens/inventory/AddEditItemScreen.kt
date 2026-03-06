package com.inventoria.app.ui.screens.inventory

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalPermissionsApi::class)
@Composable
fun AddEditItemScreen(
    onNavigateBack: () -> Unit,
    onPickLocation: () -> Unit,
    viewModel: AddEditItemViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.handleImageSelection(tempPhotoUri)
        }
        pendingCameraLaunch = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.handleImageSelection(uri)
    }

    // Handle camera launch after permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted, pendingCameraLaunch) {
        if (cameraPermissionState.status.isGranted && pendingCameraLaunch) {
            val uri = viewModel.getTempImageUri()
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

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
            // Image Selection Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.previewImageUri != null) {
                    val imageModel = if (viewModel.previewImageUri!!.startsWith("http") || viewModel.previewImageUri!!.startsWith("content")) {
                        viewModel.previewImageUri
                    } else {
                        File(viewModel.previewImageUri!!)
                    }
                    
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Item Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showImageSourceDialog = true },
                        contentScale = ContentScale.Crop
                    )
                    
                    if (uiState.isUploadingImage) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    
                    // Remove Image Button
                    IconButton(
                        onClick = viewModel::removeImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove Photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clickable { showImageSourceDialog = true },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Change Photo",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showImageSourceDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (uiState.isUploadingImage) {
                                CircularProgressIndicator()
                            } else {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Add Photo", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

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
                shape = MaterialTheme.shapes.medium,
                enabled = !uiState.isUploadingImage
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Item")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Image Source") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Take Photo") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        modifier = Modifier.clickable {
                            if (cameraPermissionState.status.isGranted) {
                                showImageSourceDialog = false
                                val uri = viewModel.getTempImageUri()
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                pendingCameraLaunch = true
                                cameraPermissionState.launchPermissionRequest()
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Choose from Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
