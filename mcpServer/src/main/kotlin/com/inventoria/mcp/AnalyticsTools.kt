package com.inventoria.mcp

import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.InventoriaJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/*
 * Tools that work on many rows at once, or on the vault as a whole: reports, bulk edits, and
 * export / import. Everything that writes goes through the same Vault rules as the single-row tools.
 */

private const val MAX_BULK = 200
private const val MAX_DAYS_BY_DAY = 400

/** Nodes an export covers and an import restores. Settings is a free-form map, handled apart. */
private val EXPORT_NODES = listOf(
    Nodes.ITEMS, Nodes.ITEM_LINKS, Nodes.COLLECTIONS, Nodes.COLLECTION_ITEMS,
    Nodes.TODOS, Nodes.TASKS, Nodes.TASK_TYPES, Nodes.SCHEDULE_BLOCKS
)

/** Tools whose create-call `batch_create` may repeat. */
private val BATCHABLE = listOf(
    "create_todo", "create_item", "create_collection", "create_schedule_block", "create_task_type", "create_task"
)

/** Settings keys the phone and web app actually sync. Anything else written to the node is ignored by them. */
private val SYNCED_SETTINGS = listOf("custom_username")

private val SAFE_FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,100}")
private val SAFE_KEY = Regex("[A-Za-z0-9_-]{1,100}")

/** Where exports are written and imports read from. Fixed, so a tool call cannot touch other files. */
private val exportsDir: Path get() = home.resolve("exports")

private fun round1(x: Double) = Math.round(x * 10) / 10.0

/** A node as a key -> row map, whichever shape the database returned it in (it sends 0..n keys as an array). */
private fun rowsByKey(node: JsonElement): Map<String, JsonElement> = when (node) {
    is JsonObject -> node.filterValues { it !is JsonNull }
    is JsonArray -> node.withIndex().filter { it.value !is JsonNull }.associate { it.index.toString() to it.value }
    else -> emptyMap()
}

private fun dayRange(args: Args, defaultDaysBack: Int): Pair<Long, Long> {
    val today = LocalDate.now(zone)
    val from = args.str("from")?.let(::parseDay) ?: parseDay(today.minusDays(defaultDaysBack.toLong()).toString())
    // 'to' is the last day wanted, inclusive, so the window runs to the start of the day after it.
    val lastDay = args.str("to")?.let { LocalDate.parse(dayString(parseDay(it))) } ?: today
    val to = parseDay(lastDay.plusDays(1).toString())
    if (to <= from) throw ToolError("'to' is before 'from'")
    return from to to
}

fun analyticsTools(vault: Vault, others: List<Tool>): List<Tool> = listOf(
    Tool(
        name = "time_report",
        description = "Tracked time and points added up over a date range, grouped by day, Task Type, Kind or task name. " +
            "Defaults to the last 7 days up to today. Segments crossing midnight or the range edge are split " +
            "proportionally, and points are the app's hour-points (a still-running segment adds time but no points).",
        inputSchema = schema(
            "from" to strP("First day, YYYY-MM-DD (default: 6 days ago)"),
            "to" to strP("Last day, YYYY-MM-DD, inclusive (default: today)"),
            "group_by" to strP("What to group by (default DAY)", enum = ReportGroup.entries.map { it.name }),
            "task_type_id" to strP("Only segments of this Task Type"),
            "kind" to strP("Only segments of this Kind", enum = TaskKind.entries.map { it.name }),
            "limit" to intP("Most rows to return, biggest first for non-day groups (default 50)")
        ),
        readOnly = true
    ) { args ->
        val (from, to) = dayRange(args, defaultDaysBack = 6)
        val group = args.enum<ReportGroup>("group_by") ?: ReportGroup.DAY
        if (group == ReportGroup.DAY && (to - from) / 86_400_000L > MAX_DAYS_BY_DAY) {
            throw ToolError("Grouping by day covers at most $MAX_DAYS_BY_DAY days; narrow the range or group by something else")
        }
        val type = args.str("task_type_id")
        val kind = args.enum<TaskKind>("kind")
        val typeNames = vault.taskTypes().associate { it.id to it.name }
        val tasks = vault.tasks().filter { (type == null || it.taskTypeId == type) && (kind == null || it.kind == kind) }
        val rows = buildTimeReport(tasks, from, to, group, nowMillis(), zone) { id ->
            if (id == null) "(no type)" else typeNames[id] ?: "(deleted type)"
        }
        val limit = (args.int("limit") ?: 50).coerceIn(1, 500)
        obj(
            "from" to dayString(from),
            "to" to dayString(to - 1),
            "groupBy" to group.name,
            "totalMinutes" to rows.sumOf { it.millis } / 60_000,
            "totalPoints" to round1(rows.sumOf { it.points }),
            "rows" to JsonArray(rows.take(limit).map {
                obj("key" to it.key, "minutes" to it.millis / 60_000, "points" to round1(it.points), "segments" to it.segments)
            })
        )
    },

    Tool(
        name = "todo_stats",
        description = "How your todos stand: totals by state, overdue, how many were created and completed in a range " +
            "(default the last 30 days), and the open ones broken down by Kind, priority and Task Type.",
        inputSchema = schema(
            "from" to strP("First day of the range, YYYY-MM-DD (default: 29 days ago)"),
            "to" to strP("Last day, YYYY-MM-DD, inclusive (default: today)")
        ),
        readOnly = true
    ) { args ->
        val (from, to) = dayRange(args, defaultDaysBack = 29)
        val todos = vault.todos()
        val typeNames = vault.taskTypes().associate { it.id to it.name }
        val today = parseDay(LocalDate.now(zone).toString())
        val open = todos.filter { it.state != TodoState.COMPLETE }
        val completedInRange = todos.count { it.state == TodoState.COMPLETE && (it.completedAt ?: -1L) in from until to }
        val createdInRange = todos.count { it.createdAt in from until to }
        fun counts(items: List<Todo>, key: (Todo) -> String): JsonObject =
            JsonObject(items.groupingBy(key).eachCount().toList().sortedByDescending { it.second }
                .associate { it.first to JsonPrimitive(it.second) })
        obj(
            "from" to dayString(from),
            "to" to dayString(to - 1),
            "total" to todos.size,
            "byState" to obj(
                "incomplete" to todos.count { it.state == TodoState.INCOMPLETE },
                "inProgress" to todos.count { it.state == TodoState.IN_PROGRESS },
                "complete" to todos.count { it.state == TodoState.COMPLETE }
            ),
            "open" to obj(
                "count" to open.size,
                "overdue" to open.count { (it.deadline ?: Long.MAX_VALUE) < today },
                "dueToday" to open.count { it.deadline == today },
                "noDueDate" to open.count { it.deadline == null },
                "repeating" to open.count { it.deadline != null && it.repeatInterval.name != "NONE" }
            ),
            "createdInRange" to createdInRange,
            "completedInRange" to completedInRange,
            "openByKind" to counts(open) { it.kind.name },
            "openByPriority" to counts(open) { it.priority?.name ?: "(none)" },
            "openByTaskType" to counts(open) { it.taskTypeId?.let { id -> typeNames[id] } ?: "(no type)" }
        )
    },

    Tool(
        name = "bulk_update_todos",
        description = "Apply the same change to many todos at once (up to $MAX_BULK): any of kind, task_type_id, due_date, " +
            "due_time, reminder_offset_minutes, priority, repeat, and/or a state (with the app's sub-todo cascade). " +
            "Title, description and parent are per-todo and are refused here. Each todo succeeds or fails on its own.",
        inputSchema = schema(
            "ids" to arrP("string", "Todo ids"),
            *TODO_FIELDS.filter { it.first !in listOf("title", "description", "parent_id") }.toTypedArray(),
            "state" to strP("Also move each to this state", enum = TodoState.entries.map { it.name }),
            required = listOf("ids")
        ),
        readOnly = false
    ) { args ->
        for (banned in listOf("title", "description", "parent_id")) {
            if (args.has(banned)) throw ToolError("'$banned' cannot be set in bulk; use update_todo on each todo")
        }
        val ids = args.strList("ids")?.distinct()?.takeIf { it.isNotEmpty() } ?: throw ToolError("'ids' must list at least one todo")
        if (ids.size > MAX_BULK) throw ToolError("At most $MAX_BULK todos per call")
        val state = args.enum<TodoState>("state")
        val changesFields = TODO_FIELDS.any { args.has(it.first) }
        if (!changesFields && state == null) throw ToolError("Nothing to change: send at least one field or a state")

        val results = ids.map { id ->
            try {
                if (changesFields) {
                    val current = vault.requireTodo(id)
                    val next = applyTodoArgs(vault, args, current)
                    vault.edit("todos", id, Todo.serializer()) { next }
                }
                if (state != null) setTodoState(vault, id, state)
                obj("id" to id, "ok" to true)
            } catch (e: ToolError) {
                obj("id" to id, "ok" to false, "error" to e.message)
            }
        }
        obj("updated" to results.count { it["ok"] == JsonPrimitive(true) }, "failed" to results.count { it["ok"] == JsonPrimitive(false) }, "results" to JsonArray(results))
    },

    Tool(
        name = "bulk_move_items",
        description = "Move many items into one container (or out to the open) in one call, up to $MAX_BULK. Items linked as " +
            "leader/follower move together, as in move_item. Each item succeeds or fails on its own.",
        inputSchema = schema(
            "ids" to arrP("integer", "Item ids"),
            "parent_id" to intP("Container to move them into, or null to take them out", nullable = true),
            "location" to strP("Where they now are, as text; only used when parent_id is null"),
            required = listOf("ids", "parent_id")
        ),
        readOnly = false
    ) { args ->
        val ids = args.longList("ids")?.distinct()?.takeIf { it.isNotEmpty() } ?: throw ToolError("'ids' must list at least one item")
        if (ids.size > MAX_BULK) throw ToolError("At most $MAX_BULK items per call")
        if (!args.has("parent_id")) throw ToolError("'parent_id' is required (use null to take the items out)")
        val parent = args.long("parent_id")
        val location = args.str("location")
        val alreadyMoved = mutableSetOf<Long>()
        val results = ids.map { id ->
            if (id in alreadyMoved) {
                obj("id" to id, "ok" to true, "note" to "moved along with a linked item")
            } else try {
                val moved = moveItem(vault, id, parent, location)
                moved.forEach { m -> (m as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }?.let(alreadyMoved::add) }
                obj("id" to id, "ok" to true, "moved" to moved.size)
            } catch (e: ToolError) {
                obj("id" to id, "ok" to false, "error" to e.message)
            }
        }
        obj("failed" to results.count { it["ok"] == JsonPrimitive(false) }, "results" to JsonArray(results))
    },

    Tool(
        name = "batch_create",
        description = "Create many rows in one call by repeating a create tool (up to $MAX_BULK): give the tool name and an " +
            "array of argument objects, each exactly what that tool takes. Rows are created in order; one failing " +
            "does not stop the rest, and the result says which did. Tools: ${BATCHABLE.joinToString()}.",
        inputSchema = schema(
            "tool" to strP("Which create tool to repeat", enum = BATCHABLE),
            "rows" to arrP("object", "One argument object per row to create"),
            required = listOf("tool", "rows")
        ),
        readOnly = false
    ) { args ->
        val name = args.reqStr("tool")
        val tool = others.firstOrNull { it.name == name && name in BATCHABLE } ?: throw ToolError("'$name' cannot be batched; use one of ${BATCHABLE.joinToString()}")
        val rows = args.json["rows"] as? JsonArray ?: throw ToolError("'rows' must be an array of objects")
        if (rows.isEmpty()) throw ToolError("'rows' is empty")
        if (rows.size > MAX_BULK) throw ToolError("At most $MAX_BULK rows per call")
        val results = rows.mapIndexed { index, row ->
            try {
                val rowArgs = row as? JsonObject ?: throw ToolError("Row $index is not an object")
                obj("row" to index, "ok" to true, "created" to tool.handler(Args(rowArgs)))
            } catch (e: ToolError) {
                obj("row" to index, "ok" to false, "error" to e.message)
            }
        }
        obj("created" to results.count { it["ok"] == JsonPrimitive(true) }, "failed" to results.count { it["ok"] == JsonPrimitive(false) }, "results" to JsonArray(results))
    },

    Tool(
        name = "export_vault",
        description = "Write the whole vault (every node, deleted rows included) to one JSON file on this machine, under " +
            "the server's exports folder, and return where it went and how many rows each node held. A backup you can " +
            "keep, diff, or hand to import_vault.",
        inputSchema = schema("file_name" to strP("Name for the file (default vault-<timestamp>.json); letters, digits, . _ - only")),
        // Reads the vault, but writes a file here, so it is not a pure read.
        readOnly = false
    ) { args ->
        val fileName = args.str("file_name")?.trim() ?: "vault-${isoString(nowMillis()).replace(':', '-')}.json"
        if (!SAFE_FILE_NAME.matches(fileName)) throw ToolError("'file_name' may only hold letters, digits, '.', '_' and '-'")
        val counts = linkedMapOf<String, Int>()
        val nodes = linkedMapOf<String, JsonElement>()
        for (node in EXPORT_NODES) {
            val rows = rowsByKey(vault.readNode(node))
            counts[node] = rows.size
            nodes[node] = JsonObject(rows)
        }
        val settings = vault.readNode("settings")
        val document = obj(
            "format" to "inventoria-vault-export-1",
            "exportedAtIso" to isoString(nowMillis()),
            "ownerUid" to vault.ownerUid,
            "nodes" to JsonObject(nodes),
            "settings" to settings
        )
        val path = exportsDir.resolve(fileName)
        Files.createDirectories(path.parent)
        Files.writeString(path, InventoriaJson.encodeToString(JsonElement.serializer(), document))
        obj("file" to path.toAbsolutePath().toString(), "rowsByNode" to counts, "bytes" to Files.size(path))
    },

    Tool(
        name = "import_vault",
        description = "Restore rows from an export_vault file in the exports folder. Strictly additive: a row whose id is " +
            "already in the vault, even as a deleted tombstone, is left exactly as it is, so nothing is ever " +
            "overwritten. Use it to bring back rows lost to a bad sync, or to copy a backup into a fresh vault.",
        inputSchema = schema(
            "file_name" to strP("The export file's name"),
            "nodes" to arrP("string", "Only these nodes (default all of: ${EXPORT_NODES.joinToString()})"),
            "dry_run" to boolP("Report what would be restored without writing anything"),
            required = listOf("file_name")
        ),
        readOnly = false
    ) { args ->
        val fileName = args.reqStr("file_name").trim()
        if (!SAFE_FILE_NAME.matches(fileName)) throw ToolError("'file_name' may only hold letters, digits, '.', '_' and '-'")
        val path = exportsDir.resolve(fileName)
        if (!Files.isRegularFile(path)) throw ToolError("No export '$fileName' in ${exportsDir.toAbsolutePath()}")
        val document = runCatching { InventoriaJson.parseToJsonElement(Files.readString(path)) as JsonObject }
            .getOrElse { throw ToolError("'$fileName' is not valid JSON") }
        if ((document["format"] as? JsonPrimitive)?.content != "inventoria-vault-export-1") {
            throw ToolError("'$fileName' is not an export_vault file")
        }
        val stored = document["nodes"] as? JsonObject ?: throw ToolError("'$fileName' has no nodes")
        val wanted = args.strList("nodes")?.also { list ->
            list.firstOrNull { it !in EXPORT_NODES }?.let { throw ToolError("'$it' is not an importable node; use ${EXPORT_NODES.joinToString()}") }
        } ?: EXPORT_NODES
        val dryRun = args.bool("dry_run") ?: false

        val report = linkedMapOf<String, Any>()
        for (node in wanted) {
            val fromFile = rowsByKey(stored[node] ?: JsonNull)
            val present = rowsByKey(vault.readNode(node)).keys
            var restored = 0
            var skipped = 0
            var invalid = 0
            for ((key, row) in fromFile) {
                when {
                    key in present -> skipped++
                    row !is JsonObject || !SAFE_KEY.matches(key) -> invalid++
                    else -> {
                        if (!dryRun) vault.putRaw(node, key, row)
                        restored++
                    }
                }
            }
            report[node] = obj("restored" to restored, "alreadyPresent" to skipped, "invalid" to invalid)
        }
        obj("dryRun" to dryRun, "nodes" to report)
    },

    Tool(
        name = "get_settings",
        description = "The vault's settings node as stored, and which keys the apps actually sync. Today that is only the " +
            "display name (change it with set_username); other app settings live on each device and are not in the cloud.",
        inputSchema = schema(),
        readOnly = true
    ) {
        obj("settings" to vault.readNode("settings"), "syncedKeys" to SYNCED_SETTINGS)
    }
)
