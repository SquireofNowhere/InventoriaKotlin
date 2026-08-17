package com.inventoria.app.data

import com.inventoria.app.data.local.TodoDao
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
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

    suspend fun insertTodo(todo: Todo) {
        val timestamp = getNextTimestamp(todo.updatedAt)
        todoDao.insertTodo(todo.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun updateTodo(todo: Todo) {
        val existing = todoDao.getTodoById(todo.id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        todoDao.updateTodo(todo.copy(updatedAt = timestamp, isDirty = true))
    }

    private suspend fun setState(id: String, state: TodoState) {
        val existing = todoDao.getTodoById(id) ?: return
        val timestamp = getNextTimestamp(existing.updatedAt)
        todoDao.updateTodo(
            existing.copy(
                state = state,
                completedAt = if (state == TodoState.COMPLETE) System.currentTimeMillis() else null,
                updatedAt = timestamp,
                isDirty = true
            )
        )
    }

    /** Sets [id] directly to COMPLETE or INCOMPLETE (never IN_PROGRESS -- that's reserved for the
     * cascade below and for a parent's own live-derived "mixed children" display), then walks its
     * whole descendant subtree: completing cascades every currently-INCOMPLETE descendant to
     * IN_PROGRESS (a signal they're implied-covered, not individually verified); un-completing
     * reverts every currently-IN_PROGRESS descendant back to INCOMPLETE. A descendant already at
     * the cascade's target state, or one the user completed/progressed themselves and so sits
     * outside the cascade's `from` state, is left untouched either way -- the walk still recurses
     * through it to reach anything further down. */
    suspend fun setStateWithCascade(id: String, complete: Boolean) {
        setState(id, if (complete) TodoState.COMPLETE else TodoState.INCOMPLETE)
        val cascadeFrom = if (complete) TodoState.INCOMPLETE else TodoState.IN_PROGRESS
        val cascadeTo = if (complete) TodoState.IN_PROGRESS else TodoState.INCOMPLETE

        val all = todoDao.getAllTodosList()
        val childrenByParentId = all.groupBy { it.parentTodoId }
        suspend fun visit(parentId: String) {
            childrenByParentId[parentId]?.forEach { child ->
                if (child.state == cascadeFrom) {
                    setState(child.id, cascadeTo)
                }
                visit(child.id)
            }
        }
        visit(id)
    }

    suspend fun softDeleteTodo(id: String) {
        val existing = todoDao.getTodoById(id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        todoDao.softDeleteTodoById(id, timestamp)
    }

    /** Puts a soft-deleted todo back. The timestamp must beat the tombstone's own, or the delete
     * still wins the last-write-wins merge on the next pull. */
    suspend fun restoreTodo(id: String) {
        val existing = todoDao.getTodoById(id)
        todoDao.restoreTodoById(id, getNextTimestamp(existing?.updatedAt ?: 0L))
    }

    suspend fun purgeOldDeletedTodos(threshold: Long) {
        todoDao.purgeOldDeletedTodos(threshold)
    }
}
