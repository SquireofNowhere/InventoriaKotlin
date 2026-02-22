
package com.inventoria.app.ui.screens.inventory

import androidx.compose.runtime.getValue
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
    var minQuantity by mutableStateOf("")

    private var currentItemId: Long? = null

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        savedStateHandle.get<Long>("itemId")?.let { itemId ->
            if (itemId != -1L) {
                viewModelScope.launch {
                    repository.getItemById(itemId)?.let { item ->
                        currentItemId = item.id
                        name = item.name
                        quantity = item.quantity.toString()
                        location = item.location
                        price = item.price?.toString() ?: ""
                        category = item.category ?: ""
                        description = item.description ?: ""
                        minQuantity = item.minimumQuantity?.toString() ?: ""
                    }
                }
            }
        }
    }

    fun onSaveClick() {
        viewModelScope.launch {
            try {
                if (name.isBlank() || quantity.isBlank() || location.isBlank()) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Please fill in required fields"))
                    return@launch
                }

                val item = InventoryItem(
                    id = currentItemId ?: 0L,
                    name = name,
                    quantity = quantity.toIntOrNull() ?: 0,
                    location = location,
                    price = price.toDoubleOrNull(),
                    category = category.ifBlank { null },
                    description = description.ifBlank { null },
                    minimumQuantity = minQuantity.toIntOrNull()
                )

                if (currentItemId == null) {
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
