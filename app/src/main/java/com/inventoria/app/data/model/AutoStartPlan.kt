package com.inventoria.app.data.model

/**
 * When a todo starts tracking itself, stored on the todo ([Todo.autoStart]) as text, the same way
 * its reminders are:
 *
 *  - [beforeDueMinutes]: start this long before the due moment (0 = at the due time). Needs a
 *    deadline; an all-day one counts from 09:00, like reminders do.
 *  - [schedules]: start on the calendar -- every day, chosen weekdays, a day of the month, or one
 *    date -- with no deadline needed. The same rules a reminder schedule uses; a repeat on the day
 *    makes no sense for a start, so it is dropped.
 *  - [runMinutes]: how long the started session is allowed to run before it stops by itself. Null
 *    lets it run until you stop it.
 *
 * Example: `before=15 on=w15@0800 run=45`.
 */
data class AutoStartPlan(
    val beforeDueMinutes: Int? = null,
    val schedules: List<ReminderSchedule> = emptyList(),
    val runMinutes: Int? = null
) {
    /** A length with nothing to start it does nothing, so it does not make the plan non-empty. */
    val isEmpty: Boolean get() = beforeDueMinutes == null && schedules.isEmpty()

    /** True when part of the plan counts from the deadline, so it means nothing without one. */
    val needsDeadline: Boolean get() = beforeDueMinutes != null

    fun encode(): String {
        val parts = mutableListOf<String>()
        beforeDueMinutes?.let { parts += "before=$it" }
        if (schedules.isNotEmpty()) parts += "on=" + schedules.joinToString(",") { it.token }
        runMinutes?.let { parts += "run=$it" }
        return parts.joinToString(" ")
    }

    /** "15 min before it's due, Every day at 08:00 · then stops after 45 min" */
    fun describe(): String {
        if (isEmpty) return "Not automatic"
        val starts = buildList {
            beforeDueMinutes?.let { add(if (it == 0) "At the due time" else "${TaskTimePlan.minutesLabel(it)} before it's due") }
            schedules.forEach { add(it.describe()) }
        }
        val tail = runMinutes?.let { " · then stops after ${TaskTimePlan.minutesLabel(it)}" } ?: ""
        return starts.joinToString(" · ") + tail
    }

    /** The plan the started session runs under: [runMinutes] as both its planned length and its stop. */
    fun sessionPlan(): TaskTimePlan =
        runMinutes?.let { TaskTimePlan.of(goalMinutes = it, stopAtGoal = true) } ?: TaskTimePlan.NONE

    companion object {
        val NONE = AutoStartPlan()

        /** A start up to a year ahead of the due moment is as far as it goes, as with reminders. */
        const val MAX_BEFORE_MINUTES = ReminderPlan.MAX_LEAD_MINUTES

        const val MAX_SCHEDULES = 10

        fun of(
            beforeDueMinutes: Int?,
            schedules: Collection<ReminderSchedule>,
            runMinutes: Int?
        ): AutoStartPlan = AutoStartPlan(
            beforeDueMinutes = beforeDueMinutes?.takeIf { it in 0..MAX_BEFORE_MINUTES },
            schedules = schedules
                .mapNotNull { it.copy(burstEveryMinutes = 0, burstUntilMinute = it.minuteOfDay).normalised() }
                .distinct()
                .take(MAX_SCHEDULES),
            runMinutes = runMinutes?.takeIf { it in 1..TaskTimePlan.MAX_MINUTES }
        )

        /** Reads [encode]'s output. Anything unreadable is dropped rather than failing. */
        fun parse(raw: String): AutoStartPlan {
            var before: Int? = null
            var schedules = emptyList<ReminderSchedule>()
            var run: Int? = null
            raw.trim().split(Regex("\\s+")).forEach { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                when (key) {
                    "before" -> before = value.toIntOrNull()
                    "on" -> schedules = value.split(',').mapNotNull { ReminderSchedule.parse(it) }
                    "run" -> run = value.toIntOrNull()
                }
            }
            return of(before, schedules, run)
        }
    }
}

/** The auto-start rules this todo carries. */
fun Todo.autoStartPlan(): AutoStartPlan = AutoStartPlan.parse(autoStart)

/** This todo with [plan] set. With no deadline the "before it's due" part is dropped, since it has
 * nothing to count from; the schedules stay, because they count from the calendar. */
fun Todo.withAutoStart(plan: AutoStartPlan): Todo {
    val effective = if (deadline == null) AutoStartPlan.of(null, plan.schedules, plan.runMinutes) else plan
    return copy(autoStart = effective.encode())
}

/**
 * The next instant after [now] this todo should start tracking itself, or null when nothing is
 * left: no plan, finished, deleted, or every start already behind us. As with reminders, only the
 * next one is ever asked for.
 */
fun Todo.nextAutoStartAfter(now: Long): Long? {
    if (isDeleted || state == TodoState.COMPLETE) return null
    val plan = autoStartPlan()
    if (plan.isEmpty) return null
    val due = dueMillis()
    val beforeDue = if (due != null) plan.beforeDueMinutes?.let { due - it * 60_000L }?.takeIf { it > now } else null
    val onCalendar = plan.schedules.mapNotNull { it.nextAfter(now) }
    return (listOfNotNull(beforeDue) + onCalendar).minOrNull()
}
