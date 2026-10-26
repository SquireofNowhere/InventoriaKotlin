package com.inventoria.mcp

import com.inventoria.shared.model.ReminderPlan
import com.inventoria.shared.model.ReminderSchedule
import com.inventoria.shared.model.ReminderSpan
import com.inventoria.shared.model.ScheduleBlock
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.TaskType
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoPriority
import com.inventoria.shared.model.TodoRepeat
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.autoStartPlan
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.model.reminders
import com.inventoria.shared.model.withAutoStart
import com.inventoria.shared.model.withReminders
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
internal suspend fun applyTodoArgs(vault: Vault, args: Args, base: Todo): Todo {
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
    if (args.has("reminder_offset_minutes") || args.has("reminder_before") || args.has("reminder_every") ||
        args.has("reminder_schedules")
    ) {
        // Each field changes only its own part of the plan: sending reminder_every leaves the lead
        // times and schedules alone, and so on round.
        val current = t.reminders()
        var leads = current.leadMinutes
        var every = current.every
        var schedules = current.schedules
        if (args.has("reminder_schedules")) {
            schedules = if (args.isNull("reminder_schedules")) emptyList() else {
                val tokens = args.strList("reminder_schedules") ?: emptyList()
                if (tokens.size > ReminderPlan.MAX_SCHEDULES) {
                    throw ToolError("'reminder_schedules' takes at most ${ReminderPlan.MAX_SCHEDULES} entries")
                }
                tokens.map {
                    ReminderSchedule.parse(it)
                        ?: throw ToolError(
                            "'reminder_schedules' entries look like d@0800, w15@0800, m15@0900 or o20261103@1000, " +
                                "optionally ending ~5-1700; got '$it'"
                        )
                }
            }
        }
        if (args.has("reminder_offset_minutes")) {
            val offset = args.pickInt("reminder_offset_minutes", null)
            if (offset != null && (offset < 0 || offset > ReminderPlan.MAX_LEAD_MINUTES)) {
                throw ToolError("'reminder_offset_minutes' must be between 0 and ${ReminderPlan.MAX_LEAD_MINUTES}")
            }
            leads = listOfNotNull(offset)
        }
        if (args.has("reminder_before")) {
            leads = if (args.isNull("reminder_before")) emptyList() else {
                (args.strList("reminder_before") ?: emptyList()).map {
                    ReminderPlan.leadMinutesOf(it)
                        ?: throw ToolError("'reminder_before' entries look like 30m, 4h, 2d or 1w (up to a year); got '$it'")
                }
            }
        }
        if (args.has("reminder_every")) {
            every = if (args.isNull("reminder_every")) null else {
                val raw = args.reqStr("reminder_every")
                val span = ReminderSpan.parse(raw)
                    ?: throw ToolError("'reminder_every' looks like 30m, 2h, 1d, 1w or 1mo; got '$raw'")
                if (span.amount !in 1..ReminderPlan.MAX_EVERY_AMOUNT) {
                    throw ToolError("'reminder_every' needs an amount from 1 to ${ReminderPlan.MAX_EVERY_AMOUNT}")
                }
                if ((span.fixedMinutes ?: Long.MAX_VALUE) < ReminderPlan.MIN_EVERY_MINUTES) {
                    throw ToolError("'reminder_every' cannot be shorter than ${ReminderPlan.MIN_EVERY_MINUTES} minutes")
                }
                span
            }
        }
        t = t.withReminders(ReminderPlan.of(leads, every, schedules))
    }
    t = applyAutoStartArgs(args, t)
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
        // withReminders and withAutoStart drop what counts from the deadline and keep the calendar schedules.
        t = t.copy(deadlineMinuteOfDay = null, repeatInterval = TodoRepeat.NONE)
            .withReminders(t.reminders())
            .withAutoStart(t.autoStartPlan())
    }
    if (t.title.isBlank()) throw ToolError("'title' cannot be empty")
    return t
}

/**
 * Moves a todo to [state] the way the app's tick-off does, and returns the ids it changed. COMPLETE
 * also moves every unfinished descendant to IN_PROGRESS (covered, not individually verified);
 * INCOMPLETE moves IN_PROGRESS descendants back; IN_PROGRESS sets just this one.
 */
internal suspend fun setTodoState(vault: Vault, id: String, state: TodoState): List<String> {
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
    return changed
}

internal val TODO_FIELDS = arrayOf(
    "title" to strP("What needs doing"),
    "description" to strP("Longer notes", nullable = true),
    "kind" to strP("Kind: $KIND_LEGEND", enum = KIND_NAMES),
    "task_type_id" to strP("Task Type id from list_task_types", nullable = true),
    "due_date" to strP("Due day, YYYY-MM-DD in the vault's time zone", nullable = true),
    "due_time" to strP("Due time of day, HH:MM (24-hour); with no due_date this means today", nullable = true),
    "reminder_offset_minutes" to intP(
        "Ring once, this many minutes before it is due (0 = at the due time, 1440 = a day before); needs a " +
            "due_date. Replaces any lead times already set; for several, use reminder_before. Leave out for no alarm.",
        nullable = true
    ),
    "reminder_before" to arrP(
        "string",
        "Ring this long before it is due, once for each entry: durations like 30m, 4h, 2d, 1w (0m = at the due " +
            "time), e.g. [\"6h\",\"5h\",\"4h\"]. Replaces the lead times; [] clears them. Needs a due_date. " +
            "All-day todos count from 09:00."
    ),
    "reminder_every" to strP(
        "Keep ringing every so often until it is due, counted back from the due time and ending with it: 30m, " +
            "2h, 1d, 1w or 1mo (at least 5m). Combines with reminder_before. null stops the repeat. Needs a due_date.",
        nullable = true
    ),
    "reminder_schedules" to arrP(
        "string",
        "Calendar reminders that need no due_date, one per entry, as compact tokens: d@0800 (every day at " +
            "08:00), w15@0800 (weekdays 1=Mon..7=Sun: here Mon and Fri), m15@0900 (the 15th of every month; a " +
            "shorter month rings on its last day), o20261103@1000 (once, on that date). Add ~5-1700 to ring " +
            "again and again that day, every 5 minutes (at least 5) until 17:00, e.g. m15@0900~5-1700 for 97 " +
            "rings. Up to ${ReminderPlan.MAX_SCHEDULES}. Replaces the schedules; [] clears them."
    ),
    "priority" to strP("Priority, A1 (highest) to C3 (lowest)", nullable = true, enum = TodoPriority.entries.map { it.name }),
    "parent_id" to strP("Make this a sub-todo of the todo with this id", nullable = true),
    "repeat" to strP("Repeat interval once a due_date is set", enum = TodoRepeat.entries.map { it.name })
) + AUTO_START_FIELDS

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
        val changed = setTodoState(vault, id, state)
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
        description = "Plan a block of time (e.g. 06:00-07:00 Gym). A block scores nothing itself; it shows on the " +
            "Schedule view and Today's Now card. With auto_start the phone also begins tracking a session, named, " +
            "kinded and typed like the block, when the block starts (and with auto_stop ends it when the block ends).",
        inputSchema = schema(
            "title" to strP("Block title"),
            "date" to strP("The day it starts, YYYY-MM-DD"),
            "start" to strP("Start time HH:MM"),
            "end" to strP("End time HH:MM, later than start on the same day"),
            "kind" to strP("Kind: $KIND_LEGEND", enum = KIND_NAMES),
            "task_type_id" to strP("Task Type id", nullable = true),
            "repeat" to strP("Repeat after the first day", enum = listOf("NONE", "DAILY", "WEEKLY")),
            "auto_start" to boolP("Start tracking a session by itself when the block begins (default false)"),
            "auto_stop" to boolP("Stop that session by itself when the block ends. Needs auto_start (default false)"),
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
            autoStart = args.bool("auto_start") ?: false,
            autoStop = args.bool("auto_stop") ?: false,
            notes = args.str("notes") ?: "",
            updatedAt = nowMillis()
        )
        if (block.autoStop && !block.autoStart) throw ToolError("'auto_stop' needs 'auto_start'")
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
            "end_date" to strP(
                "The last day a repeating block shows on, YYYY-MM-DD; earlier days keep it. null removes the end.",
                nullable = true
            ),
            "auto_start" to boolP(
                "Start tracking a session by itself when the block begins. Turning it off also turns auto_stop off."
            ),
            "auto_stop" to boolP("Stop that session by itself when the block ends. Needs auto_start."),
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
            val autoStart = args.bool("auto_start") ?: b.autoStart
            if (args.bool("auto_stop") == true && !autoStart) throw ToolError("'auto_stop' needs 'auto_start'")
            val next = b.copy(
                autoStart = autoStart,
                // As in the app's editor: stopping by itself only means something when it starts by itself.
                autoStop = autoStart && (args.bool("auto_stop") ?: b.autoStop),
                title = if (args.has("title")) args.reqStr("title").trim() else b.title,
                kind = args.enum<TaskKind>("kind") ?: b.kind,
                taskTypeId = args.pickStr("task_type_id", b.taskTypeId),
                dayStart = args.str("date")?.let(::parseDay) ?: b.dayStart,
                startMinuteOfDay = args.str("start")?.let(::parseMinuteOfDay) ?: b.startMinuteOfDay,
                endMinuteOfDay = args.str("end")?.let(::parseMinuteOfDay) ?: b.endMinuteOfDay,
                repeatDaily = if (repeat != null) repeat == "DAILY" else b.repeatDaily,
                repeatWeekly = if (repeat != null) repeat == "WEEKLY" else b.repeatWeekly,
                endDay = if (args.has("end_date")) args.str("end_date")?.let(::parseDay) else b.endDay,
                notes = if (args.has("notes")) args.str("notes") ?: "" else b.notes
            )
            if (next.endMinuteOfDay <= next.startMinuteOfDay) throw ToolError("'end' must be later than 'start'")
            // A local copy: endDay is a public property of another module, which cannot be smart-cast.
            val lastDay = next.endDay
            if (lastDay != null && lastDay < next.dayStart) throw ToolError("'end_date' cannot be before the block's start date")
            next
        }
        obj("block" to blockView(updated))
    },

    Tool(
        name = "delete_schedule_block",
        description = "Delete a schedule block (soft delete, restorable). A repeating block is removed on every day, " +
            "past included -- which rewrites what the calendar shows for days already gone. To stop a repeating " +
            "block going forward and keep its past, pass keep_past_until (its last day) instead of deleting.",
        inputSchema = schema(
            "id" to strP("Block id"),
            "keep_past_until" to strP(
                "Instead of deleting, end the repeating block after this day (YYYY-MM-DD, usually yesterday or " +
                    "today) so every day up to it still shows it.",
                nullable = true
            ),
            required = listOf("id")
        ),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqStr("id")
        val keepUntil = args.str("keep_past_until")?.let(::parseDay)
        if (keepUntil != null) {
            val existing = vault.row("schedule_blocks", id, ScheduleBlock.serializer())
            if (existing == null || existing.isDeleted) throw ToolError("No schedule block '$id'")
            if (!existing.repeatDaily && !existing.repeatWeekly) {
                throw ToolError("Only a repeating block can be ended; delete this one without keep_past_until")
            }
            if (keepUntil < existing.dayStart) throw ToolError("'keep_past_until' is before the block starts; delete it instead")
            val ended = vault.edit("schedule_blocks", id, ScheduleBlock.serializer()) { it.copy(endDay = keepUntil) }
            obj("ended" to id, "block" to blockView(ended))
        } else {
            if (!vault.setDeleted("schedule_blocks", id, true)) throw ToolError("No schedule block '$id'")
            obj("deleted" to id)
        }
    },

    Tool(
        name = "update_task",
        description = "Rename a tracked task segment, change its Task Type or Kind, or change when it ran: send start and/or " +
            "end, or start/nothing plus duration_minutes (what you leave out stays). New times must already be over. " +
            "Changing the times or the Kind re-freezes the segment's score from its new length and Kind, using the " +
            "current same-Kind streak, as the app does when you edit them there. kind recolours just this segment " +
            "(the app's per-segment edit); with apply_to_session it recolours every segment of the session, which " +
            "must be finished. A segment that is running, or the paused latest segment of an active session, is " +
            "live on a device and cannot be edited: stop it first. To plan a live session's rings and stop, use " +
            "set_session_time_plan.",
        inputSchema = schema(
            "id" to strP("Task id"),
            "name" to strP("New name"),
            "task_type_id" to strP("Task Type id", nullable = true),
            "kind" to strP("New Kind: $KIND_LEGEND", enum = KIND_NAMES),
            "apply_to_session" to boolP("With kind: recolour every segment of this segment's session, not just this one"),
            "start" to strP("When it began, 2026-10-03T14:05 (local time in the vault's zone) or epoch milliseconds"),
            "end" to strP("When it ended, same format. Send this or duration_minutes."),
            "duration_minutes" to intP("How long it lasted, counted from the start; instead of end"),
            required = listOf("id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqStr("id")
        val task = vault.row("tasks", id, Task.serializer())
        if (task == null || task.isDeleted) throw ToolError("No task '$id'")
        if (isLiveSegment(vault, task)) throw ToolError("Task '${task.name}' is running or paused on a device; stop it there first")
        args.str("task_type_id")?.let { vault.requireTaskType(it) }

        val retime = args.has("start") || args.has("end") || args.has("duration_minutes")
        val times = if (retime) {
            resolveSegmentTimes(task, args.str("start"), args.str("end"), args.int("duration_minutes"), nowMillis())
        } else null

        val newKind = args.enum<TaskKind>("kind")
        val wholeSession = args.bool("apply_to_session") == true
        if (wholeSession && newKind == null) throw ToolError("'apply_to_session' needs a 'kind'")
        val kindChanged = newKind != null && newKind != task.kind
        val others = if (wholeSession && kindChanged) {
            vault.tasks().filter { it.groupId == task.groupId && it.id != task.id }
        } else emptyList()
        if (wholeSession && kindChanged && (task.isSessionActive || others.any { it.isSessionActive })) {
            throw ToolError("That session is still open; stop it before recolouring all of it")
        }

        // Every score is read before any write, as the phone does: the streak is whatever the finished
        // sessions say right now, and writing one segment would otherwise shift the next one's.
        val kind = newKind ?: task.kind
        val score = if (times != null || kindChanged) {
            val length = times?.let { (start, end) -> end - start } ?: task.duration
            scoreOf(vault, kind, length)
        } else null
        val otherScores = others.associate { it.id to scoreOf(vault, kind, it.duration) }
        val overlaps = times?.let { (start, end) -> overlappingSegments(vault.tasks(), task, start, end) }.orEmpty()

        val updated = vault.edit("tasks", id, Task.serializer()) { t ->
            t.copy(
                name = if (args.has("name")) args.reqStr("name").trim() else t.name,
                isNameCustom = if (args.has("name")) true else t.isNameCustom,
                taskTypeId = args.pickStr("task_type_id", t.taskTypeId),
                kind = kind,
                // The app marks a one-segment recolour as custom and a whole-session one as not.
                isKindCustom = if (kindChanged) !wholeSession else t.isKindCustom,
                startTime = times?.first ?: t.startTime,
                endTime = times?.second ?: t.endTime,
                duration = times?.let { (start, end) -> end - start } ?: t.duration,
                score = score ?: t.score
            )
        }
        for (other in others) {
            vault.edit("tasks", other.id, Task.serializer()) { it.copy(kind = kind, isKindCustom = false, score = otherScores.getValue(other.id)) }
        }
        obj(
            "task" to taskView(updated),
            "segmentsRecoloured" to if (wholeSession && kindChanged) others.size + 1 else null,
            "overlapsWith" to JsonArray(overlaps.take(5).map { obj("id" to it.id, "name" to it.name, "startIso" to isoString(it.startTime)) })
        )
    },

    Tool(
        name = "delete_task",
        description = "Delete a tracked task segment (soft delete, restorable). Not allowed while it is running, or " +
            "while it is the paused latest segment of an active session.",
        inputSchema = schema("id" to strP("Task id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqStr("id")
        val task = vault.row("tasks", id, Task.serializer())
        if (task == null || task.isDeleted) throw ToolError("No task '$id'")
        if (isLiveSegment(vault, task)) throw ToolError("Task '${task.name}' is running or paused on a device; stop or discard it there")
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

