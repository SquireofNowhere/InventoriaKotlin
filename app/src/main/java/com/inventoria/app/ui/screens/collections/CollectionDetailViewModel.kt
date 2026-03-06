package com.inventoria.app.ui.screens.collections

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.*
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionDetailUiState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val matchedItemIds: Set<Long> = emptySet(),
    val expandedItemIds: Set<Long> = emptySet(),
    val allLinks: List<ItemLink> = emptyList(),
    val linkedItemIds: Set<Long> = emptySet(),
    val collectionItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository
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
    
    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private val _packResult = MutableSharedFlow<PackResultDisplay?>()
    val packResult = _packResult.asSharedFlow()
    
    init {
        observeItems()
    }

    private fun observeItems() {
        viewModelScope.launch {
            val itemsWithLocationsFlow = inventoryRepository.getAllItemsWithResolvedLocations()
            val allLinksFlow = inventoryRepository.getAllLinksFlow()

            combine(
                _collectionId.filterNotNull(),
                itemsWithLocationsFlow,
                allLinksFlow,
                settingsRepository.expandedItemIds
            ) { id, allItems, allLinks, expandedIdsStrings ->
                val collection = collectionRepository.getCollectionWithItems(id).first()
                val collectionItemIds = collection?.items?.map { it.id }?.toSet() ?: emptySet()
                
                // For the collection detail view, we strictly show only items that are members of the collection.
                // Heirarchy is preserved only if both parent and child are members.
                val itemsInCollection = allItems.filter { collectionItemIds.contains(it.id) }
                
                val linkedItemIds = allLinks.flatMap { listOf(it.followerId, it.leaderId) }.toSet()
                val expandedIds = expandedIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()

                CollectionDetailUiState(
                    items = allItems,
                    filteredItems = itemsInCollection,
                    matchedItemIds = collectionItemIds,
                    expandedItemIds = expandedIds,
                    allLinks = allLinks,
                    linkedItemIds = linkedItemIds,
                    collectionItemIds = collectionItemIds,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun loadCollection(id: Long) {
        _collectionId.value = id
    }
    
    fun toggleItemExpansion(itemId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.expandedItemIds
            val new = if (current.contains(itemId)) current - itemId else current + itemId
            settingsRepository.saveExpandedItems(new.map { it.toString() }.toSet())
        }
    }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            try {
                val item = inventoryRepository.getItemById(itemId) ?: return@launch
                if (item.equipped) {
                    if (repack && item.lastParentId != null) {
                        inventoryRepository.updateItems(listOf(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null)))
                    } else {
                        inventoryRepository.updateItems(listOf(item.copy(equipped = false, lastParentId = null)))
                    }
                } else {
                    inventoryRepository.updateItems(listOf(item.copy(equipped = true)))
                }
            } catch (e: Exception) {
                Log.e("CollectionDetailVM", "Failed to toggle equip", e)
            }
        }
    }

    fun moveItem(itemId: Long, newParentId: Long?) {
        viewModelScope.launch {
            try {
                val itemToMove = inventoryRepository.getItemById(itemId) ?: return@launch
                if (itemId == newParentId) return@launch
                
                if (newParentId != null) {
                    val target = inventoryRepository.getItemById(newParentId)
                    if (target != null) {
                        if (target.storage) {
                            inventoryRepository.updateItems(listOf(itemToMove.copy(parentId = newParentId, equipped = false, lastParentId = null)))
                        } else {
                            inventoryRepository.addLink(followerId = itemId, leaderId = newParentId)
                        }
                    }
                } else {
                    inventoryRepository.updateItems(listOf(itemToMove.copy(parentId = null)), applyToFollowers = false)
                }
            } catch (e: Exception) {
                Log.e("CollectionDetailVM", "Failed to move item", e)
            }
        }
    }

    fun linkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            inventoryRepository.addLink(followerId, leaderId)
        }
    }

    fun unlinkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            inventoryRepository.removeLink(followerId, leaderId)
        }
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
