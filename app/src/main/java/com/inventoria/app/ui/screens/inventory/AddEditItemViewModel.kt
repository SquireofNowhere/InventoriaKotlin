
package com.inventoria.app.ui.screens.inventory

import android.content.Context
import android.location.Geocoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint
import java.util.Locale
import javax.inject.Inject

data class CustomField(
    val key: String,
    val value: String
)

data class AddEditItemUiState(
    val geoPoint: GeoPoint? = null,
    val address: String = "",
    val isResolvingAddress: Boolean = false,
    val storageItems: List<InventoryItem> = emptyList()
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    var name by mutableStateOf("")
    var quantity by mutableStateOf("")
    var price by mutableStateOf("")
    var category by mutableStateOf("")
    var description by mutableStateOf("")
    var isStorage by mutableStateOf(false)
    var parentId by mutableStateOf<Long?>(null)
    var isEquipped by mutableStateOf(false)
    
    private val _uiState = MutableStateFlow(AddEditItemUiState())
    val uiState: StateFlow<AddEditItemUiState> = _uiState

    val currentLocationGeoPoint: GeoPoint? 
        get() = _uiState.value.geoPoint

    var customFields = mutableStateListOf<CustomField>()
    private var currentItemId: Long? = null

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        val itemId: Long? = savedStateHandle.get<Long>("itemId")

        // Load storage items for parent selection
        viewModelScope.launch {
            repository.getStorageItems().collect { items ->
                _uiState.update { it.copy(storageItems = items.filter { it.id != itemId }) }
            }
        }

        if (itemId != null && itemId != 0L && itemId != -1L) {
            viewModelScope.launch {
                repository.getItemById(itemId)?.let { item ->
                    currentItemId = item.id
                    name = item.name
                    quantity = item.quantity.toString()
                    price = item.price?.toString() ?: ""
                    category = item.category ?: ""
                    description = item.description ?: ""
                    isStorage = item.isStorage
                    parentId = item.parentId
                    isEquipped = item.isEquipped
                    
                    val geoPoint = if (item.latitude != null && item.longitude != null) {
                        GeoPoint(item.latitude, item.longitude)
                    } else {
                        parseLocation(item.location)
                    }
                    
                    _uiState.update { it.copy(geoPoint = geoPoint, address = item.location) }

                    customFields.clear()
                    item.customFields.forEach { (key, value) ->
                        customFields.add(CustomField(key, value))
                    }
                }
            }
        } else {
            getCurrentLocation(isManual = false)
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

    fun updateLocation(geoPoint: GeoPoint) {
        _uiState.update { it.copy(geoPoint = geoPoint, isResolvingAddress = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val address = reverseGeocode(geoPoint)
            _uiState.update { it.copy(address = address, isResolvingAddress = false) }
        }
    }

    private fun reverseGeocode(geoPoint: GeoPoint): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                listOfNotNull(
                    addr.subThoroughfare,
                    addr.thoroughfare,
                    addr.subLocality,
                    addr.locality,
                    addr.countryName
                ).joinToString(", ")
            } else {
                "${geoPoint.latitude}, ${geoPoint.longitude}"
            }
        } catch (e: Exception) {
            "${geoPoint.latitude}, ${geoPoint.longitude}"
        }
    }

    private fun parseLocation(locationStr: String): GeoPoint? {
        return try {
            val parts = locationStr.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) return GeoPoint(lat, lon)
            }
            null
        } catch (e: Exception) { null }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getCurrentLocation(isManual: Boolean = true) {
        if (!isManual && _uiState.value.address.isNotBlank()) return

        viewModelScope.launch {
            try {
                val locationResult = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, null
                ).await()
                
                locationResult?.let {
                    updateLocation(GeoPoint(it.latitude, it.longitude))
                }
            } catch (e: Exception) { }
        }
    }

    fun addCustomField() { customFields.add(CustomField("", "")) }
    fun removeCustomField(index: Int) { if (index in customFields.indices) customFields.removeAt(index) }
    fun updateCustomField(index: Int, key: String, value: String) {
        if (index in customFields.indices) customFields[index] = CustomField(key, value)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            try {
                if (name.isBlank() || quantity.isBlank()) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Please fill in required fields"))
                    return@launch
                }
                
                // If no parent and not equipped, location is required
                if (parentId == null && !isEquipped && _uiState.value.address.isBlank()) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Location is required if not equipped or in a container"))
                    return@launch
                }

                val state = _uiState.value
                
                val item = InventoryItem(
                    id = currentItemId ?: 0L,
                    name = name,
                    quantity = quantity.toIntOrNull() ?: 0,
                    location = state.address,
                    latitude = state.geoPoint?.latitude,
                    longitude = state.geoPoint?.longitude,
                    price = price.toDoubleOrNull(),
                    isStorage = isStorage,
                    parentId = parentId,
                    isEquipped = isEquipped,
                    category = category.ifBlank { null },
                    description = description.ifBlank { null },
                    customFields = customFields.filter { it.key.isNotBlank() }.associate { it.key to it.value }
                )

                if (item.id == 0L) repository.insertItem(item) else repository.updateItem(item)
                _eventFlow.emit(UiEvent.SaveItem)
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Error saving item: ${e.message}"))
            }
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object SaveItem : UiEvent()
    }
}
