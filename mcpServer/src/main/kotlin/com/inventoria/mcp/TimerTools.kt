package com.inventoria.mcp

import com.inventoria.shared.model.Scoring
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.nowMillis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/*
 * Time tracking: back-filling finished segments, and driving the timers.
 *
 * The session rules here are the phone's own (TaskRepository): a *session* is every Task row that
 * shares a groupId, a pause closes the running segment and a resume opens a new one, stopping
 * freezes the score and ends the session, and an "interruption" is a session started on top of
 * another that resumes the other when it stops. The phone reads and writes these same rows, so each
 * rule is ported rather than reinvented -- see Scoring for the score itself.
 */

/** The tracker screen and the widget both refuse a sixth concurrent session. */
private const val MAX_ACTIVE_SESSIONS = 5

private const val PHONE_NOTE = "The phone shows this the next time it syncs. Its foreground timer notification only " +
    "appears once the app is opened, but the elapsed time is correct either way."

private suspend fun activeSessions(vault: Vault): Map<String, List<Task>> =
    vault.tasks().groupBy { it.groupId }.filter { (_, segments) -> segments.any { it.isSessionActive } }

private suspend fun hasActiveInterrupter(vault: Vault, groupId: String): Boolean =
    activeSessions(vault).values.any { segments -> segments.any { it.interruptedGroupId == groupId } }

/** The score a segment of [kind] lasting [durationMs] freezes at, by the streak as of [endedBy]. */
private suspend fun scoreOf(vault: Vault, kind: TaskKind, durationMs: Long, endedBy: Long = Long.MAX_VALUE): Int =
    Scoring.frozenScore(kind, durationMs, Scoring.streakCount(vault.tasks(), kind, endedBy))

private suspend fun endSession(vault: Vault, groupId: String) {
    for (t in vault.tasks().filter { it.groupId == groupId && it.isSessionActive }) {
        vault.edit("tasks", t.id, Task.serializer()) { it.copy(isSessionActive = false) }
    }
}

/** Freezes [running] at [now], then ends its session. The score is read before the session ends, as on the phone. */
private suspend fun stopRunningSegment(vault: Vault, running: Task, now: Long) {
    val duration = (now - running.startTime).coerceAtLeast(0L)
    val score = scoreOf(vault, running.kind, duration)
    vault.edit("tasks", running.id, Task.serializer()) {
        it.copy(isRunning = false, endTime = now, duration = duration, score = score)
    }
    endSession(vault, running.groupId)
}

/** Stops whatever is interrupting [groupId], and whatever interrupts that, deepest first. */
private suspend fun stopInterruptionChain(vault: Vault, groupId: String, now: Long) {
    val interrupting = activeSessions(vault).filter { (_, segments) -> segments.any { it.interruptedGroupId == groupId } }
    for ((interrupterGroupId, segments) in interrupting) {
        stopInterruptionChain(vault, interrupterGroupId, now)
        val running = segments.firstOrNull { it.isRunning }
        if (running != null) stopRunningSegment(vault, running, now) else endSession(vault, interrupterGroupId)
    }
}

/** Closes [groupId]'s running segment as a pause. Returns it, or null when nothing was running. */
private suspend fun pauseSession(vault: Vault, groupId: String, now: Long): Task? {
    val running = vault.tasks().firstOrNull { it.groupId == groupId && it.isRunning } ?: return null
    val duration = (now - running.startTime).coerceAtLeast(0L)
    val score = scoreOf(vault, running.kind, duration)
    vault.edit("tasks", running.id, Task.serializer()) {
        it.copy(isRunning = false, isPaused = true, endTime = now, duration = duration, score = score)
    }
    return running
}

/**
 * Un-pauses [groupId] by opening a fresh running segment, first collapsing any interruption chain
 * stacked on top. Carries the session's links and type over from its latest segment, as the phone
 * does, or a resume would sever them.
 */
private suspend fun resumeSession(vault: Vault, groupId: String, now: Long): Task? {
    val segments = vault.tasks().filter { it.groupId == groupId }
    if (segments.none { it.isSessionActive } || segments.any { it.isRunning }) return null
    val latest = segments.maxByOrNull { it.startTime } ?: return null
    stopInterruptionChain(vault, groupId, now)
    val task = Task(
        id = UUID.randomUUID().toString(),
        groupId = groupId,
        name = latest.name,
        kind = latest.kind,
        taskTypeId = latest.taskTypeId,
        isRunning = true,
        startTime = now,
        interruptedGroupId = latest.interruptedGroupId,
        countsForStreak = latest.countsForStreak,
        originTodoId = latest.originTodoId,
        updatedAt = nowMillis()
    )
    return vault.create("tasks", task.id, task, Task.serializer())
}

/**
 * Readies [parentGroupId] to have a child session started on it; true when the link should be made.
 * A running parent is paused. A parent already paused only because another child runs on it accepts
 * a further child. A parent paused by hand does not: stopping the child would resume it unasked.
 */
private suspend fun beginInterruptionOf(vault: Vault, parentGroupId: String, now: Long): Boolean {
    val segments = activeSessions(vault)[parentGroupId] ?: return false
    if (segments.any { it.isRunning }) {
        pauseSession(vault, parentGroupId, now)
        return true
    }
    return hasActiveInterrupter(vault, parentGroupId)
}

/** The session of the nearest ancestor todo that is being tracked and can be interrupted, as the todo's Start button picks. */
private suspend fun ancestorSessionToInterrupt(vault: Vault, todoId: String, now: Long): String? {
    val groupByTodoId = vault.tasks()
        .filter { it.isSessionActive && it.originTodoId != null }
        .groupBy { it.originTodoId }
    if (groupByTodoId.isEmpty()) return null
    val byId = vault.todos().associateBy { it.id }
    val seen = mutableSetOf(todoId)
    var parentId = byId[todoId]?.parentTodoId
    while (parentId != null && seen.add(parentId)) {
        val groupId = groupByTodoId[parentId]?.firstOrNull()?.groupId
        if (groupId != null && beginInterruptionOf(vault, groupId, now)) return groupId
        parentId = byId[parentId]?.parentTodoId
    }
    return null
}

/** One session as a person would describe it: its state, its total time so far, and what it is tied to. */
private fun sessionView(segments: List<Task>, now: Long): JsonObject {
    val latest = segments.maxByOrNull { it.startTime }!!
    val running = segments.firstOrNull { it.isRunning }
    val totalMs = segments.sumOf { it.elapsedMillis(now) }
    return obj(
        "groupId" to latest.groupId,
        "name" to latest.name,
        "state" to if (running != null) "RUNNING" else "PAUSED",
        "kind" to latest.kind.name,
        "taskTypeId" to latest.taskTypeId,
        "segments" to segments.size,
        "totalMinutes" to totalMs / 60_000,
        "runningSegmentId" to running?.id,
        "interruptsGroupId" to latest.interruptedGroupId,
        "originTodoId" to latest.originTodoId,
        "startedIso" to isoString(segments.minOf { it.startTime })
    )
}

fun timerTools(vault: Vault): List<Tool> = listOf(
    Tool(
        name = "list_active_timers",
        description = "The sessions being tracked right now: each one running or paused, with its total time so far, " +
            "its Kind and Task Type, and which session it interrupts. Use the groupId with pause_timer, resume_timer " +
            "and stop_timer.",
        inputSchema = schema(),
        readOnly = true
    ) {
        val now = nowMillis()
        val sessions = activeSessions(vault).values.sortedBy { s -> s.minOf { it.startTime } }
        obj(
            "active" to sessions.size,
            "maxActive" to MAX_ACTIVE_SESSIONS,
            "sessions" to JsonArray(sessions.map { sessionView(it, now) })
        )
    },

    Tool(
        name = "create_task",
        description = "Back-fill a finished stretch of time you forgot to track: one completed segment with a name, start " +
            "and end (or a duration). It is scored exactly as if the timer had run, using the run of same-Kind sessions " +
            "that ended before it. To track something live use start_timer instead.",
        inputSchema = schema(
            "name" to strP("What you were doing"),
            "start" to strP("When it began, 2026-10-03T14:05 (local time in the vault's zone)"),
            "end" to strP("When it ended, same format. Give this or duration_minutes."),
            "duration_minutes" to intP("How long it lasted, instead of end"),
            "kind" to strP("Kind: $KIND_LEGEND (default GRAPHITE)", enum = TaskKind.entries.map { it.name }),
            "task_type_id" to strP("Task Type id", nullable = true),
            required = listOf("name", "start")
        ),
        readOnly = false
    ) { args ->
        val name = args.reqStr("name").trim()
        val start = parseInstant(args.reqStr("start"))
        val end = when {
            args.has("end") && args.has("duration_minutes") -> throw ToolError("Send end or duration_minutes, not both")
            args.has("end") -> parseInstant(args.reqStr("end"))
            args.has("duration_minutes") -> start + (args.int("duration_minutes") ?: throw ToolError("'duration_minutes' is required")) * 60_000L
            else -> throw ToolError("Send 'end' or 'duration_minutes'")
        }
        if (end <= start) throw ToolError("The end must be after the start")
        val now = nowMillis()
        if (end > now) throw ToolError("That ends in the future; a segment can only be back-filled once it is over")
        val typeId = args.str("task_type_id")
        if (typeId != null) vault.requireTaskType(typeId)
        val kind = args.enum<TaskKind>("kind") ?: TaskKind.GRAPHITE
        val duration = end - start

        val task = Task(
            id = UUID.randomUUID().toString(),
            groupId = UUID.randomUUID().toString(),
            name = name,
            taskTypeId = typeId,
            kind = kind,
            startTime = start,
            endTime = end,
            duration = duration,
            isSessionActive = false,
            isNameCustom = true,
            isKindCustom = args.has("kind"),
            score = scoreOf(vault, kind, duration, endedBy = end),
            updatedAt = now
        )
        obj("task" to taskView(vault.create("tasks", task.id, task, Task.serializer())))
    },

    Tool(
        name = "start_timer",
        description = "Start tracking time live. Give a name, or a todo_id to start from that todo (it takes the todo's " +
            "title, Kind and Task Type and is tagged to it, and runs under a tracked parent todo's session as the app " +
            "does). interrupts_group_id starts it on top of a session, pausing that one until this stops. At most " +
            "$MAX_ACTIVE_SESSIONS sessions can be active. $PHONE_NOTE",
        inputSchema = schema(
            "name" to strP("What you are doing (optional when todo_id is given)"),
            "todo_id" to strP("Start from this todo"),
            "kind" to strP("Kind: $KIND_LEGEND (default GRAPHITE, or the todo's)", enum = TaskKind.entries.map { it.name }),
            "task_type_id" to strP("Task Type id"),
            "interrupts_group_id" to strP("Run on top of this active session, which is paused meanwhile"),
            "counts_for_streak" to boolP("With interrupts_group_id: let it take part in streaks (default false)")
        ),
        readOnly = false
    ) { args ->
        val now = nowMillis()
        val sessions = activeSessions(vault)
        if (sessions.size >= MAX_ACTIVE_SESSIONS) {
            throw ToolError("$MAX_ACTIVE_SESSIONS sessions are already active; stop or finish one first")
        }
        val todo = args.str("todo_id")?.let { vault.requireTodo(it) }
        if (todo != null && sessions.values.any { s -> s.any { it.originTodoId == todo.id } }) {
            throw ToolError("A timer for todo '${todo.title}' is already active")
        }
        val name = args.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: todo?.title ?: throw ToolError("Give a 'name' or a 'todo_id'")
        val typeId = args.str("task_type_id") ?: todo?.taskTypeId
        if (typeId != null) vault.requireTaskType(typeId)
        val kind = args.enum<TaskKind>("kind") ?: todo?.kind ?: TaskKind.GRAPHITE

        var interrupted: String? = null
        val explicit = args.str("interrupts_group_id")
        if (explicit != null) {
            if (explicit !in sessions) throw ToolError("No active session '$explicit'; see list_active_timers")
            if (!beginInterruptionOf(vault, explicit, now)) {
                throw ToolError("That session was paused by hand, so stopping this one would resume it unasked; resume or stop it first")
            }
            interrupted = explicit
        } else if (todo != null) {
            interrupted = ancestorSessionToInterrupt(vault, todo.id, now)
        }

        val task = Task(
            id = UUID.randomUUID().toString(),
            groupId = UUID.randomUUID().toString(),
            name = name,
            taskTypeId = typeId,
            kind = kind,
            isKindCustom = args.has("kind"),
            isNameCustom = args.has("name"),
            isRunning = true,
            startTime = now,
            interruptedGroupId = interrupted,
            // Deliberate work under a parent todo counts toward streaks; a plain interruption does not.
            countsForStreak = if (explicit != null) args.bool("counts_for_streak") ?: false else interrupted != null,
            originTodoId = todo?.id,
            updatedAt = now
        )
        obj(
            "task" to taskView(vault.create("tasks", task.id, task, Task.serializer())),
            "interruptsGroupId" to interrupted,
            "note" to PHONE_NOTE
        )
    },

    Tool(
        name = "pause_timer",
        description = "Pause a running session: closes its running segment and scores it. Resume with resume_timer.",
        inputSchema = schema("group_id" to strP("Session id from list_active_timers"), required = listOf("group_id")),
        readOnly = false
    ) { args ->
        val groupId = args.reqStr("group_id")
        val paused = pauseSession(vault, groupId, nowMillis()) ?: throw ToolError("No running session '$groupId'")
        obj("paused" to paused.name, "groupId" to groupId)
    },

    Tool(
        name = "resume_timer",
        description = "Resume a paused session by opening a new running segment on it. Any interruption stacked on top is " +
            "stopped first, since there is nothing left for it to return to. $PHONE_NOTE",
        inputSchema = schema("group_id" to strP("Session id from list_active_timers"), required = listOf("group_id")),
        readOnly = false
    ) { args ->
        val groupId = args.reqStr("group_id")
        val task = resumeSession(vault, groupId, nowMillis())
            ?: throw ToolError("Session '$groupId' is not active and paused (it may be running already, or finished)")
        obj("task" to taskView(task), "note" to PHONE_NOTE)
    },

    Tool(
        name = "stop_timer",
        description = "Stop a session for good: stops anything interrupting it, freezes its score, and resumes the session " +
            "it was interrupting, if any. If it was started from a todo the todo is NOT ticked off; the result names " +
            "it so you can use set_todo_state.",
        inputSchema = schema("group_id" to strP("Session id from list_active_timers"), required = listOf("group_id")),
        readOnly = false
    ) { args ->
        val groupId = args.reqStr("group_id")
        val now = nowMillis()
        val segments = vault.tasks().filter { it.groupId == groupId }
        if (segments.none { it.isSessionActive }) throw ToolError("No active session '$groupId'")
        val running = segments.firstOrNull { it.isRunning }
        // A paused session has no running segment to ask, so read its links off the latest one.
        val reference = running ?: segments.maxByOrNull { it.startTime }
        val interrupted = reference?.interruptedGroupId
        val originTodoId = reference?.originTodoId

        stopInterruptionChain(vault, groupId, now)
        if (running != null) stopRunningSegment(vault, running, now) else endSession(vault, groupId)
        if (interrupted != null && !hasActiveInterrupter(vault, interrupted)) resumeSession(vault, interrupted, now)

        val total = vault.tasks().filter { it.groupId == groupId }
        obj(
            "stopped" to reference?.name,
            "totalMinutes" to total.sumOf { it.duration } / 60_000,
            "points" to Math.round(total.sumOf { it.score } / 6.0) / 10.0,
            "originTodoId" to originTodoId,
            "resumedGroupId" to interrupted
        )
    }
)
