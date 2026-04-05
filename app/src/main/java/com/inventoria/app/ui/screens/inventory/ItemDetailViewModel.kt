package com.inventoria.app.ui.screens.inventory

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.ItemLink
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val itemId: Long = savedStateHandle.get<Long>("itemId") 
        ?: savedStateHandle.get<String>("itemId")?.toLongOrNull() 
        ?: 0L

    init {
        if (itemId != 0L) {
            loadItem()
            loadChildren()
            loadCollections()
            loadAvailableContainers()
            loadLinks()
            
            viewModelScope.launch {
                repository.touchItem(itemId)
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid Item ID")
        }
    }

    private fun loadChildren() {
        viewModelScope.launch {
            repository.getItemsByParent(itemId).collect { items ->
                _uiState.update { it.copy(children = items) }
            }
        }
    }

    private fun loadItem() {
        viewModelScope.launch {
            combine(
                repository.getItemByIdFlow(itemId),
                repository.getAllItems(),
                repository.getAllLinksFlow()
            ) { item, allItems, links ->
                if (item == null) return@combine null
                
                // We need all items to resolve hierarchy
                val resolvedItems = repository.resolveLocations(allItems, links)
                resolvedItems.find { it.id == itemId }
            }.collect { item ->
                if (item != null) {
                    val parentItem = item.parentId?.let { repository.getItemById(it) }
                    val lastParentName = item.lastParentId?.let { repository.getItemById(it)?.name }
                    
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
                findChildren(itemId, allItems, childrenIds)
                
                val validTargets = allItems.filter { 
                    it.id != itemId && !childrenIds.contains(it.id) 
                }
                _uiState.update { it.copy(availableContainers = validTargets) }
            }
        }
    }

    private fun findChildren(parentId: Long, allItems: List<InventoryItem>, childrenIds: MutableSet<Long>) {
        val directChildren = allItems.filter { it.parentId == parentId }
        directChildren.forEach { child ->
            if (childrenIds.add(child.id)) {
                findChildren(child.id, allItems, childrenIds)
            }
        }
    }

    private fun loadLinks() {
        viewModelScope.launch {
            repository.getLinksForItemFlow(itemId).collect { links ->
                val itemIds = links.flatMap { listOf(it.leaderId, it.followerId) }.distinct()
                val names = mutableMapOf<Long, String>()
                itemIds.forEach { id ->
                    repository.getItemById(id)?.let { names[id] = it.name }
                }
                _uiState.update { it.copy(links = links, linkNames = names) }
            }
        }
    }

    fun toggleEquip(repack: Boolean = false) {
        viewModelScope.launch {
            val currentItem = _uiState.value.item ?: return@launch
            repository.setItemEquipped(itemId, !currentItem.equipped, repack)
        }
    }

    fun moveToContainer(containerId: Long?) {
        viewModelScope.launch {
            repository.moveItem(itemId, containerId)
        }
    }

    fun removeLink(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            repository.removeLink(followerId, leaderId)
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

    fun setProfilePicture(url: String) {
        viewModelScope.launch {
            val item = _uiState.value.item ?: return@launch
            repository.updateItem(item.copy(profilePictureUrl = url))
        }
    }

    fun removeImage(url: String) {
        viewModelScope.launch {
            val item = _uiState.value.item ?: return@launch
            val newList = item.imageUrls.filter { it != url }
            val newProfile = if (item.profilePictureUrl == url) newList.firstOrNull() else item.profilePictureUrl
            repository.updateItem(item.copy(imageUrls = newList, profilePictureUrl = newProfile))
        }
    }
}
