package com.inventoria.app.ui.screens.inventory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditItemScreen(
    onNavigateBack: () -> Unit,
    onPickLocation: () -> Unit,
    viewModel: AddEditItemViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { viewModel.handleImageSelection(it) }
        }
        pendingCameraLaunch = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleImageSelection(it) }
    }

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    ) { granted ->
        if (granted && pendingCameraLaunch) {
            val uri = viewModel.getTempImageUri()
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is AddEditItemViewModel.UiEvent.SaveItem -> onNavigateBack()
                is AddEditItemViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (viewModel.name.isEmpty()) "Add Item" else "Edit Item", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.onSaveClick() }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { showImageSourceDialog = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (viewModel.previewImageUri != null) {
                        AsyncImage(
                            model = viewModel.previewImageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { viewModel.removeImage() },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Image", tint = Color.White)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text("Add Photo")
                        }
                    }
                    if (uiState.isUploadingImage) {
                        CircularProgressIndicator()
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Item Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.quantity,
                    onValueChange = { viewModel.quantity = it },
                    label = { Text("Quantity") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = viewModel.price,
                    onValueChange = { viewModel.price = it },
                    label = { Text("Price (Optional)") },
                    modifier = Modifier.weight(1f),
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            OutlinedTextField(
                value = viewModel.category,
                onValueChange = { viewModel.category = it },
                label = { Text("Category (Comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.parsedCategories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.parsedCategories.forEach { cat ->
                        SuggestionChip(onClick = {}, label = { Text(cat) })
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

            HorizontalDivider()

            Text("Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = uiState.address,
                onValueChange = { /* Manual editing handled via dialog usually */ },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = onPickLocation) {
                        Icon(Icons.Default.Map, contentDescription = "Pick on Map")
                    }
                }
            )

            Button(
                onClick = { viewModel.getCurrentLocation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isResolvingAddress
            ) {
                if (uiState.isResolvingAddress) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Get Current Location")
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.isStorage, onCheckedChange = { viewModel.isStorage = it })
                Text("This item is a container (can store other items)")
            }

            if (uiState.storageItems.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = uiState.storageItems.find { it.id == viewModel.parentId }?.name ?: "None (Base Level)",
                        onValueChange = {},
                        label = { Text("Stored Inside") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("None (Base Level)") },
                            onClick = {
                                viewModel.parentId = null
                                expanded = false
                            }
                        )
                        uiState.storageItems.forEach { storage ->
                            DropdownMenuItem(
                                text = { Text(storage.name) },
                                onClick = {
                                    viewModel.parentId = storage.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom Fields", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.addCustomField() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Field")
                }
            }

            viewModel.customFields.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = field.key,
                        onValueChange = { viewModel.updateCustomField(index, it, field.value) },
                        label = { Text("Key") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { viewModel.updateCustomField(index, field.key, it) },
                        label = { Text("Value") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeCustomField(index) }) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Image Source") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Camera") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            if (cameraPermissionState.status.isGranted) {
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
                        headlineContent = { Text("Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }
}
