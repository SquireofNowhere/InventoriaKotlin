package com.inventoria.app.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollectionReadiness
import com.inventoria.app.data.model.InventoryCollectionWithItems
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.PackResult
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.InventoryRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PackResultDisplay(
    val message: String,
    val isSuccess: Boolean
)

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _collectionId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private val _packResult = MutableSharedFlow<PackResultDisplay>()
    val packResult: SharedFlow<PackResultDisplay> = _packResult.asSharedFlow()

    val collectionWithItems: StateFlow<InventoryCollectionWithItems?> = _collectionId
        .filterNotNull()
        .flatMapLatest { id -> collectionRepository.getCollectionWithItems(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val readiness: StateFlow<InventoryCollectionReadiness?> = _collectionId
        .filterNotNull()
        .flatMapLatest { id -> collectionRepository.getCollectionReadiness(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val availableContainers: StateFlow<List<InventoryItem>> = inventoryRepository.getStorageItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeItems()
    }

    private fun observeItems() {
        // getItemsForCollection(id) must be subscribed to as its own live flow (via flatMapLatest)
        // rather than snapshotted with .first() inside the combine lambda below -- a one-time
        // .first() read never re-runs when the collection's items themselves change, only when
        // one of the *other* combined flows happens to emit, so adding/removing items wouldn't
        // show up here until something unrelated (e.g. leaving and re-entering the screen,
        // which recreates the ViewModel) forced a fresh read. See TECHNICAL_AUDIT.md #6.
        val collectionItemsFlow = _collectionId.filterNotNull().flatMapLatest { id ->
            collectionRepository.getItemsForCollection(id)
        }

        combine(
            _collectionId.filterNotNull(),
            inventoryRepository.getAllItems(),
            inventoryRepository.getAllLinksFlow(),
            settingsRepository.getExpandedItemIds(),
            collectionItemsFlow
        ) { id, allItems, allLinks, expandedIdsStrings, collectionItems ->
            val expandedItemIds = expandedIdsStrings.mapNotNull { it.toLongOrNull() }.toSet()
            val collectionItemIds = collectionItems.map { it.itemId }.toSet()

            val resolvedItems = inventoryRepository.resolveLocations(allItems, allLinks)
            val filteredItems = resolvedItems.filter { it.id in collectionItemIds }

            CollectionDetailUiState(
                items = allItems,
                filteredItems = filteredItems,
                matchedItemIds = collectionItemIds,
                expandedItemIds = expandedItemIds,
                allLinks = allLinks,
                linkedItemIds = allLinks.map { it.followerId }.toSet(),
                collectionItemIds = collectionItemIds,
                isLoading = false
            )
        }.onEach { newState ->
            _uiState.value = newState
        }.launchIn(viewModelScope)
    }

    fun loadCollection(id: Long) {
        _collectionId.value = id
    }

    fun toggleItemExpansion(itemId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.expandedItemIds
            val next = if (itemId in current) current - itemId else current + itemId
            settingsRepository.saveExpandedItems(next.map { it.toString() }.toSet())
        }
    }

    fun toggleEquip(itemId: Long, repack: Boolean = false) {
        viewModelScope.launch {
            inventoryRepository.updateItemEquippedStatus(itemId, !inventoryRepository.getItemById(itemId)?.equipped!!)
        }
    }

    fun moveItem(itemId: Long, newParentId: Long?) {
        viewModelScope.launch {
            inventoryRepository.moveItem(itemId, newParentId)
        }
    }

    fun linkItem(followerId: Long, leaderId: Long) {
        viewModelScope.launch {
            inventoryRepository.addLink(followerId, leaderId)
        }
    }

    fun packIntoContainer(containerId: Long) {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            val result = collectionRepository.packCollectionIntoContainer(id, containerId)
            handlePackResult(result)
        }
    }

    fun unpackCollection() {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            val result = collectionRepository.unpackCollection(id)
            handlePackResult(result)
        }
    }

    fun equipCollection() {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            val result = collectionRepository.equipCollection(id)
            handlePackResult(result)
        }
    }

    fun unequipCollection(repack: Boolean = false) {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            val result = collectionRepository.unequipCollection(id, repack)
            handlePackResult(result)
        }
    }

    private suspend fun handlePackResult(result: PackResult) {
        val display = when (result) {
            is PackResult.Success -> PackResultDisplay(result.message, true)
            is PackResult.Partial -> PackResultDisplay("${result.message}: ${result.errors.joinToString()}", true)
            is PackResult.Error -> PackResultDisplay(result.message, false)
        }
        _packResult.emit(display)
    }

    suspend fun getContainerName(id: Long): String? {
        return inventoryRepository.getItemById(id)?.name
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            collectionRepository.removeItemFromCollection(id, itemId)
        }
    }

    fun deleteCollection(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val id = _collectionId.value ?: return@launch
            collectionRepository.deleteCollection(id)
            onDeleted()
        }
    }
}
