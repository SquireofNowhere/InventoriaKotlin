package com.inventoria.app.data.repository

import android.content.Context
import com.inventoria.app.data.model.InventoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory data operations using a simple text file.
 * Uses a StateFlow to provide real-time updates across the app.
 */
@Singleton
class InventoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fileName = "inventory.txt"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _itemsFlow = MutableStateFlow<List<InventoryItem>>(emptyList())
    val itemsFlow: StateFlow<List<InventoryItem>> = _itemsFlow.asStateFlow()

    init {
        // Initial load
        repositoryScope.launch {
            _itemsFlow.value = readItemsFromFile()
        }
    }

    private suspend fun readItemsFromFile(): List<InventoryItem> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            return@withContext emptyList()
        }
        try {
            file.readLines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    try {
                        InventoryItem(
                            id = parts[0].toLong(),
                            name = parts[1],
                            quantity = parts[2].toInt(),
                            location = parts[3],
                            price = parts.getOrNull(4)?.toDoubleOrNull(),
                            category = parts.getOrNull(5),
                            description = parts.getOrNull(6),
                            minimumQuantity = parts.getOrNull(7)?.toIntOrNull()
                        )
                    } catch (e: Exception) {
                        null 
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun writeItemsToFile(items: List<InventoryItem>) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        file.bufferedWriter().use { writer ->
            items.forEach { item ->
                val line = listOf(
                    item.id,
                    item.name,
                    item.quantity,
                    item.location,
                    item.price ?: "",
                    item.category ?: "",
                    item.description ?: "",
                    item.minimumQuantity ?: ""
                ).joinToString("|")
                writer.write(line)
                writer.newLine()
            }
        }
        // Update the flow so all UI observers get the new data immediately
        _itemsFlow.value = items
    }

    // Queries (Now using the observable StateFlow)
    fun getAllItems(): Flow<List<InventoryItem>> = itemsFlow
    
    suspend fun getItemById(id: Long): InventoryItem? {
        return _itemsFlow.value.find { it.id == id }
    }
    
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = itemsFlow.map { items ->
        items.find { it.id == id }
    }
    
    fun searchItems(query: String): Flow<List<InventoryItem>> = itemsFlow.map { items ->
        if (query.isBlank()) items 
        else items.filter { it.name.contains(query, ignoreCase = true) || it.category?.contains(query, ignoreCase = true) == true }
    }
    
    fun getItemsByCategory(category: String): Flow<List<InventoryItem>> = itemsFlow.map { items ->
        items.filter { it.category == category }
    }
    
    fun getLowStockItems(): Flow<List<InventoryItem>> = itemsFlow.map { items ->
        items.filter { it.quantity <= (it.minimumQuantity ?: 0) }
    }
    
    fun getOutOfStockItems(): Flow<List<InventoryItem>> = itemsFlow.map { items ->
        items.filter { it.quantity <= 0 }
    }
    
    fun getAllCategories(): Flow<List<String>> = itemsFlow.map { items ->
        items.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct()
    }
    
    fun getItemCount(): Flow<Int> = itemsFlow.map { it.size }
    
    fun getTotalValue(): Flow<Double?> = itemsFlow.map { items ->
        items.sumOf { (it.price ?: 0.0) * it.quantity }
    }
    
    // Mutations
    suspend fun insertItem(item: InventoryItem): Long {
        val items = _itemsFlow.value.toMutableList()
        val newItem = item.copy(
            id = (items.maxOfOrNull { it.id } ?: 0L) + 1,
            createdAt = Date(),
            updatedAt = Date()
        )
        items.add(newItem)
        writeItemsToFile(items)
        return newItem.id
    }
    
    suspend fun updateItem(item: InventoryItem) {
        val items = _itemsFlow.value.toMutableList()
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item.copy(updatedAt = Date())
            writeItemsToFile(items)
        }
    }
    
    suspend fun deleteItemById(id: Long) {
        val items = _itemsFlow.value.toMutableList()
        if (items.removeAll { it.id == id }) {
            writeItemsToFile(items)
        }
    }

    suspend fun updateQuantity(id: Long, newQuantity: Int) {
        val items = _itemsFlow.value.toMutableList()
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            items[index] = items[index].copy(quantity = newQuantity, updatedAt = Date())
            writeItemsToFile(items)
        }
    }
}
