package com.inventoria.mcp

import com.inventoria.shared.model.ScheduleBlock
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.TaskType
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoPriority
import com.inventoria.shared.model.TodoRepeat
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.InventoriaJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.util.UUID

private val KIND_NAMES = TaskKind.entries.map { it.name }

private fun todayStart(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Applies a create/update call's arguments to [base]. Shared by both so a todo is validated the
 * same way whichever route it came in by, and kept consistent: a todo with no date has no time, no
 * reminder and no repeat, because all three only mean something relative to a day.
 */
private suspend fun applyTodoArgs(vault: Vault, args: Args, base: Todo): Todo {
    var t = base
    if (args.has("title")) t = t.copy(title = args.reqStr("title").trim())
    if (args.has("description")) t = t.copy(description = args.str("description") ?: "")
    args.enum<TaskKind>("kind")?.let { t = t.copy(kind = it) }

    if (args.has("task_type_id")) {
        val typeId = args.str("task_type_id")
        if (typeId != null) vault.requireTaskType(typeId)
        t = t.copy(taskTypeId = typeId)
    }

    if (args.has("due_date")) {
        t = t.copy(deadline = if (args.isNull("due_date")) null else parseDay(args.reqStr("due_date")))
    }
    if (args.has("due_time")) {
        t = if (args.isNull("due_time")) {
            t.copy(deadlineMinuteOfDay = null)
        } else {
            // As in the app: choosing a time with no date means today.
            t.copy(deadlineMinuteOfDay = parseMinuteOfDay(args.reqStr("due_time")), deadline = t.deadline ?: todayStart())
        }
    }
    if (args.has("reminder_offset_minutes")) {
        val offset = args.pickInt("reminder_offset_minutes", null)
        if (offset != null && offset < 0) throw ToolError("'reminder_offset_minutes' cannot be negative")
        t = t.copy(reminderOffsetMinutes = offset)
    }
    if (args.has("priority")) {
        t = t.copy(priority = if (args.isNull("priority")) null else args.enum<TodoPriority>("priority"))
    }
    args.enum<TodoRepeat>("repeat")?.let { t = t.copy(repeatInterval = it) }

    if (args.has("parent_id")) {
        val parentId = args.str("parent_id")
        if (parentId != null) {
            if (parentId == t.id) throw ToolError("A todo cannot be its own parent")
            vault.requireTodo(parentId)
            val byId = vault.todos().associateBy { it.id }
            var cursor: String? = parentId
            val seen = mutableSetOf<String>()
            while (cursor != null && seen.add(cursor)) {
                if (cursor == t.id) throw ToolError("That would make the todo its own ancestor")
                cursor = byId[cursor]?.parentTodoId
            }
        }
        t = t.copy(parentTodoId = parentId)
    }

    if (t.deadline == null) {
        if (t.repeatInterval != TodoRepeat.NONE && args.has("repeat")) {
            throw ToolError("A repeating todo needs a due_date")
        }
        t = t.copy(deadlineMinuteOfDay = null, reminderOffsetMinutes = null, repeatInterval = TodoRepeat.NONE)
    }
    if (t.title.isBlank()) throw ToolError("'title' cannot be empty")
    return t
}

private val TODO_FIELDS = arrayOf(
    "title" to strP("What needs doing"),
    "description" to strP("Longer notes", nullable = true),
    "kind" to strP("Kind: $KIND_LEGEND", enum = KIND_NAMES),
    "task_type_id" to strP("Task Type id from list_task_types", nullable = true),
    "due_date" to strP("Due day, YYYY-MM-DD in the vault's time zone", nullable = true),
    "due_time" to strP("Due time of day, HH:MM (24-hour); with no due_date this means today", nullable = true),
    "reminder_offset_minutes" to intP(
        "Ring this many minutes before it is due (0 = at the due time, 1440 = a day before); needs a due_date. " +
            "Leave out for no alarm.",
        nullable = true
    ),
    "priority" to strP("Priority, A1 (highest) to C3 (lowest)", nullable = true, enum = TodoPriority.entries.map { it.name }),
    "parent_id" to strP("Make this a sub-todo of the todo with this id", nullable = true),
    "repeat" to strP("Repeat interval once a due_date is set", enum = TodoRepeat.entries.map { it.name })
)

fun plannerTools(vault: Vault): List<Tool> = listOf(
    Tool(
        name = "create_todo",
        description = "Create a todo. It lands on the phone's Todos tab at its next sync, and rings if you set a reminder.",
        inputSchema = schema(*TODO_FIELDS, required = listOf("title")),
        readOnly = false
    ) { args ->
        val now = nowMillis()
        val base = Todo(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
        val todo = applyTodoArgs(vault, args, base)
        obj("todo" to todoView(vault.create("todos", todo.id, todo, Todo.serializer())))
    },

    Tool(
        name = "update_todo",
        description = "Change a todo. Only the fields you send change; send null to clear a clearable one (clearing " +
            "due_date also clears its time, reminder and repeat). To tick it off use set_todo_state.",
        inputSchema = schema("id" to strP("Todo id"), *TODO_FIELDS, required = listOf("id")),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        val current = vault.requireTodo(id)
        val next = applyTodoArgs(vault, args, current)
        obj("todo" to todoView(vault.edit("todos", id, Todo.serializer()) { next }))
    },

    Tool(
        name = "set_todo_state",
        description = "Tick a todo off or reopen it. COMPLETE also moves every unfinished sub-todo to IN_PROGRESS " +
            "(implied covered, not individually verified); INCOMPLETE moves IN_PROGRESS sub-todos back to INCOMPLETE. " +
            "IN_PROGRESS sets just this todo, with no cascade.",
        inputSchema = schema(
            "id" to strP("Todo id"),
            "state" to strP("New state", enum = TodoState.entries.map { it.name }),
            required = listOf("id", "state")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        val state = args.enum<TodoState>("state") ?: throw ToolError("'state' is required")
        vault.requireTodo(id)
        val changed = mutableListOf<String>()

        suspend fun setState(todoId: String, to: TodoState) {
            vault.edit("todos", todoId, Todo.serializer()) { t ->
                t.copy(state = to, completedAt = if (to == TodoState.COMPLETE) nowMillis() else null)
            }
            changed += todoId
        }

        setState(id, state)
        if (state != TodoState.IN_PROGRESS) {
            val from = if (state == TodoState.COMPLETE) TodoState.INCOMPLETE else TodoState.IN_PROGRESS
            val to = if (state == TodoState.COMPLETE) TodoState.IN_PROGRESS else TodoState.INCOMPLETE
            val children = vault.todos().groupBy { it.parentTodoId }
            val visited = mutableSetOf(id)
            suspend fun visit(parentId: String) {
                for (child in children[parentId].orEmpty()) {
                    if (!visited.add(child.id)) continue
                    if (child.state == from) setState(child.id, to)
                    visit(child.id)
                }
            }
            visit(id)
        }
        obj("state" to state.name, "updatedTodoIds" to JsonArray(changed.map { JsonPrimitive(it) }))
    },

    Tool(
        name = "delete_todo",
        description = "Delete a todo (soft delete, restorable). Sub-todos are left in place unless include_sub_todos is true.",
        inputSchema = schema(
            "id" to strP("Todo id"),
            "include_sub_todos" to boolP("Also delete everything nested under it"),
            required = listOf("id")
        ),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqStr("id")
        val todo = vault.requireTodo(id)
        val children = vault.todos().groupBy { it.parentTodoId }
        val doomed = mutableListOf(id)
        if (args.bool("include_sub_todos") == true) {
            val queue = ArrayDeque(listOf(id))
            while (queue.isNotEmpty()) {
                for (child in children[queue.removeFirst()].orEmpty()) {
                    if (child.id !in doomed) { doomed += child.id; queue.addLast(child.id) }
                }
            }
        }
        doomed.forEach { vault.setDeleted("todos", it, true) }
        obj(
            "deleted" to todo.title,
            "todosDeleted" to doomed.size,
            "subTodosLeftInPlace" to if (doomed.size == 1) children[id].orEmpty().size else 0
        )
    },

    Tool(
        name = "create_task_type",
        description = "Add a Task Type (an activity label such as 'Gym'). Fails if one with that name already exists.",
        inputSchema = schema("name" to strP("Name"), required = listOf("name")),
        readOnly = false
    ) { args ->
        val name = args.reqStr("name").trim()
        if (vault.taskTypes().any { it.name.equals(name, ignoreCase = true) }) {
            throw ToolError("A task type named '$name' already exists")
        }
        val type = TaskType(id = UUID.randomUUID().toString(), name = name, updatedAt = nowMillis())
        obj("taskType" to InventoriaJson.encodeToJsonElement(TaskType.serializer(), vault.create("task_types", type.id, type, TaskType.serializer())))
    },

    Tool(
        name = "rename_task_type",
        description = "Rename a Task Type. Tasks and todos refer to it by id, so the new name shows everywhere.",
        inputSchema = schema("id" to strP("Task type id"), "name" to strP("New name"), required = listOf("id", "name")),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        vault.requireTaskType(id)
        val name = args.reqStr("name").trim()
        val updated = vault.edit("task_types", id, TaskType.serializer()) { it.copy(name = name) }
        obj("taskType" to InventoriaJson.encodeToJsonElement(TaskType.serializer(), updated))
    },

    Tool(
        name = "delete_task_type",
        description = "Delete a Task Type (soft delete). Tasks and todos keep their reference; only the label goes.",
        inputSchema = schema("id" to strP("Task type id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val type = vault.requireTaskType(args.reqStr("id"))
        vault.setDeleted("task_types", type.id, true)
        obj("deleted" to type.name)
    },

    Tool(
        name = "create_schedule_block",
        description = "Plan a block of time (e.g. 06:00-07:00 Gym). A block never scores and never starts anything by " +
            "itself; it shows on the Schedule view and Today's Now card.",
        inputSchema = schema(
            "title" to strP("Block title"),
            "date" to strP("The day it starts, YYYY-MM-DD"),
            "start" to strP("Start time HH:MM"),
            "end" to strP("End time HH:MM, later than start on the same day"),
            "kind" to strP("Kind: $KIND_LEGEND", enum = KIND_NAMES),
            "task_type_id" to strP("Task Type id", nullable = true),
            "repeat" to strP("Repeat after the first day", enum = listOf("NONE", "DAILY", "WEEKLY")),
            "notes" to strP("Notes"),
            required = listOf("title", "date", "start", "end")
        ),
        readOnly = false
    ) { args ->
        args.str("task_type_id")?.let { vault.requireTaskType(it) }
        val repeat = args.str("repeat")?.uppercase() ?: "NONE"
        val block = ScheduleBlock(
            id = UUID.randomUUID().toString(),
            title = args.reqStr("title").trim(),
            kind = args.enum<TaskKind>("kind") ?: TaskKind.GRAPHITE,
            taskTypeId = args.str("task_type_id"),
            dayStart = parseDay(args.reqStr("date")),
            startMinuteOfDay = parseMinuteOfDay(args.reqStr("start")),
            endMinuteOfDay = parseMinuteOfDay(args.reqStr("end")),
            repeatDaily = repeat == "DAILY",
            repeatWeekly = repeat == "WEEKLY",
            notes = args.str("notes") ?: "",
            updatedAt = nowMillis()
        )
        if (repeat !in listOf("NONE", "DAILY", "WEEKLY")) throw ToolError("'repeat' must be NONE, DAILY or WEEKLY")
        if (block.endMinuteOfDay <= block.startMinuteOfDay) throw ToolError("'end' must be later than 'start'")
        obj("block" to blockView(vault.create("schedule_blocks", block.id, block, ScheduleBlock.serializer())))
    },

    Tool(
        name = "update_schedule_block",
        description = "Change a schedule block. Only sent fields change.",
        inputSchema = schema(
            "id" to strP("Block id"),
            "title" to strP("Block title"),
            "date" to strP("The day it starts, YYYY-MM-DD"),
            "start" to strP("Start time HH:MM"),
            "end" to strP("End time HH:MM"),
            "kind" to strP("Kind: $KIND_LEGEND", enum = KIND_NAMES),
            "task_type_id" to strP("Task Type id", nullable = true),
            "repeat" to strP("Repeat", enum = listOf("NONE", "DAILY", "WEEKLY")),
            "notes" to strP("Notes"),
            required = listOf("id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        val existing = vault.row("schedule_blocks", id, ScheduleBlock.serializer())
        if (existing == null || existing.isDeleted) throw ToolError("No schedule block '$id'")
        args.str("task_type_id")?.let { vault.requireTaskType(it) }
        val repeat = args.str("repeat")?.uppercase()
        if (repeat != null && repeat !in listOf("NONE", "DAILY", "WEEKLY")) throw ToolError("'repeat' must be NONE, DAILY or WEEKLY")
        val updated = vault.edit("schedule_blocks", id, ScheduleBlock.serializer()) { b ->
            val next = b.copy(
                title = if (args.has("title")) args.reqStr("title").trim() else b.title,
                kind = args.enum<TaskKind>("kind") ?: b.kind,
                taskTypeId = args.pickStr("task_type_id", b.taskTypeId),
                dayStart = args.str("date")?.let(::parseDay) ?: b.dayStart,
                startMinuteOfDay = args.str("start")?.let(::parseMinuteOfDay) ?: b.startMinuteOfDay,
                endMinuteOfDay = args.str("end")?.let(::parseMinuteOfDay) ?: b.endMinuteOfDay,
                repeatDaily = if (repeat != null) repeat == "DAILY" else b.repeatDaily,
                repeatWeekly = if (repeat != null) repeat == "WEEKLY" else b.repeatWeekly,
                notes = if (args.has("notes")) args.str("notes") ?: "" else b.notes
            )
            if (next.endMinuteOfDay <= next.startMinuteOfDay) throw ToolError("'end' must be later than 'start'")
            next
        }
        obj("block" to blockView(updated))
    },

    Tool(
        name = "delete_schedule_block",
        description = "Delete a schedule block (soft delete, restorable). A repeating block is removed on every day.",
        inputSchema = schema("id" to strP("Block id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqStr("id")
        if (!vault.setDeleted("schedule_blocks", id, true)) throw ToolError("No schedule block '$id'")
        obj("deleted" to id)
    },

    Tool(
        name = "update_task",
        description = "Rename a tracked task segment or change its Task Type. Its Kind and score are not editable here: " +
            "the score was frozen when the segment finished. A task that is running or paused is live on a device and " +
            "cannot be edited from here.",
        inputSchema = schema(
            "id" to strP("Task id"),
            "name" to strP("New name"),
            "task_type_id" to strP("Task Type id", nullable = true),
            required = listOf("id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        val task = vault.row("tasks", id, Task.serializer())
        if (task == null || task.isDeleted) throw ToolError("No task '$id'")
        if (task.isRunning || task.isPaused) throw ToolError("Task '${task.name}' is running or paused on a device; stop it there first")
        args.str("task_type_id")?.let { vault.requireTaskType(it) }
        val updated = vault.edit("tasks", id, Task.serializer()) { t ->
            t.copy(
                name = if (args.has("name")) args.reqStr("name").trim() else t.name,
                isNameCustom = if (args.has("name")) true else t.isNameCustom,
                taskTypeId = args.pickStr("task_type_id", t.taskTypeId)
            )
        }
        obj("task" to taskView(updated))
    },

    Tool(
        name = "delete_task",
        description = "Delete a tracked task segment (soft delete, restorable). Not allowed while it is running or paused.",
        inputSchema = schema("id" to strP("Task id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqStr("id")
        val task = vault.row("tasks", id, Task.serializer())
        if (task == null || task.isDeleted) throw ToolError("No task '$id'")
        if (task.isRunning || task.isPaused) throw ToolError("Task '${task.name}' is running or paused on a device; stop or discard it there")
        vault.setDeleted("tasks", id, true)
        obj("deleted" to task.name)
    },

    Tool(
        name = "restore",
        description = "Bring back something deleted (see list_deleted). Use the id exactly as list_deleted shows it.",
        inputSchema = schema(
            "kind" to strP("What kind of thing", enum = RESTORABLE_KINDS),
            "id" to strP("Its id"),
            required = listOf("kind", "id")
        ),
        readOnly = false
    ) { args ->
        val kind = args.enum<RestorableKind>("kind") ?: throw ToolError("'kind' is required")
        val id = args.reqStr("id")
        val node = when (kind) {
            RestorableKind.ITEM -> Nodes.ITEMS
            RestorableKind.COLLECTION -> Nodes.COLLECTIONS
            RestorableKind.TODO -> Nodes.TODOS
            RestorableKind.TASK -> Nodes.TASKS
            RestorableKind.TASK_TYPE -> Nodes.TASK_TYPES
            RestorableKind.SCHEDULE_BLOCK -> Nodes.SCHEDULE_BLOCKS
        }
        if (kind == RestorableKind.ITEM || kind == RestorableKind.COLLECTION) {
            id.toLongOrNull() ?: throw ToolError("'id' must be a number for ${kind.name.lowercase()}")
        }
        if (!vault.setDeleted(node, id, false)) throw ToolError("No ${kind.name.lowercase()} with id '$id'")
        obj("restored" to id, "kind" to kind.name)
    },

    Tool(
        name = "set_username",
        description = "Set the display name stored in the vault's settings (the only setting that syncs between devices).",
        inputSchema = schema("name" to strP("Display name"), required = listOf("name")),
        readOnly = false
    ) { args ->
        val name = args.reqStr("name").trim()
        vault.putSetting("custom_username", JsonPrimitive(name))
        obj("username" to name)
    }
)

