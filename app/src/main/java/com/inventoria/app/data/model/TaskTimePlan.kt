package com.inventoria.app.data.model

import java.util.Calendar

/**
 * How long a tracked session is meant to go for, and what should happen on the way, as one small
 * value stored on the session's segments ([Task.timePlan]) as text:
 *
 *  - [goalMinutes]: the planned length ("supposed to go for 90 minutes"). It always rings when
 *    reached, and the task details screen shows progress against it.
 *  - [ringAtMinutes]: ring once each time the session has been running that long ("25 and 45 minutes
 *    in"). Only time actually running counts, so a pause does not move them.
 *  - [ringEveryMinutes]: ring every so often while running ("every 30 minutes").
 *  - [stopAtGoal]: stop the session by itself when the planned length is reached.
 *  - [stopAtMillis]: stop the session by itself at this clock instant ("stop at 17:00"). Absolute, so
 *    nothing has to remember which day "17:00" meant.
 *
 * Text form, so it syncs as one field and a build that does not know a part ignores it:
 * `goal=90 at=25,45 every=30 stop=goal stopat=1730000000000` (all minutes except the last).
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

    val goalMillis: Long? get() = goalMinutes?.let { it * MILLIS_PER_MINUTE }

    fun encode(): String {
        val parts = mutableListOf<String>()
        goalMinutes?.let { parts += "goal=$it" }
        if (ringAtMinutes.isNotEmpty()) parts += "at=" + ringAtMinutes.joinToString(",")
        ringEveryMinutes?.let { parts += "every=$it" }
        if (stopsAtGoal) parts += "stop=goal"
        stopAtMillis?.let { parts += "stopat=$it" }
        return parts.joinToString(" ")
    }

    /** "Planned 1 hr · rings at 25 min, 45 min · every 30 min · stops at 17:00" */
    fun describe(): String {
        if (isEmpty) return "No plan"
        val parts = mutableListOf<String>()
        goalMinutes?.let { parts += "Planned ${minutesLabel(it)}" }
        if (ringAtMinutes.isNotEmpty()) parts += "rings at " + ringAtMinutes.joinToString(", ") { minutesLabel(it) }
        ringEveryMinutes?.let { parts += "rings every ${minutesLabel(it)}" }
        if (stopsAtGoal) parts += "stops when the plan is up"
        stopAtMillis?.let { parts += "stops at ${clockLabel(it)}" }
        return parts.joinToString(" · ")
    }

    /**
     * The next point, in time the session has been running, at which it rings -- strictly after
     * [elapsedMs] -- or null. The planned length counts as a ring.
     */
    fun nextRingElapsedAfter(elapsedMs: Long): Long? {
        val candidates = mutableListOf<Long>()
        ringAtMinutes.forEach { minutes -> (minutes * MILLIS_PER_MINUTE).takeIf { it > elapsedMs }?.let(candidates::add) }
        goalMillis?.takeIf { it > elapsedMs }?.let(candidates::add)
        ringEveryMinutes?.let { every ->
            val step = every * MILLIS_PER_MINUTE
            candidates.add((elapsedMs / step + 1) * step)
        }
        return candidates.minOrNull()
    }

    /**
     * The clock instant a session that is running, with [elapsedMs] on its clock at [now], is due
     * to be stopped by this plan, or null when nothing stops it. Can be in the past: a plan that
     * ran out while the phone was off still names the moment it should have ended.
     */
    fun stopInstant(elapsedMs: Long, now: Long): Long? {
        val byGoal = goalMillis?.takeIf { stopsAtGoal }?.let { now + (it - elapsedMs) }
        return listOfNotNull(byGoal, stopAtMillis).minOrNull()
    }

    /** The clock instant of the next thing this plan does -- a ring or the stop -- or null. */
    fun nextEventAt(elapsedMs: Long, now: Long): Long? {
        val ring = nextRingElapsedAfter(elapsedMs)?.let { now + (it - elapsedMs) }
        return listOfNotNull(ring, stopInstant(elapsedMs, now)).minOrNull()
    }

    companion object {
        val NONE = TaskTimePlan()

        const val MILLIS_PER_MINUTE = 60_000L

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

        /** A running time as the notification and dialog say it: "47 min", "1 hr 5 min". */
        fun elapsedLabel(millis: Long): String {
            val minutes = (millis / MILLIS_PER_MINUTE).toInt()
            return if (minutes < 1) "under a minute" else minutesLabel(minutes)
        }

        /** The time of day of [millis] as "17:00", in the device's zone. */
        fun clockLabel(millis: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            return c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0') + ":" +
                c.get(Calendar.MINUTE).toString().padStart(2, '0')
        }

        /** The next time the clock reads [minuteOfDay] after [now]: today if it is still ahead, else tomorrow. */
        fun nextClockInstant(minuteOfDay: Int, now: Long): Long {
            val c = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                set(Calendar.MINUTE, minuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
    }
}

/**
 * Everything one session has run for: finished segments are frozen at their stored length, the
 * running one counts up from its start. A pause closes a segment, so time spent paused never counts.
 */
fun sessionElapsedMillis(segments: List<Task>, now: Long): Long =
    segments.sumOf { if (it.isRunning) maxOf(0L, now - it.startTime) else it.duration }

/** The plan a session follows: the one on its most recent segment, which resuming copies forward. */
fun List<Task>.sessionTimePlan(): TaskTimePlan =
    maxByOrNull { it.startTime }?.let { TaskTimePlan.parse(it.timePlan) } ?: TaskTimePlan.NONE
