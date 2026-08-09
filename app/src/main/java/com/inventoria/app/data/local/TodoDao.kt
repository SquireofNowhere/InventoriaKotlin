package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM Todo WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getVisibleTodos(): Flow<List<Todo>>

    @Query("SELECT * FROM Todo WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllTodosList(): List<Todo>

    @Query("SELECT * FROM Todo")
    suspend fun getAllTodosForSyncList(): List<Todo>

    @Query("SELECT * FROM Todo WHERE isDirty = 1")
    fun getDirtyTodosFlow(): Flow<List<Todo>>

    @Query("SELECT * FROM Todo WHERE isDirty = 1")
    suspend fun getDirtyTodosList(): List<Todo>

    @Query("UPDATE Todo SET isDirty = 0 WHERE id IN (:todoIds)")
    suspend fun markTodosClean(todoIds: List<String>)

    @Query("SELECT * FROM Todo WHERE id = :id LIMIT 1")
    suspend fun getTodoById(id: String): Todo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: Todo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodos(todos: List<Todo>)

    @Update
    suspend fun updateTodo(todo: Todo)

    @Query("UPDATE Todo SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteTodoById(id: String, timestamp: Long)

    @Query("DELETE FROM Todo WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedTodos(threshold: Long)
}
