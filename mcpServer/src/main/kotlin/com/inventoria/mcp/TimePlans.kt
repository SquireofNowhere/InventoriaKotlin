package com.inventoria.mcp

import com.inventoria.shared.model.AutoStartPlan
import com.inventoria.shared.model.ReminderPlan
import com.inventoria.shared.model.ReminderSchedule
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskTimePlan
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.autoStartPlan
import com.inventoria.shared.model.withAutoStart
import kotlinx.datetime.TimeZone

/*
 * The pure rules behind the tools that plan time: a session's time plan, a finished segment's
 * times, and a todo's auto start. Kept free of the vault so each can be tested on its own; the tools
 * only read the rows, call these, and write what comes back.
 */

/** [TaskTimePlan.describe] with its clock times read in the vault's zone, not this machine's. */
fun TaskTimePlan.describeInVaultZone(): String = describe(TimeZone.of(zone.id))

// --- A session's time plan -----------------------------------------------------------------------

internal val TIME_PLAN_KEYS = listOf("goal_minutes", "ring_at_minutes", "ring_every_minutes", "stop_at_goal", "stop_at")

internal val TIME_PLAN_FIELDS = arrayOf(
    "goal_minutes" to intP(
        "Planned length in minutes (up to a week). The phone rings when it is reached and shows progress " +
            "against it. null removes it.",
        nullable = true
    ),
    "ring_at_minutes" to arrP(
        "integer",
        "Ring once when the session has been running this many minutes, e.g. [25,45]. Only time actually " +
            "running counts, so a pause does not move them. Up to ${TaskTimePlan.MAX_MARKS}. [] clears them."
    ),
    "ring_every_minutes" to intP(
        "Ring every so many minutes of running time (at least ${TaskTimePlan.MIN_EVERY_MINUTES}). null stops the repeat.",
        nullable = true
    ),
    "stop_at_goal" to boolP("Stop the session by itself when goal_minutes is reached (needs a goal_minutes)."),
    "stop_at" to strP(
        "Stop the session by itself at this moment: 2026-10-26T17:00 (local time in the vault's zone) or epoch " +
            "milliseconds. Must be in the future. null removes it.",
        nullable = true
    )
)

fun hasTimePlanArgs(args: Args): Boolean = TIME_PLAN_KEYS.any(args::has)

/**
 * [base] with the plan fields in [args] applied. Each field changes only its own part, null clears
 * it, and a clash (stopping at a goal that is not there) is an error rather than a silent drop.
 */
fun applyTimePlanArgs(args: Args, base: TaskTimePlan, now: Long): TaskTimePlan {
    var goal = base.goalMinutes
    var marks = base.ringAtMinutes
    var every = base.ringEveryMinutes
    var stopAtGoal = base.stopAtGoal
    var stopAt = base.stopAtMillis

    if (args.has("goal_minutes")) {
        goal = args.int("goal_minutes")
        if (goal != null && goal !in 1..TaskTimePlan.MAX_MINUTES) {
            throw ToolError("'goal_minutes' must be between 1 and ${TaskTimePlan.MAX_MINUTES} (a week)")
        }
    }
    if (args.has("ring_at_minutes")) {
        marks = if (args.isNull("ring_at_minutes")) emptyList() else {
            val given = args.longList("ring_at_minutes") ?: emptyList()
            if (given.size > TaskTimePlan.MAX_MARKS) {
                throw ToolError("'ring_at_minutes' takes at most ${TaskTimePlan.MAX_MARKS} entries")
            }
            given.map {
                if (it !in 1..TaskTimePlan.MAX_MINUTES) {
                    throw ToolError("'ring_at_minutes' entries must be between 1 and ${TaskTimePlan.MAX_MINUTES}; got $it")
                }
                it.toInt()
            }
        }
    }
    if (args.has("ring_every_minutes")) {
        every = args.int("ring_every_minutes")
        if (every != null && every !in TaskTimePlan.MIN_EVERY_MINUTES..TaskTimePlan.MAX_MINUTES) {
            throw ToolError("'ring_every_minutes' must be between ${TaskTimePlan.MIN_EVERY_MINUTES} and ${TaskTimePlan.MAX_MINUTES}")
        }
    }
    if (args.has("stop_at_goal")) stopAtGoal = args.bool("stop_at_goal") ?: false
    if (args.has("stop_at")) {
        stopAt = args.str("stop_at")?.let(::parseInstant)
        if (stopAt != null && stopAt <= now) throw ToolError("'stop_at' is already in the past")
    }

    if (goal == null) {
        if (args.bool("stop_at_goal") == true) throw ToolError("'stop_at_goal' needs a goal_minutes to stop at")
        // Removing the goal takes the stop that hung off it along.
        stopAtGoal = false
    }
    return TaskTimePlan.of(goal, marks, every, stopAtGoal, stopAt)
}

// --- A finished segment's times -----------------------------------------------------------------

/**
 * The start and end [task] ends up with after an edit. Whatever is not sent stays: send `start`
 * and/or `end`, or `start`/nothing plus `duration_minutes` to set the length from the start. A
 * segment can only be moved to a time that is already over, as a back-filled one can.
 */
fun resolveSegmentTimes(task: Task, start: String?, end: String?, durationMinutes: Int?, now: Long): Pair<Long, Long> {
    if (start == null && end == null && durationMinutes == null) {
        throw ToolError("Send start, end or duration_minutes to change the times")
    }
    if (end != null && durationMinutes != null) throw ToolError("Send end or duration_minutes, not both")
    if (durationMinutes != null && durationMinutes < 1) throw ToolError("'duration_minutes' must be at least 1")

    val newStart = start?.let(::parseInstant) ?: task.startTime
    val oldEnd = task.endTime ?: (task.startTime + task.duration)
    val newEnd = when {
        end != null -> parseInstant(end)
        durationMinutes != null -> newStart + durationMinutes * 60_000L
        else -> oldEnd
    }
    if (newEnd <= newStart) throw ToolError("The end must be after the start")
    if (newEnd > now) throw ToolError("That ends in the future; a segment can only be moved to time that is already over")
    return newStart to newEnd
}

/** The other tracked segments that overlap [start]..[end], so an edit that lands on top of one can say so. */
fun overlappingSegments(others: List<Task>, own: Task, start: Long, end: Long): List<Task> =
    others.filter { it.id != own.id && !it.isDeleted && it.startTime < end && (it.endTime ?: Long.MAX_VALUE) > start }

// --- A todo's auto start ------------------------------------------------------------------------

internal val AUTO_START_KEYS = listOf("auto_start_before", "auto_start_schedules", "auto_start_run_minutes")

internal val AUTO_START_FIELDS = arrayOf(
    "auto_start_before" to strP(
        "Start tracking this todo by itself this long before it is due: 30m, 4h, 2d or 1w (0m = at the due time; " +
            "all-day todos count from 09:00). Needs a due_date. null removes it.",
        nullable = true
    ),
    "auto_start_schedules" to arrP(
        "string",
        "Start tracking on the calendar, with no due_date needed, as the compact tokens reminder_schedules uses: " +
            "d@0800 (every day), w15@0800 (Mon and Fri), m15@0900 (the 15th), o20261103@1000 (once). A ~5-1700 " +
            "repeat on the end means nothing for a start and is dropped. Up to ${AutoStartPlan.MAX_SCHEDULES}. " +
            "Replaces the schedules; [] clears them."
    ),
    "auto_start_run_minutes" to intP(
        "Stop the started session by itself this many minutes after it begins. Only with an auto start " +
            "(auto_start_before or auto_start_schedules). null lets it run until stopped.",
        nullable = true
    )
)

/**
 * [todo] with the auto-start fields in [args] applied. Like reminders, each field changes only its
 * own part. A start counted back from the deadline is refused when there is no deadline, rather than
 * quietly dropped, and a run length with nothing to start it goes when the last start does.
 */
fun applyAutoStartArgs(args: Args, todo: Todo): Todo {
    if (AUTO_START_KEYS.none(args::has)) return todo
    val current = todo.autoStartPlan()
    var before = current.beforeDueMinutes
    var schedules = current.schedules
    var run = current.runMinutes

    if (args.has("auto_start_before")) {
        before = if (args.isNull("auto_start_before")) null else {
            val raw = args.reqStr("auto_start_before")
            ReminderPlan.leadMinutesOf(raw)
                ?: throw ToolError("'auto_start_before' looks like 30m, 4h, 2d or 1w (up to a year); got '$raw'")
        }
        if (before != null && todo.deadline == null) throw ToolError("'auto_start_before' needs a due_date")
    }
    if (args.has("auto_start_schedules")) {
        schedules = if (args.isNull("auto_start_schedules")) emptyList() else {
            val tokens = args.strList("auto_start_schedules") ?: emptyList()
            if (tokens.size > AutoStartPlan.MAX_SCHEDULES) {
                throw ToolError("'auto_start_schedules' takes at most ${AutoStartPlan.MAX_SCHEDULES} entries")
            }
            tokens.map {
                ReminderSchedule.parse(it)
                    ?: throw ToolError(
                        "'auto_start_schedules' entries look like d@0800, w15@0800, m15@0900 or o20261103@1000; got '$it'"
                    )
            }
        }
    }
    if (args.has("auto_start_run_minutes")) {
        run = args.int("auto_start_run_minutes")
        if (run != null && run !in 1..TaskTimePlan.MAX_MINUTES) {
            throw ToolError("'auto_start_run_minutes' must be between 1 and ${TaskTimePlan.MAX_MINUTES} (a week)")
        }
    }

    if (before == null && schedules.isEmpty()) {
        if (args.has("auto_start_run_minutes") && run != null) {
            throw ToolError("'auto_start_run_minutes' needs an auto start: set auto_start_before or auto_start_schedules too")
        }
        run = null
    }
    return todo.withAutoStart(AutoStartPlan.of(before, schedules, run))
}
