package com.inventoria.app.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.*
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    
    private val _collectionId = MutableStateFlow<Long?>(null)
    
    val collectionWithItems: StateFlow<InventoryCollectionWithItems?> = 
        _collectionId.filterNotNull()
            .flatMapLatest { collectionRepository.getCollectionWithItems(it) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    val readiness: StateFlow<InventoryCollectionReadiness?> = 
        _collectionId.filterNotNull()
            .flatMapLatest { id ->
                collectionRepository.getCollectionReadiness(id)
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    val availableContainers: StateFlow<List<InventoryItem>> = 
        inventoryRepository.getStorageItems()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _packResult = MutableSharedFlow<PackResultDisplay?>()
    val packResult = _packResult.asSharedFlow()
    
    fun loadCollection(id: Long) {
        _collectionId.value = id
    }
    
    fun packIntoContainer(containerId: Long) {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                when (val result = collectionRepository.packCollectionIntoContainer(
                    collectionId, containerId
                )) {
                    is PackResult.Success -> {
                        _packResult.emit(PackResultDisplay(result.message, true))
                    }
                    is PackResult.ValidationFailed -> {
                        _packResult.emit(PackResultDisplay(
                            "Cannot pack:\n" + result.errors.joinToString("\n"),
                            false
                        ))
                    }
                    is PackResult.Error -> {
                        _packResult.emit(PackResultDisplay(result.message, false))
                    }
                }
            }
        }
    }
    
    fun unpackCollection() {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                when (val result = collectionRepository.unpackCollection(collectionId)) {
                    is PackResult.Success -> {
                        _packResult.emit(PackResultDisplay(result.message, true))
                    }
                    is PackResult.Error -> {
                        _packResult.emit(PackResultDisplay(result.message, false))
                    }
                    else -> {}
                }
            }
        }
    }
    
    fun equipCollection() {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                when (val result = collectionRepository.equipCollection(collectionId)) {
                    is PackResult.Success -> {
                        _packResult.emit(PackResultDisplay(result.message, true))
                    }
                    is PackResult.Error -> {
                        _packResult.emit(PackResultDisplay(result.message, false))
                    }
                    else -> {}
                }
            }
        }
    }
    
    fun unequipCollection(repack: Boolean = false) {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                when (val result = collectionRepository.unequipCollection(collectionId, repack)) {
                    is PackResult.Success -> {
                        _packResult.emit(PackResultDisplay(result.message, true))
                    }
                    is PackResult.Error -> {
                        _packResult.emit(PackResultDisplay(result.message, false))
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun getContainerName(id: Long): String? {
        return inventoryRepository.getItemById(id)?.name
    }
    
    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                collectionRepository.removeItemFromCollection(collectionId, itemId)
            }
        }
    }
    
    fun deleteCollection() {
        viewModelScope.launch {
            _collectionId.value?.let { collectionId ->
                collectionRepository.deleteCollection(collectionId)
            }
        }
    }
}

data class PackResultDisplay(val message: String, val isSuccess: Boolean)
