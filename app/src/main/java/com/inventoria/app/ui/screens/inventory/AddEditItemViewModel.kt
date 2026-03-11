package com.inventoria.app.ui.screens.inventory

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.FirebaseStorageRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.*
import javax.inject.Inject

data class ImageUpload(
    val uri: Uri,
    var url: String? = null,
    var isUploading: Boolean = false,
    var isError: Boolean = false
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val storageRepository: FirebaseStorageRepository,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "AddEditItemViewModel"

    var name by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var price by mutableStateOf("")
    var category by mutableStateOf("")
    var description by mutableStateOf("")
    var isStorage by mutableStateOf(false)
    var parentId by mutableStateOf<Long?>(null)
    var isEquipped by mutableStateOf(false)
    
    // Existing URLs from the server
    val existingImageUrls = mutableStateListOf<String>()
    // Wrapped URIs for newly added pictures with state tracking
    val pendingImages = mutableStateListOf<ImageUpload>()
    
    var profilePictureUrl by mutableStateOf<String?>(null)
    var pendingProfilePictureUri by mutableStateOf<Uri?>(null)

    val currencySymbol: StateFlow<String> = combine(
        settingsRepository.getCurrencyCode(),
        settingsRepository.isAutoCurrencyEnabled()
    ) { code, auto ->
        if (auto) {
            try {
                Currency.getInstance(Locale.getDefault()).symbol
            } catch (e: Exception) {
                "$"
            }
        } else {
            try {
                Currency.getInstance(code).symbol
            } catch (e: Exception) {
                "$"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "$")

    val parsedCategories by derivedStateOf {
        category.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private val _uiState = MutableStateFlow(AddEditItemUiState())
    val uiState: StateFlow<AddEditItemUiState> = _uiState.asStateFlow()

    val customFields = mutableStateListOf<CustomField>()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var currentItemId: Long? = null

    init {
        val itemIdRaw = savedStateHandle.get<Any>("itemId")
        val itemId = when (itemIdRaw) {
            is Long -> itemIdRaw
            is String -> itemIdRaw.toLongOrNull()
            else -> null
        }

        val parentIdRaw = savedStateHandle.get<Any>("parentId")
        val initialParentId = when (parentIdRaw) {
            is Long -> parentIdRaw
            is String -> parentIdRaw.toLongOrNull()
            else -> null
        }

        viewModelScope.launch {
            repository.getStorageItems().collect { list ->
                _uiState.update { it.copy(storageItems = list.filter { it.id != itemId }) }
            }
        }

        if (itemId != null && itemId != 0L && itemId != -1L) {
            loadItem(itemId)
        } else {
            parentId = initialParentId
            getCurrentLocation(false)
        }

        viewModelScope.launch {
            savedStateHandle.getStateFlow<GeoPoint?>("selected_location", null).collectLatest { geoPoint ->
                if (geoPoint != null) {
                    updateLocation(geoPoint)
                    savedStateHandle.remove<GeoPoint>("selected_location")
                }
            }
        }
    }

    private fun loadItem(itemId: Long) {
        viewModelScope.launch {
            try {
                repository.getItemById(itemId)?.let { item ->
                    currentItemId = item.id
                    name = item.name
                    quantity = item.quantity.toString()
                    price = item.price?.toString() ?: ""
                    category = item.category ?: ""
                    description = item.description ?: ""
                    isStorage = item.storage
                    parentId = item.parentId
                    isEquipped = item.equipped
                    
                    existingImageUrls.clear()
                    existingImageUrls.addAll(item.imageUrls)
                    profilePictureUrl = item.profilePictureUrl
                    
                    val location = if (item.latitude != null && item.longitude != null) {
                        GeoPoint(item.latitude!!, item.longitude!!)
                    } else {
                        parseLocation(item.location)
                    }
                    
                    _uiState.update { it.copy(geoPoint = location, address = item.location) }
                    
                    customFields.clear()
                    item.customFields.forEach { (k, v) ->
                        customFields.add(CustomField(k, v))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading item", e)
            }
        }
    }

    fun updateLocation(geoPoint: GeoPoint) {
        viewModelScope.launch {
            _uiState.update { it.copy(geoPoint = geoPoint, isResolvingAddress = true) }
            val address = withContext(Dispatchers.IO) {
                reverseGeocode(geoPoint)
            }
            _uiState.update { it.copy(address = address, isResolvingAddress = false) }
        }
    }

    fun updateAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    private fun reverseGeocode(geoPoint: GeoPoint): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            if (addresses.isNullOrEmpty()) {
                "${geoPoint.latitude}, ${geoPoint.longitude}"
            } else {
                val addr = addresses[0]
                listOfNotNull(
                    addr.subThoroughfare,
                    addr.thoroughfare,
                    addr.subLocality,
                    addr.locality,
                    addr.countryName
                ).joinToString(", ")
            }
        } catch (e: Exception) {
            "${geoPoint.latitude}, ${geoPoint.longitude}"
        }
    }

    private fun parseLocation(locationStr: String): GeoPoint? {
        return try {
            val parts = locationStr.split(",")
            if (parts.size != 2) return null
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat == null || lon == null) null
            else GeoPoint(lat, lon)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentLocation(isManual: Boolean = true) {
        if (isManual || _uiState.value.address.isBlank()) {
            viewModelScope.launch {
                try {
                    val location = fusedLocationClient.getCurrentLocation(100, null).await()
                    location?.let {
                        updateLocation(GeoPoint(it.latitude, it.longitude))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching location", e)
                }
            }
        }
    }

    fun addCustomField() {
        customFields.add(CustomField("", ""))
    }

    fun removeCustomField(index: Int) {
        if (index in customFields.indices) {
            customFields.removeAt(index)
        }
    }

    fun updateCustomField(index: Int, key: String, value: String) {
        if (index in customFields.indices) {
            customFields[index] = CustomField(key, value)
        }
    }

    fun removeExistingImage(url: String) {
        existingImageUrls.remove(url)
        if (profilePictureUrl == url) {
            profilePictureUrl = existingImageUrls.firstOrNull() ?: pendingImages.firstOrNull { it.url != null }?.url
        }
    }
    
    fun removePendingImage(upload: ImageUpload) {
        pendingImages.remove(upload)
        if (pendingProfilePictureUri == upload.uri) {
            pendingProfilePictureUri = pendingImages.firstOrNull()?.uri
        }
    }
    
    fun setProfilePicture(url: String) {
        profilePictureUrl = url
        pendingProfilePictureUri = null
    }
    
    fun setProfilePicture(uri: Uri) {
        pendingProfilePictureUri = uri
        profilePictureUrl = null
    }

    fun handleImageSelection(uri: Uri) {
        pendingImages.add(ImageUpload(uri))
        if (profilePictureUrl == null && pendingProfilePictureUri == null) {
            pendingProfilePictureUri = uri
        }
    }

    fun getTempImageUri(): Uri {
        val tempDir = File(context.cacheDir, "temp_images").apply { mkdirs() }
        val file = File(tempDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            if (name.isBlank()) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Name is required"))
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            
            // Save initial item info immediately
            val initialItem = InventoryItem(
                id = currentItemId ?: System.currentTimeMillis(),
                name = name,
                quantity = quantity.toIntOrNull() ?: 1,
                price = price.toDoubleOrNull(),
                category = category.takeIf { it.isNotBlank() },
                description = description.takeIf { it.isNotBlank() },
                location = _uiState.value.address,
                latitude = _uiState.value.geoPoint?.latitude,
                longitude = _uiState.value.geoPoint?.longitude,
                storage = isStorage,
                parentId = parentId,
                equipped = isEquipped,
                imageUrls = existingImageUrls.toList(),
                profilePictureUrl = profilePictureUrl,
                customFields = customFields.associate { it.key to it.value }.filter { it.key.isNotBlank() }
            )

            try {
                if (currentItemId == null) {
                    repository.insertItem(initialItem)
                    currentItemId = initialItem.id
                } else {
                    repository.updateItem(initialItem)
                }
                
                // Trigger background upload so user can navigate back immediately
                repository.uploadImagesInBackground(
                    itemId = initialItem.id,
                    pendingUris = pendingImages.map { it.uri },
                    profileUri = pendingProfilePictureUri,
                    existingUrls = existingImageUrls.toList(),
                    currentProfileUrl = profilePictureUrl
                )
                
                _uiState.update { it.copy(isLoading = false) }
                _eventFlow.emit(UiEvent.SaveItem)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _eventFlow.emit(UiEvent.ShowSnackbar("Error saving item: ${e.message}"))
            }
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String, val isError: Boolean = false) : UiEvent()
        object SaveItem : UiEvent()
    }
}
