package com.inventoria.app.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollectionType
import com.inventoria.app.data.model.InventoryCollectionWithCount
import com.inventoria.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<InventoryCollectionType?>(null)
    val filterType: StateFlow<InventoryCollectionType?> = _filterType.asStateFlow()

    private val _selectedCollectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCollectionIds: StateFlow<Set<Long>> = _selectedCollectionIds.asStateFlow()

    val collections: StateFlow<List<InventoryCollectionWithCount>> = combine(
        _searchQuery,
        _filterType
    ) { query, type ->
        query to type
    }.flatMapLatest { (query, type) ->
        collectionRepository.getCollectionsWithCounts().map { list ->
            list.filter { item ->
                val matchesQuery = item.collection.name.contains(query, ignoreCase = true) ||
                        item.collection.description?.contains(query, ignoreCase = true) == true
                val matchesType = type == null || item.collection.collectionType == type
                matchesQuery && matchesType
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(type: InventoryCollectionType?) {
        _filterType.value = type
    }

    fun toggleSelection(collectionId: Long) {
        val current = _selectedCollectionIds.value
        _selectedCollectionIds.value = if (collectionId in current) current - collectionId else current + collectionId
    }

    fun clearSelection() {
        _selectedCollectionIds.value = emptySet()
    }

    fun selectAll() {
        val visibleIds = collections.value.map { it.collection.id }.toSet()
        _selectedCollectionIds.value = if (visibleIds.isNotEmpty() && visibleIds == _selectedCollectionIds.value) {
            emptySet()
        } else {
            visibleIds
        }
    }

    fun deleteSelectedCollections() {
        viewModelScope.launch {
            val idsToDelete = _selectedCollectionIds.value
            _selectedCollectionIds.value = emptySet()
            // Each delete's local removal is instant, but it also awaits a Firebase network
            // round-trip (see ErrorLog.md #27). A sequential forEach would gate every
            // subsequent item's instant local delete behind the previous item's network call,
            // making a multi-select delete look stalled/laggy -- run them concurrently instead.
            idsToDelete.map { id -> async { collectionRepository.deleteCollection(id) } }.awaitAll()
        }
    }

    fun quickEquipCollection(collectionId: Long) {
        viewModelScope.launch {
            // Logic ported from decompiled code would go here
            // collectionRepository.equipCollection(collectionId)
        }
    }

    fun quickPackCollection(collectionId: Long) {
        viewModelScope.launch {
            // Logic ported from decompiled code would go here
        }
    }
}
