package com.inventoria.app.ui.screens.inventory

import android.annotation.SuppressLint
import android.content.Context
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

data class CustomField(
    val key: String,
    val value: String
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    var name by mutableStateOf("")
    var quantity by mutableStateOf("")
    var location by mutableStateOf("")
    var price by mutableStateOf("")
    var category by mutableStateOf("")
    var description by mutableStateOf("")
    
    // For the map picker
    var currentLocationGeoPoint by mutableStateOf<GeoPoint?>(null)

    var customFields = mutableStateListOf<CustomField>()
    private var currentItemId: Long? = null

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        // Now safely retrieved as Long because of NavType.LongType in NavHost
        val itemId: Long? = savedStateHandle.get<Long>("itemId")

        if (itemId != null && itemId != -1L && itemId != 0L) {
            viewModelScope.launch {
                repository.getItemById(itemId)?.let { item ->
                    currentItemId = item.id
                    name = item.name
                    quantity = item.quantity.toString()
                    location = item.location
                    price = item.price?.toString() ?: ""
                    category = item.category ?: ""
                    description = item.description ?: ""
                    
                    // Parse initial GeoPoint if exists
                    currentLocationGeoPoint = parseLocation(item.location)

                    customFields.clear()
                    item.customFields.forEach { (key, value) ->
                        customFields.add(CustomField(key, value))
                    }
                }
            }
        } else {
            getCurrentLocation(isManual = false)
        }

        // Observe changes from the Location Picker
        viewModelScope.launch {
            savedStateHandle.getStateFlow<GeoPoint?>("selected_location", null).collectLatest { geoPoint ->
                if (geoPoint != null) {
                    val address = savedStateHandle.get<String>("selected_address") ?: ""
                    updateLocation(geoPoint, address)
                    
                    // Clear the values so they don't trigger again
                    savedStateHandle.remove<GeoPoint>("selected_location")
                    savedStateHandle.remove<String>("selected_address")
                }
            }
        }
    }

    fun updateLocation(geoPoint: GeoPoint, text: String) {
        location = text
        currentLocationGeoPoint = geoPoint
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(isManual: Boolean = true) {
        if (!isManual && location.isNotBlank()) return

        viewModelScope.launch {
            try {
                val locationResult = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                
                locationResult?.let {
                    val coords = "${it.latitude}, ${it.longitude}"
                    if (isManual || location.isBlank()) {
                        updateLocation(GeoPoint(it.latitude, it.longitude), coords)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun parseLocation(locationStr: String): GeoPoint? {
        return try {
            val parts = locationStr.split(",")
            if (parts.size == 2) GeoPoint(parts[0].trim().toDouble(), parts[1].trim().toDouble()) else null
        } catch (e: Exception) { null }
    }

    fun addCustomField() { customFields.add(CustomField("", "")) }
    fun removeCustomField(index: Int) { if (index in customFields.indices) customFields.removeAt(index) }
    fun updateCustomField(index: Int, key: String, value: String) {
        if (index in customFields.indices) customFields[index] = CustomField(key, value)
    }

    fun onSaveClick() {
        viewModelScope.launch {
            try {
                if (name.isBlank() || quantity.isBlank() || location.isBlank()) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Please fill in required fields"))
                    return@launch
                }

                val customFieldsMap = customFields
                    .filter { it.key.isNotBlank() }
                    .associate { it.key to it.value }

                val item = InventoryItem(
                    id = currentItemId ?: 0L,
                    name = name,
                    quantity = quantity.toIntOrNull() ?: 0,
                    location = location,
                    price = price.toDoubleOrNull(),
                    category = category.ifBlank { null },
                    description = description.ifBlank { null },
                    customFields = customFieldsMap
                )

                if (currentItemId == null || currentItemId == 0L) {
                    repository.insertItem(item)
                } else {
                    repository.updateItem(item)
                }
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
