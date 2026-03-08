package com.inventoria.app.ui.screens.collections

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryCollectionType
import com.inventoria.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditCollectionViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var id by mutableStateOf<Long?>(null)
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var icon by mutableStateOf("📦")
    var color by mutableStateOf(-14575885) // Default color
    var tags by mutableStateOf("")
    var collectionType by mutableStateOf(InventoryCollectionType.OTHER)
    var requiresSameLocation by mutableStateOf(false)

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        val collectionId = savedStateHandle.get<Long>("id")
        if (collectionId != null && collectionId != 0L) {
            loadCollection(collectionId)
        }
    }

    private fun loadCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.getCollectionWithItems(collectionId).collect { data ->
                data?.collection?.let { collection ->
                    id = collection.id
                    name = collection.name
                    description = collection.description ?: ""
                    icon = collection.icon ?: "📦"
                    color = collection.color
                    tags = collection.tags.joinToString(", ")
                    collectionType = collection.collectionType
                    requiresSameLocation = collection.requiresSameLocation
                }
            }
        }
    }

    fun onSave() {
        viewModelScope.launch {
            if (name.isBlank()) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Name is required"))
                return@launch
            }

            val collection = InventoryCollection(
                id = id ?: 0L,
                name = name,
                description = description.takeIf { it.isNotBlank() },
                icon = icon,
                color = color,
                tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                collectionType = collectionType,
                requiresSameLocation = requiresSameLocation,
                updatedAt = System.currentTimeMillis()
            )

            try {
                if (id == null || id == 0L) {
                    collectionRepository.createCollection(collection)
                } else {
                    collectionRepository.updateCollection(collection)
                }
                _eventFlow.emit(UiEvent.SaveSuccess)
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Error saving collection: ${e.message}"))
            }
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object SaveSuccess : UiEvent()
    }
}
