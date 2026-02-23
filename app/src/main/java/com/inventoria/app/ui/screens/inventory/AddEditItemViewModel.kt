package com.inventoria.app.ui.screens.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomField(
    val key: String,
    val value: String
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle
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

    init {
        // Safely retrieve itemId which might be stored as a String by Compose Navigation
        val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
            ?: savedStateHandle.get<Long>("itemId")

        itemId?.let { id ->
            if (id != -1L && id != 0L) {
                viewModelScope.launch {
                    repository.getItemById(id)?.let { item ->
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
