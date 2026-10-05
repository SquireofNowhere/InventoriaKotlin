package com.inventoria.mcp

import com.inventoria.shared.model.InventoryCollection
import com.inventoria.shared.model.InventoryCollectionItem
import com.inventoria.shared.model.InventoryItem
import com.inventoria.shared.model.ItemLink
import com.inventoria.shared.model.ScheduleBlock
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.TaskType
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.computeTaskTypeStats
import com.inventoria.shared.model.hasReminder
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.model.reminders
import com.inventoria.shared.remote.InventoriaJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZonedDateTime

private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 500

val KIND_LEGEND: String = TaskKind.entries.joinToString("; ") {
    "${it.name} (${if (it.productivityValue > 0) "+" else ""}${it.productivityValue}, ${it.category.name.lowercase()})"
}

// --- Row access shared by the read and write tools ---------------------------------------------

suspend fun Vault.items(): List<InventoryItem> = rows("items", InventoryItem.serializer()).filterNot { it.isDeleted }
suspend fun Vault.itemLinks(): List<ItemLink> = rows("item_links", ItemLink.serializer()).filterNot { it.isDeleted }
suspend fun Vault.collections(): List<InventoryCollection> =
    rows("collections", InventoryCollection.serializer()).filterNot { it.isDeleted }
suspend fun Vault.collectionItems(): List<InventoryCollectionItem> =
    rows("collection_items", InventoryCollectionItem.serializer()).filterNot { it.isDeleted }
suspend fun Vault.todos(): List<Todo> = rows("todos", Todo.serializer()).filterNot { it.isDeleted }
suspend fun Vault.tasks(): List<Task> = rows("tasks", Task.serializer()).filterNot { it.isDeleted }
suspend fun Vault.taskTypes(): List<TaskType> = rows("task_types", TaskType.serializer()).filterNot { it.isDeleted }
suspend fun Vault.scheduleBlocks(): List<ScheduleBlock> =
    rows("schedule_blocks", ScheduleBlock.serializer()).filterNot { it.isDeleted }

/** A live (not tombstoned) item or a [ToolError] saying why not. */
suspend fun Vault.requireItem(id: Long): InventoryItem {
    val item = row("items", id.toString(), InventoryItem.serializer()) ?: throw ToolError("No item with id $id")
    if (item.isDeleted) throw ToolError("Item $id is deleted; use restore to bring it back first")
    return item
}

suspend fun Vault.requireCollection(id: Long): InventoryCollection {
    val c = row("collections", id.toString(), InventoryCollection.serializer())
        ?: throw ToolError("No collection with id $id")
    if (c.isDeleted) throw ToolError("Collection $id is deleted; use restore to bring it back first")
    return c
}

suspend fun Vault.requireTodo(id: String): Todo {
    val t = row("todos", id, Todo.serializer()) ?: throw ToolError("No todo with id '$id'")
    if (t.isDeleted) throw ToolError("Todo '$id' is deleted; use restore to bring it back first")
    return t
}

suspend fun Vault.requireTaskType(id: String): TaskType {
    val t = row("task_types", id, TaskType.serializer()) ?: throw ToolError("No task type with id '$id'")
    if (t.isDeleted) throw ToolError("Task type '$id' is deleted; use restore to bring it back first")
    return t
}

// --- Views: the row as stored, plus the human-readable forms the model would otherwise guess at -

private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
    InventoriaJson.encodeToJsonElement(serializer, value) as JsonObject

fun itemView(item: InventoryItem): JsonObject {
    var view = encode(InventoryItem.serializer(), item)
        .withField("createdAtIso", JsonPrimitive(isoString(item.createdAt)))
        .withField("updatedAtIso", JsonPrimitive(isoString(item.updatedAt)))
    item.getTotalValue()?.let { view = view.withField("totalValue", JsonPrimitive(it)) }
    return view
}

fun todoView(todo: Todo): JsonObject {
    var view = encode(Todo.serializer(), todo)
    todo.deadline?.let { view = view.withField("deadlineDate", JsonPrimitive(dayString(it))) }
    todo.deadlineMinuteOfDay?.let { view = view.withField("deadlineTime", JsonPrimitive(minuteString(it))) }
    todo.completedAt?.let { view = view.withField("completedAtIso", JsonPrimitive(isoString(it))) }
    if (todo.hasReminder) view = view.withField("remindersText", JsonPrimitive(todo.reminders().describe()))
    return view
}

fun taskView(task: Task, now: Long = nowMillis()): JsonObject {
    var view = encode(Task.serializer(), task)
        .withField("startIso", JsonPrimitive(isoString(task.startTime)))
        .withField("durationMinutes", JsonPrimitive(task.elapsedMillis(now) / 60_000))
        .withField("kindName", JsonPrimitive(task.kind.displayName))
    task.endTime?.let { view = view.withField("endIso", JsonPrimitive(isoString(it))) }
    return view
}

fun blockView(block: ScheduleBlock): JsonObject =
    encode(ScheduleBlock.serializer(), block)
        .withField("date", JsonPrimitive(dayString(block.dayStart)))
        .withField("start", JsonPrimitive(minuteString(block.startMinuteOfDay)))
        .withField("end", JsonPrimitive(minuteString(block.endMinuteOfDay)))

fun collectionView(c: InventoryCollection): JsonObject = encode(InventoryCollection.serializer(), c)

/** How ready a collection is, by the app's own rule: an entry counts once the item covers its required quantity. */
fun readiness(entries: List<InventoryCollectionItem>, itemsById: Map<Long, InventoryItem>): JsonObject {
    var available = 0
    var packed = 0
    var equipped = 0
    for (entry in entries) {
        val item = itemsById[entry.itemId]
        if (item != null && item.quantity >= entry.requiredQuantity) {
            available++
            if (item.equipped) equipped++
            if (item.parentId != null) packed++
        }
    }
    val percent = if (entries.isEmpty()) 100.0 else available * 100.0 / entries.size
    return obj(
        "totalEntries" to entries.size,
        "available" to available,
        "packed" to packed,
        "equipped" to equipped,
        "readinessPercent" to Math.round(percent * 10) / 10.0
    )
}

private fun limitOf(args: Args): Int = (args.int("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

private fun page(total: Int, shown: List<JsonElement>, key: String): JsonObject =
    obj("matched" to total, "returned" to shown.size, key to JsonArray(shown))

// --- The tools ----------------------------------------------------------------------------------

fun readTools(vault: Vault): List<Tool> = listOf(
    Tool(
        name = "vault_summary",
        description = "Overview of the whole vault: item, collection, todo, task and schedule counts, total inventory " +
            "value, overdue todos, running tasks, and the time zone dates are read in. Call this first to orient.",
        inputSchema = schema(),
        readOnly = true
    ) {
        val items = vault.items()
        val todos = vault.todos()
        val tasks = vault.tasks()
        val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val open = todos.filter { it.state != TodoState.COMPLETE }
        obj(
            "ownerUid" to vault.ownerUid,
            "timeZone" to zone.id,
            "today" to dayString(today),
            "items" to obj(
                "count" to items.size,
                "totalUnits" to items.sumOf { it.quantity },
                "totalValue" to items.sumOf { it.getTotalValue() ?: 0.0 },
                "containers" to items.count { it.storage },
                "equipped" to items.count { it.equipped },
                "outOfStock" to items.count { it.quantity <= 0 }
            ),
            "collections" to vault.collections().size,
            "todos" to obj(
                "open" to open.size,
                "inProgress" to todos.count { it.state == TodoState.IN_PROGRESS },
                "completed" to todos.count { it.state == TodoState.COMPLETE },
                "overdue" to open.count { (it.deadline ?: Long.MAX_VALUE) < today }
            ),
            "tasks" to obj(
                "total" to tasks.size,
                "running" to tasks.count { it.isRunning },
                "paused" to tasks.count { it.isPaused && !it.isRunning }
            ),
            "taskTypes" to vault.taskTypes().size,
            "scheduleBlocks" to vault.scheduleBlocks().size
        )
    },

    Tool(
        name = "list_items",
        description = "Search and filter inventory items. All filters combine (AND). Items inside a container have " +
            "parent_id set; an item that is on the person has equipped=true. Sorted by name.",
        inputSchema = schema(
                "query" to strP("Text to find in name, description, location, category tags, barcode or SKU"),
                "category" to strP("Only items carrying this exact tag (items hold comma-separated tags in 'category')"),
                "parent_id" to intP("Only items directly inside this container item"),
                "top_level" to boolP("Only items that are not inside a container and not equipped"),
                "containers_only" to boolP("Only items that are containers (storage=true)"),
                "equipped" to boolP("Filter on whether the item is equipped (on person)"),
                "in_stock" to boolP("true = quantity above zero, false = out of stock"),
                "limit" to intP("Maximum rows to return (default $DEFAULT_LIMIT, max $MAX_LIMIT)")
        ),
        readOnly = true
    ) { args ->
        val query = args.str("query")?.trim()?.lowercase()
        val category = args.str("category")?.trim()?.lowercase()
        val parent = args.long("parent_id")
        val matched = vault.items().filter { item ->
            (query == null || listOfNotNull(item.name, item.description, item.location, item.category, item.barcode, item.sku)
                .any { it.lowercase().contains(query) }) &&
                (category == null || item.getParsedTags().any { it.lowercase() == category }) &&
                (parent == null || item.parentId == parent) &&
                (args.bool("top_level") != true || (item.parentId == null && !item.equipped)) &&
                (args.bool("containers_only") != true || item.storage) &&
                (args.bool("equipped") == null || item.equipped == args.bool("equipped")) &&
                (args.bool("in_stock") == null || item.isInStock() == args.bool("in_stock"))
        }.sortedBy { it.name.lowercase() }
        page(matched.size, matched.take(limitOf(args)).map { itemView(it) }, "items")
    },

    Tool(
        name = "get_item",
        description = "One item in full: its row, the chain of containers it sits in, what it contains, the items it " +
            "leads or follows, and the collections it belongs to.",
        inputSchema = schema("id" to intP("Item id"), required = listOf("id")),
        readOnly = true
    ) { args ->
        val id = args.reqLong("id")
        val item = vault.requireItem(id)
        val all = vault.items()
        val byId = all.associateBy { it.id }

        val chain = mutableListOf<JsonElement>()
        var cursor = item.parentId
        val seen = mutableSetOf(item.id)
        while (cursor != null && seen.add(cursor)) {
            val parent = byId[cursor] ?: break
            chain += obj("id" to parent.id, "name" to parent.name)
            cursor = parent.parentId
        }

        val links = vault.itemLinks()
        val memberships = vault.collectionItems().filter { it.itemId == id }
        val collectionsById = vault.collections().associateBy { it.id }

        obj(
            "item" to itemView(item),
            "insideChain" to JsonArray(chain),
            "contains" to JsonArray(all.filter { it.parentId == id }.map { obj("id" to it.id, "name" to it.name, "quantity" to it.quantity) }),
            "follows" to JsonArray(links.filter { it.followerId == id }.mapNotNull { byId[it.leaderId] }.map { obj("id" to it.id, "name" to it.name) }),
            "leads" to JsonArray(links.filter { it.leaderId == id }.mapNotNull { byId[it.followerId] }.map { obj("id" to it.id, "name" to it.name) }),
            "collections" to JsonArray(memberships.mapNotNull { m ->
                collectionsById[m.collectionId]?.let { obj("id" to it.id, "name" to it.name, "requiredQuantity" to m.requiredQuantity) }
            })
        )
    },

    Tool(
        name = "list_collections",
        description = "All collections (named sets of items, e.g. a packing kit) with their readiness.",
        inputSchema = schema(),
        readOnly = true
    ) {
        val itemsById = vault.items().associateBy { it.id }
        val entries = vault.collectionItems().groupBy { it.collectionId }
        val list = vault.collections().sortedBy { it.name.lowercase() }
        page(list.size, list.map { c ->
            collectionView(c).withField("readiness", readiness(entries[c.id].orEmpty(), itemsById))
        }, "collections")
    },

    Tool(
        name = "get_collection",
        description = "One collection with every entry (item name, required quantity, how many are on hand) and its readiness.",
        inputSchema = schema("id" to intP("Collection id"), required = listOf("id")),
        readOnly = true
    ) { args ->
        val c = vault.requireCollection(args.reqLong("id"))
        val itemsById = vault.items().associateBy { it.id }
        val entries = vault.collectionItems().filter { it.collectionId == c.id }.sortedBy { it.sortOrder }
        obj(
            "collection" to collectionView(c),
            "readiness" to readiness(entries, itemsById),
            "entries" to JsonArray(entries.map { e ->
                val item = itemsById[e.itemId]
                obj(
                    "itemId" to e.itemId,
                    "itemName" to item?.name,
                    "requiredQuantity" to e.requiredQuantity,
                    "onHand" to item?.quantity,
                    "satisfied" to (item != null && item.quantity >= e.requiredQuantity),
                    "equipped" to item?.equipped,
                    "insideItemId" to item?.parentId,
                    "notes" to e.notes
                )
            })
        )
    },

    Tool(
        name = "list_todos",
        description = "Todos, newest first. Completed todos are left out unless include_completed is true. Dates are " +
            "YYYY-MM-DD in the vault's time zone. A todo with parent_id is a sub-todo.",
        inputSchema = schema(
                "query" to strP("Text to find in title or description"),
                "state" to strP("Only this state", enum = listOf("INCOMPLETE", "IN_PROGRESS", "COMPLETE")),
                "due_from" to strP("Only todos due on or after this date (YYYY-MM-DD)"),
                "due_to" to strP("Only todos due on or before this date (YYYY-MM-DD)"),
                "overdue" to boolP("Only unfinished todos whose due date has passed"),
                "parent_id" to strP("Only direct sub-todos of this todo"),
                "top_level" to boolP("Only todos that are not sub-todos"),
                "include_completed" to boolP("Include completed todos (default false unless state=COMPLETE)"),
                "limit" to intP("Maximum rows (default $DEFAULT_LIMIT, max $MAX_LIMIT)")
        ),
        readOnly = true
    ) { args ->
        val query = args.str("query")?.trim()?.lowercase()
        val state = args.enum<TodoState>("state")
        val from = args.str("due_from")?.let(::parseDay)
        val to = args.str("due_to")?.let(::parseDay)
        val parent = args.str("parent_id")
        val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val matched = vault.todos().filter { t ->
            (state != null || args.bool("include_completed") == true || t.state != TodoState.COMPLETE) &&
                (state == null || t.state == state) &&
                (query == null || t.title.lowercase().contains(query) || t.description.lowercase().contains(query)) &&
                (from == null || (t.deadline ?: Long.MIN_VALUE) >= from) &&
                (to == null || (t.deadline ?: Long.MAX_VALUE) <= to) &&
                (args.bool("overdue") != true || (t.state != TodoState.COMPLETE && (t.deadline ?: Long.MAX_VALUE) < today)) &&
                (parent == null || t.parentTodoId == parent) &&
                (args.bool("top_level") != true || t.parentTodoId == null)
        }.sortedByDescending { it.createdAt }
        page(matched.size, matched.take(limitOf(args)).map { todoView(it) }, "todos")
    },

    Tool(
        name = "get_todo",
        description = "One todo with its direct sub-todos.",
        inputSchema = schema("id" to strP("Todo id"), required = listOf("id")),
        readOnly = true
    ) { args ->
        val todo = vault.requireTodo(args.reqStr("id"))
        val children = vault.todos().filter { it.parentTodoId == todo.id }.sortedBy { it.createdAt }
        obj("todo" to todoView(todo), "subTodos" to JsonArray(children.map { todoView(it) }))
    },

    Tool(
        name = "list_tasks",
        description = "Time-tracking task segments, most recent first. Each row is one tracked stretch of time with a " +
            "name, Kind, optional Task Type, start/end and duration. Timestamps are local time in the vault's zone.",
        inputSchema = schema(
                "query" to strP("Text to find in the task name"),
                "from" to strP("Only segments starting at or after this (YYYY-MM-DD or YYYY-MM-DDTHH:MM)"),
                "to" to strP("Only segments starting before this (YYYY-MM-DD or YYYY-MM-DDTHH:MM)"),
                "task_type_id" to strP("Only this Task Type"),
                "running" to boolP("true = only tasks running or paused right now"),
                "limit" to intP("Maximum rows (default $DEFAULT_LIMIT, max $MAX_LIMIT)")
        ),
        readOnly = true
    ) { args ->
        val query = args.str("query")?.trim()?.lowercase()
        val from = args.str("from")?.let(::parseInstant)
        val to = args.str("to")?.let(::parseInstant)
        val type = args.str("task_type_id")
        val now = nowMillis()
        val matched = vault.tasks().filter { t ->
            (query == null || t.name.lowercase().contains(query)) &&
                (from == null || t.startTime >= from) &&
                (to == null || t.startTime < to) &&
                (type == null || t.taskTypeId == type) &&
                (args.bool("running") == null || (t.isRunning || t.isPaused) == args.bool("running"))
        }.sortedByDescending { it.startTime }
        page(matched.size, matched.take(limitOf(args)).map { taskView(it, now) }, "tasks")
    },

    Tool(
        name = "list_task_types",
        description = "Task Types (the activity labels like Work or Eating) with how many segments use each, their " +
            "total tracked minutes and average points.",
        inputSchema = schema(),
        readOnly = true
    ) {
        val types = vault.taskTypes()
        val stats = computeTaskTypeStats(types, vault.tasks())
        page(types.size, types.sortedBy { it.name.lowercase() }.map { type ->
            val s = stats[type.id]
            obj(
                "id" to type.id,
                "name" to type.name,
                "taskCount" to s?.taskCount,
                "totalMinutes" to s?.let { it.totalDurationMs / 60_000 },
                "averagePoints" to s?.averagePoints,
                "mostUsedKind" to s?.mostUsedKind?.name
            )
        }, "taskTypes")
    },

    Tool(
        name = "list_schedule_blocks",
        description = "Schedule blocks (planned time like '06:00-07:00 Gym'). Give a date, or a from/to range, to see " +
            "which blocks occur on those days with repeats expanded; with neither, lists every block as stored.",
        inputSchema = schema(
                "date" to strP("A single day, YYYY-MM-DD"),
                "from" to strP("First day of a range, YYYY-MM-DD"),
                "to" to strP("Last day of a range, YYYY-MM-DD (inclusive; at most 60 days after from)")
        ),
        readOnly = true
    ) { args ->
        val blocks = vault.scheduleBlocks()
        val date = args.str("date")
        val from = args.str("from") ?: date
        val to = args.str("to") ?: date
        if (from == null || to == null) {
            page(blocks.size, blocks.sortedWith(compareBy<ScheduleBlock>({ it.dayStart }, { it.startMinuteOfDay })).map { blockView(it) }, "blocks")
        } else {
            val start = LocalDate.parse(dayString(parseDay(from)))
            val end = LocalDate.parse(dayString(parseDay(to)))
            if (end.isBefore(start)) throw ToolError("'to' is before 'from'")
            if (end.toEpochDay() - start.toEpochDay() > 60) throw ToolError("Range is longer than 60 days")
            val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
            val rows = days.flatMap { day ->
                val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                blocks.filter { occursOn(it, day, dayStart) }
                    .sortedBy { it.startMinuteOfDay }
                    .map { blockView(it).withField("occursOn", JsonPrimitive(day.toString())) }
            }
            page(rows.size, rows, "occurrences")
        }
    },

    Tool(
        name = "read_node",
        description = "Escape hatch: the raw JSON stored at a vault node, including soft-deleted rows. 'path' is one of " +
            "${Nodes.readable.joinToString()}, optionally followed by /<key> for one row. Prefer the typed tools.",
        inputSchema = schema("path" to strP("e.g. 'items', 'todos/<id>', 'settings'"), required = listOf("path")),
        readOnly = true
    ) { args ->
        val path = args.reqStr("path").trim('/')
        val top = path.substringBefore('/')
        if (top !in Nodes.readable) throw ToolError("'$top' is not a vault node; use one of ${Nodes.readable.joinToString()}")
        if (path.split('/').any { it.isEmpty() || it == ".." || it == "." }) throw ToolError("Malformed path")
        vault.readNode(path).let { if (it is JsonNull) obj("path" to path, "value" to null) else obj("path" to path, "value" to it) }
    },

    Tool(
        name = "list_deleted",
        description = "Soft-deleted rows of one kind (deletes are kept as tombstones for 30 days on the phone), so one " +
            "can be brought back with restore.",
        inputSchema = schema(
            "kind" to strP("What to list", enum = RESTORABLE_KINDS),
            "limit" to intP("Maximum rows (default $DEFAULT_LIMIT, max $MAX_LIMIT)"),
            required = listOf("kind")
        ),
        readOnly = true
    ) { args ->
        val kind = args.enum<RestorableKind>("kind") ?: throw ToolError("'kind' is required")
        val deleted: List<JsonElement> = when (kind) {
            RestorableKind.ITEM -> vault.rows("items", InventoryItem.serializer()).filter { it.isDeleted }.map { itemView(it) }
            RestorableKind.COLLECTION -> vault.rows("collections", InventoryCollection.serializer()).filter { it.isDeleted }.map { collectionView(it) }
            RestorableKind.TODO -> vault.rows("todos", Todo.serializer()).filter { it.isDeleted }.map { todoView(it) }
            RestorableKind.TASK -> vault.rows("tasks", Task.serializer()).filter { it.isDeleted }.map { taskView(it) }
            RestorableKind.TASK_TYPE -> vault.rows("task_types", TaskType.serializer()).filter { it.isDeleted }.map { encode(TaskType.serializer(), it) }
            RestorableKind.SCHEDULE_BLOCK -> vault.rows("schedule_blocks", ScheduleBlock.serializer()).filter { it.isDeleted }.map { blockView(it) }
        }
        page(deleted.size, deleted.take(limitOf(args)), "deleted")
    }
)

enum class RestorableKind { ITEM, COLLECTION, TODO, TASK, TASK_TYPE, SCHEDULE_BLOCK }

val RESTORABLE_KINDS: List<String> = RestorableKind.entries.map { it.name }

/** Same rule as ScheduleBlock.occursOn, on a java.time day so the server needs no kotlinx-datetime. */
private fun occursOn(block: ScheduleBlock, day: LocalDate, dayStart: Long): Boolean {
    if (dayStart == block.dayStart) return true
    if (dayStart < block.dayStart) return false
    if (block.repeatDaily) return true
    if (!block.repeatWeekly) return false
    val blockDay = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(block.dayStart), zone).dayOfWeek
    return blockDay == day.dayOfWeek
}
