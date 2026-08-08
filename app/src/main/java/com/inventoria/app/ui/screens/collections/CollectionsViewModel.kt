package com.inventoria.app.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollectionType
import com.inventoria.app.data.model.InventoryCollectionWithCount
import com.inventoria.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    init {
        startPeriodicCleanup()
    }

    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (isActive) {
                collectionRepository.purgeOldDeletedCollections(System.currentTimeMillis() - 86_400_000)
                delay(60_000)
            }
        }
    }

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
            // Soft-delete is a single local UPDATE per id with no network dependency (sync rides
            // the normal dirty-flow push independently), so a plain sequential loop is fine here.
            idsToDelete.forEach { collectionRepository.deleteCollection(it) }
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
