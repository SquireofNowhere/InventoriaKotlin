package com.inventoria.shared.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How long a tracked session is meant to go for, and what should happen on the way, as one small
 * value stored on the session's segments ([Task.timePlan]) as text:
 *
 *  - [goalMinutes]: the planned length ("supposed to go for 90 minutes"). It always rings when
 *    reached.
 *  - [ringAtMinutes]: ring once each time the session has been running that long ("25 and 45 minutes
 *    in"). Only time actually running counts, so a pause does not move them.
 *  - [ringEveryMinutes]: ring every so often while running ("every 30 minutes").
 *  - [stopAtGoal]: stop the session by itself when the planned length is reached.
 *  - [stopAtMillis]: stop the session by itself at this clock instant ("stop at 17:00"). Absolute, so
 *    nothing has to remember which day "17:00" meant.
 *
 * The Android app keeps an identical copy of this (plus the alarm arithmetic, which only it needs)
 * and the text form is the contract between them: `goal=90 at=25,45 every=30 stop=goal
 * stopat=1730000000000` (all minutes except the last).
 */
data class TaskTimePlan(
    val goalMinutes: Int? = null,
    val ringAtMinutes: List<Int> = emptyList(),
    val ringEveryMinutes: Int? = null,
    val stopAtGoal: Boolean = false,
    val stopAtMillis: Long? = null
) {
    val isEmpty: Boolean
        get() = goalMinutes == null && ringAtMinutes.isEmpty() && ringEveryMinutes == null && stopAtMillis == null

    /** Stopping at the goal needs a goal to stop at. */
    val stopsAtGoal: Boolean get() = stopAtGoal && goalMinutes != null

    fun encode(): String {
        val parts = mutableListOf<String>()
        goalMinutes?.let { parts += "goal=$it" }
        if (ringAtMinutes.isNotEmpty()) parts += "at=" + ringAtMinutes.joinToString(",")
        ringEveryMinutes?.let { parts += "every=$it" }
        if (stopsAtGoal) parts += "stop=goal"
        stopAtMillis?.let { parts += "stopat=$it" }
        return parts.joinToString(" ")
    }

    /** "Planned 1 hr · rings at 25 min, 45 min · rings every 30 min · stops at 17:00" */
    fun describe(zone: TimeZone = TimeZone.currentSystemDefault()): String {
        if (isEmpty) return "No plan"
        val parts = mutableListOf<String>()
        goalMinutes?.let { parts += "Planned ${minutesLabel(it)}" }
        if (ringAtMinutes.isNotEmpty()) parts += "rings at " + ringAtMinutes.joinToString(", ") { minutesLabel(it) }
        ringEveryMinutes?.let { parts += "rings every ${minutesLabel(it)}" }
        if (stopsAtGoal) parts += "stops when the plan is up"
        stopAtMillis?.let { parts += "stops at ${clockLabel(it, zone)}" }
        return parts.joinToString(" · ")
    }

    companion object {
        val NONE = TaskTimePlan()

        /** A week is the longest a planned length or a mark may be. */
        const val MAX_MINUTES = 7 * 24 * 60

        /** Rings closer together than this are spam, the same floor as reminders. */
        const val MIN_EVERY_MINUTES = 5

        const val MAX_MARKS = 20

        /** Normalised: everything in range, marks sorted and unique, the repeat lifted to the floor. */
        fun of(
            goalMinutes: Int? = null,
            ringAtMinutes: Collection<Int> = emptyList(),
            ringEveryMinutes: Int? = null,
            stopAtGoal: Boolean = false,
            stopAtMillis: Long? = null
        ): TaskTimePlan {
            val goal = goalMinutes?.takeIf { it in 1..MAX_MINUTES }
            return TaskTimePlan(
                goalMinutes = goal,
                ringAtMinutes = ringAtMinutes.filter { it in 1..MAX_MINUTES }.distinct().sorted().take(MAX_MARKS),
                ringEveryMinutes = ringEveryMinutes?.takeIf { it in 1..MAX_MINUTES }?.coerceAtLeast(MIN_EVERY_MINUTES),
                stopAtGoal = stopAtGoal && goal != null,
                stopAtMillis = stopAtMillis?.takeIf { it > 0 }
            )
        }

        /** Reads [encode]'s output. Anything unreadable is dropped rather than failing. */
        fun parse(raw: String): TaskTimePlan {
            var goal: Int? = null
            var marks = emptyList<Int>()
            var every: Int? = null
            var stopAtGoal = false
            var stopAt: Long? = null
            raw.trim().split(Regex("\\s+")).forEach { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                when (key) {
                    "goal" -> goal = value.toIntOrNull()
                    "at" -> marks = value.split(',').mapNotNull { it.toIntOrNull() }
                    "every" -> every = value.toIntOrNull()
                    "stop" -> stopAtGoal = value == "goal"
                    "stopat" -> stopAt = value.toLongOrNull()
                }
            }
            return of(goal, marks, every, stopAtGoal, stopAt)
        }

        /** 25 -> "25 min", 60 -> "1 hr", 90 -> "1 hr 30 min", 2880 -> "2 days". */
        fun minutesLabel(minutes: Int): String = when {
            minutes % (24 * 60) == 0 -> (minutes / (24 * 60)).let { if (it == 1) "1 day" else "$it days" }
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} hr"
            else -> "${minutes / 60} hr ${minutes % 60} min"
        }

        /** The time of day of [millis] as "17:00" in [zone]. */
        fun clockLabel(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
            val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
            return time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')
        }
    }
}

/** The plan a session follows: the one on its most recent segment, which resuming copies forward. */
fun List<Task>.sessionTimePlan(): TaskTimePlan =
    maxByOrNull { it.startTime }?.let { TaskTimePlan.parse(it.timePlan) } ?: TaskTimePlan.NONE
