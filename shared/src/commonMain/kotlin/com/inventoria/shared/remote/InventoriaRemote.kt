package com.inventoria.shared.remote

import com.inventoria.shared.model.InventoryCollection
import com.inventoria.shared.model.InventoryCollectionItem
import com.inventoria.shared.model.InventoryItem
import com.inventoria.shared.model.ItemLink
import com.inventoria.shared.model.ScheduleBlock
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskType
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.nowMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Everything under users/$uid that the app shows, soft-deleted rows already dropped. */
data class InventoriaSnapshot(
    val items: List<InventoryItem> = emptyList(),
    val itemLinks: List<ItemLink> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val collections: List<InventoryCollection> = emptyList(),
    val collectionItems: List<InventoryCollectionItem> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val taskTypes: List<TaskType> = emptyList(),
    val scheduleBlocks: List<ScheduleBlock> = emptyList(),
    val loadedAt: Long = 0L
)

/**
 * Reads and writes one account's node directly in the cloud. Unlike the Android app there is no
 * local database in between: the database is the source of truth and every write lands there.
 *
 * Writes follow the same convention the Android sync relies on -- bump `updatedAt` on every
 * change -- so a phone merging the row sees the web's edit as the newer one.
 */
class InventoriaRemote(private val db: RealtimeDatabaseRest) {

    suspend fun loadSnapshot(uid: String): InventoriaSnapshot = coroutineScope {
        val root = "users/$uid"
        val items = async { db.getChildren("$root/items", InventoryItem.serializer()) }
        val links = async { db.getChildren("$root/item_links", ItemLink.serializer()) }
        val tasks = async { db.getChildren("$root/tasks", Task.serializer()) }
        val collections = async { db.getChildren("$root/collections", InventoryCollection.serializer()) }
        val collectionItems = async { db.getChildren("$root/collection_items", InventoryCollectionItem.serializer()) }
        val todos = async { db.getChildren("$root/todos", Todo.serializer()) }
        val types = async { db.getChildren("$root/task_types", TaskType.serializer()) }
        val blocks = async { db.getChildren("$root/schedule_blocks", ScheduleBlock.serializer()) }
        InventoriaSnapshot(
            items = items.await().filterNot { it.isDeleted },
            itemLinks = links.await().filterNot { it.isDeleted },
            tasks = tasks.await().filterNot { it.isDeleted },
            collections = collections.await().filterNot { it.isDeleted },
            collectionItems = collectionItems.await().filterNot { it.isDeleted },
            todos = todos.await().filterNot { it.isDeleted },
            taskTypes = types.await().filterNot { it.isDeleted },
            scheduleBlocks = blocks.await().filterNot { it.isDeleted },
            loadedAt = nowMillis()
        )
    }

    /** Checks a todo off (or back on). Returns the row as written. */
    suspend fun setTodoState(uid: String, todo: Todo, state: TodoState): Todo {
        val now = nowMillis()
        val updated = todo.copy(
            state = state,
            completedAt = if (state == TodoState.COMPLETE) now else null,
            updatedAt = now
        )
        db.patch("users/$uid/todos/${todo.id}", buildJsonObject {
            put("state", JsonPrimitive(updated.state.name))
            put("completedAt", updated.completedAt?.let { JsonPrimitive(it) } ?: JsonNull)
            put("updatedAt", JsonPrimitive(updated.updatedAt))
        })
        return updated
    }
}
