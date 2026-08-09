package com.inventoria.app.data

import com.inventoria.app.data.local.TodoDao
import com.inventoria.app.data.model.Todo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            val todos = todoDao.getVisibleTodos().first()
            val maxT = todos.maxOfOrNull { it.updatedAt } ?: 0L
            lastTimestamp.set(maxT)
        }
    }

    private fun getNextTimestamp(minTimestamp: Long = 0L): Long {
        val now = System.currentTimeMillis()
        val base = maxOf(now, minTimestamp)
        while (true) {
            val last = lastTimestamp.get()
            val next = if (base > last) base else last + 1
            if (lastTimestamp.compareAndSet(last, next)) {
                return next
            }
        }
    }

    fun getVisibleTodos(): Flow<List<Todo>> = todoDao.getVisibleTodos()

    suspend fun getTodoById(id: String): Todo? = todoDao.getTodoById(id)

    suspend fun getCompletedInRange(dayStart: Long, dayEnd: Long): List<Todo> =
        todoDao.getCompletedInRange(dayStart, dayEnd)

    suspend fun insertTodo(todo: Todo) {
        val timestamp = getNextTimestamp(todo.updatedAt)
        todoDao.insertTodo(todo.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun updateTodo(todo: Todo) {
        val existing = todoDao.getTodoById(todo.id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        todoDao.updateTodo(todo.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun setCompleted(id: String, completed: Boolean) {
        val existing = todoDao.getTodoById(id) ?: return
        val timestamp = getNextTimestamp(existing.updatedAt)
        todoDao.updateTodo(
            existing.copy(
                isCompleted = completed,
                completedAt = if (completed) System.currentTimeMillis() else null,
                updatedAt = timestamp,
                isDirty = true
            )
        )
    }

    suspend fun setActiveSessionGroupId(id: String, groupId: String?) {
        val existing = todoDao.getTodoById(id) ?: return
        val timestamp = getNextTimestamp(existing.updatedAt)
        todoDao.updateTodo(existing.copy(activeSessionGroupId = groupId, updatedAt = timestamp, isDirty = true))
    }

    suspend fun softDeleteTodo(id: String) {
        val existing = todoDao.getTodoById(id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        todoDao.softDeleteTodoById(id, timestamp)
    }

    suspend fun purgeOldDeletedTodos(threshold: Long) {
        todoDao.purgeOldDeletedTodos(threshold)
    }
}
