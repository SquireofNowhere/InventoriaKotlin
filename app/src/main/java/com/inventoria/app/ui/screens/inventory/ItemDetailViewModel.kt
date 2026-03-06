package com.inventoria.app.ui.screens.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollection
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: InventoryItem? = null,
    val parentItem: InventoryItem? = null,
    val lastParentName: String? = null,
    val collections: List<InventoryCollection> = emptyList(),
    val availableContainers: List<InventoryItem> = emptyList(),
    val links: List<ItemLink> = emptyList(),
    val linkNames: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val itemId: Long = run {
        val rawId = savedStateHandle.get<Any>("itemId")
        when (rawId) {
            is Long -> rawId
            is String -> rawId.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    init {
        if (itemId != 0L) {
            loadItem()
            loadCollections()
            loadAvailableContainers()
            loadLinks()
            viewModelScope.launch {
                repository.touchItem(itemId)
            }
        } else {
            _uiState.value = ItemDetailUiState(isLoading = false, error = "Invalid Item ID")
        }
    }

    private fun loadItem() {
        viewModelScope.launch {
            repository.getItemByIdFlow(itemId).collect { item ->
                if (item != null) {
                    var parentItem: InventoryItem? = null
                    val pId = item.parentId
                    if (pId != null) {
                        parentItem = repository.getItemById(pId)
                    }
                    
                    var lastParentName: String? = null
                    val lpId = item.lastParentId
                    if (lpId != null) {
                        lastParentName = repository.getItemById(lpId)?.name
                    }
                    
                    _uiState.update { it.copy(
                        item = item, 
                        parentItem = parentItem,
                        lastParentName = lastParentName,
                        isLoading = false
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Item not found") }
                }
            }
        }
    }

    private fun loadCollections() {
        viewModelScope.launch {
            collectionRepository.getCollectionsForItem(itemId).collect { collections ->
                _uiState.update { it.copy(collections = collections) }
            }
        }
    }

    private fun loadAvailableContainers() {
        viewModelScope.launch {
            repository.getAllItems().collect { allItems ->
                val childrenIds = mutableSetOf<Long>()
                val visited = mutableSetOf<Long>()
                fun findChildren(parentId: Long) {
                    if (!visited.add(parentId)) return
                    allItems.filter { it.parentId == parentId }.forEach {
                        childrenIds.add(it.id)
                        findChildren(it.id)
                    }
                }
                findChildren(itemId)

                val validTargets = allItems.filter { it.id != itemId && !childrenIds.contains(it.id) }
                _uiState.update { it.copy(availableContainers = validTargets) }
            }
        }
    }

    private fun loadLinks() {
        viewModelScope.launch {
            repository.getLinksForItemFlow(itemId).collect { links ->
                val names = mutableMapOf<Long, String>()
                links.forEach { link ->
                    val otherId = if (link.followerId == itemId) link.leaderId else link.followerId
                    repository.getItemById(otherId)?.let { names[otherId] = it.name }
                }
                _uiState.update { it.copy(links = links, linkNames = names) }
            }
        }
    }

    fun removeLink(leaderId: Long) {
        viewModelScope.launch {
            repository.removeLink(followerId = itemId, leaderId = leaderId)
        }
    }

    fun moveToContainer(containerId: Long?) {
        viewModelScope.launch {
            val currentItem = _uiState.value.item ?: return@launch
            val updatedItem = currentItem.copy(
                parentId = containerId,
                equipped = if (containerId != null) false else currentItem.equipped,
                lastParentId = null, // Clear repack history if manually moved
                updatedAt = System.currentTimeMillis()
            )
            repository.updateItem(updatedItem)
            
            if (containerId != null) {
                repository.getItemById(containerId)?.let { target ->
                    if (!target.storage) {
                        repository.updateItem(target.copy(storage = true, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    fun toggleEquip(repack: Boolean = false) {
        viewModelScope.launch {
            val item = _uiState.value.item ?: return@launch
            if (item.equipped) {
                // Unequipping
                if (repack && item.lastParentId != null) {
                    repository.updateItem(item.copy(equipped = false, parentId = item.lastParentId, lastParentId = null))
                } else {
                    repository.updateItem(item.copy(equipped = false, lastParentId = null))
                }
            } else {
                // Equipping
                repository.updateItem(item.copy(equipped = true))
            }
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (itemId != 0L) {
                repository.deleteItemById(itemId)
            }
            onDeleted()
        }
    }
}
