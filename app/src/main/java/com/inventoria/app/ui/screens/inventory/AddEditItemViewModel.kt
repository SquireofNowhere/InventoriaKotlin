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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    
    // Custom fields state
    var customFields = mutableStateListOf<CustomField>()

    private var currentItemId: Long? = null

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        // Safely retrieve itemId which might be stored as a String by Compose Navigation
        val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
            ?: savedStateHandle.get<Long>("itemId")

        if (itemId != null && itemId != -1L && itemId != 0L) {
            // Edit Mode
            viewModelScope.launch {
                repository.getItemById(itemId)?.let { item ->
                    currentItemId = item.id
                    name = item.name
                    quantity = item.quantity.toString()
                    location = item.location
                    price = item.price?.toString() ?: ""
                    category = item.category ?: ""
                    description = item.description ?: ""
                    
                    // Load existing custom fields
                    customFields.clear()
                    item.customFields.forEach { (key, value) ->
                        customFields.add(CustomField(key, value))
                    }
                }
            }
        } else {
            // Add Mode: Fetch current location automatically
            getCurrentLocation(isManual = false)
        }

        // Observe results from the location picker
        observeNavigationResults()
    }

    private fun observeNavigationResults() {
        // Using StateFlow observation for the navigation result. 
        // This is more robust for Compose Navigation than LiveData.
        savedStateHandle.getStateFlow<String?>("selected_address", null)
            .onEach { address ->
                if (!address.isNullOrBlank()) {
                    location = address
                    // Clear the result immediately so it doesn't re-apply
                    savedStateHandle["selected_address"] = null
                    _eventFlow.emit(UiEvent.ShowSnackbar("Location updated from map"))
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Fetches the current GPS location.
     * @param isManual If true, it will overwrite any existing location data and show feedback.
     *                 If false (automatic), it will only set the location if the field is currently blank.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(isManual: Boolean = true) {
        // Automatic check: don't overwrite if data already exists
        if (!isManual && location.isNotBlank()) return

        viewModelScope.launch {
            try {
                if (isManual) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Updating location from GPS..."))
                }

                val locationResult = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                
                if (locationResult != null) {
                    val coords = "${locationResult.latitude}, ${locationResult.longitude}"
                    // Re-check awareness before setting to prevent race conditions
                    if (isManual || location.isBlank()) {
                        location = coords
                        if (isManual) {
                            _eventFlow.emit(UiEvent.ShowSnackbar("Location updated"))
                        }
                    }
                } else if (isManual) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Could not acquire GPS location"))
                }
            } catch (e: Exception) {
                if (isManual) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("GPS Error: ${e.message}"))
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

    fun onSaveClick() {
        viewModelScope.launch {
            try {
                if (name.isBlank() || quantity.isBlank() || location.isBlank()) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Please fill in required fields"))
                    return@launch
                }

                // Convert list to map, filtering out empty keys
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
