package com.inventoria.app.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.model.InventoryCollectionType
import com.inventoria.app.data.model.InventoryCollectionWithCount
import com.inventoria.app.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private val _filterType = MutableStateFlow<InventoryCollectionType?>(null)
    val filterType = _filterType.asStateFlow()
    
    val collections: StateFlow<List<InventoryCollectionWithCount>> = 
        combine(searchQuery, filterType) { query, type ->
            Pair(query, type)
        }.flatMapLatest { (query, type) ->
            when {
                query.isNotEmpty() -> collectionRepository.searchCollections(query)
                    .map { it.map { c -> InventoryCollectionWithCount(c, 0) } }
                type != null -> collectionRepository.getCollectionsByType(type)
                    .map { it.map { c -> InventoryCollectionWithCount(c, 0) } }
                else -> collectionRepository.getCollectionsWithCounts()
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setFilter(type: InventoryCollectionType?) {
        _filterType.value = type
    }
    
    fun quickPackCollection(collectionId: Long) {
        viewModelScope.launch {
            // TODO: Show container selection dialog
        }
    }
    
    fun quickEquipCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.equipCollection(collectionId)
        }
    }
}
