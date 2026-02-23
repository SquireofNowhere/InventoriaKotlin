package com.inventoria.app.data.repository

import android.content.Context
import com.inventoria.app.data.model.InventoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileInventoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fileName = "inventory.csv"

    private suspend fun readItems(): List<InventoryItem> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            return@withContext emptyList()
        }
        file.readLines().mapNotNull { line ->
            val parts = line.split(";")
            if (parts.size >= 4) {
                try {
                    InventoryItem(
                        id = parts[0].toLong(),
                        name = parts[1],
                        quantity = parts[2].toInt(),
                        location = parts[3],
                        price = parts.getOrNull(4)?.toDoubleOrNull(),
                        category = parts.getOrNull(5),
                        description = parts.getOrNull(6)
                    )
                } catch (e: Exception) {
                    null 
                }
            } else {
                null
            }
        }
    }

    private suspend fun writeItems(items: List<InventoryItem>) = withContext(Dispatchers.IO) {
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
                    item.description ?: ""
                ).joinToString(";")
                writer.write(line)
                writer.newLine()
            }
        }
    }

    fun getAllItems(): Flow<List<InventoryItem>> = flow {
        emit(readItems())
    }.flowOn(Dispatchers.IO)

    fun getItemCount(): Flow<Int> = getAllItems().map { it.size }

    fun getTotalValue(): Flow<Double?> = getAllItems().map { items ->
        items.sumOf { (it.price ?: 0.0) * it.quantity }
    }

    fun getOutOfStockItems(): Flow<List<InventoryItem>> = getAllItems().map { items ->
        items.filter { it.quantity <= 0 }
    }

    fun getAllCategories(): Flow<List<String>> = getAllItems().map { items ->
        items.mapNotNull { it.category }.distinct()
    }

    suspend fun insertItem(item: InventoryItem): Long {
        val items = readItems().toMutableList()
        val newItem = item.copy(
            id = (items.maxOfOrNull { it.id } ?: 0L) + 1, 
            createdAt = Date(), 
            updatedAt = Date()
        )
        items.add(newItem)
        writeItems(items)
        return newItem.id
    }
    
    suspend fun getItemById(id: Long): InventoryItem? {
        return readItems().find { it.id == id }
    }

    suspend fun updateItem(item: InventoryItem) {
        val items = readItems().toMutableList()
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item.copy(updatedAt = Date())
            writeItems(items)
        }
    }

    suspend fun deleteItemById(id: Long) {
        val items = readItems().toMutableList()
        items.removeAll { it.id == id }
        writeItems(items)
    }

    fun searchItems(query: String): Flow<List<InventoryItem>> = getAllItems().map { items ->
        items.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description?.contains(query, ignoreCase = true) == true
        }
    }
}
