package com.inventoria.shared.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/** COMPLETE is only ever set directly; IN_PROGRESS is the weaker "started" signal. */
@Serializable
enum class TodoState { INCOMPLETE, IN_PROGRESS, COMPLETE }

/** ABC-123 priority, declared best (A1) to worst (C3) so ordinal doubles as the ranking. */
@Serializable
enum class TodoPriority { A1, A2, A3, B1, B2, B3, C1, C2, C3 }

/** How often a repeating todo starts over. NONE is the ordinary one-off. */
@Serializable
enum class TodoRepeat { NONE, DAILY, WEEKLY, MONTHLY }

/**
 * users/$uid/todos, keyed by [id]. [deadline] is a start-of-day timestamp and
 * [deadlineMinuteOfDay] an optional time on that day; see the Android Todo entity for the rest.
 */
@Serializable
data class Todo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val kind: TaskKind = TaskKind.GRAPHITE,
    val taskTypeId: String? = null,
    val deadline: Long? = null,
    val deadlineMinuteOfDay: Int? = null,
    // Once a todo has a reminderPlan this only mirrors its closest lead time; see withReminders.
    val reminderOffsetMinutes: Int? = null,
    // ReminderPlan.encode() text: several lead times and/or an "every so often" repeat. Blank means
    // no plan stored, so reminderOffsetMinutes alone speaks.
    val reminderPlan: String = "",
    // AutoStartPlan.encode() text: when the todo starts tracking itself. Only the phone starts
    // anything on it; this module reads and writes the text (see AutoStartPlan).
    val autoStart: String = "",
    val priority: TodoPriority? = null,
    val parentTodoId: String? = null,
    val state: TodoState = TodoState.INCOMPLETE,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    // Vestigial on Android since v2.12; kept so a round-trip through the web never drops it.
    val activeSessionGroupId: String? = null,
    val repeatInterval: TodoRepeat = TodoRepeat.NONE,
    // Which weekdays a WEEKLY repeat falls on, as a Weekdays bit set (bit 0 Monday ... bit 6 Sunday).
    // Zero is plain weekly: a week after the last deadline.
    val repeatWeekdays: Int = 0,
    // When the todo was tagged ASAP, in millis; null when it is not. An ASAP todo is due as soon as
    // possible instead of on a day (so it has no deadline), and a timer counts up from this while it
    // is open. See isAsap.
    val asapSince: Long? = null,
    val repeatCompletedCount: Int = 0,
    val repeatMissedCount: Int = 0,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
)

/** Where an all-day deadline's alarm lands when the todo carries no time of its own. The reminder
 * maths itself is in ReminderPlan.kt. */
const val ALL_DAY_REMINDER_MINUTE_OF_DAY = 9 * 60

/** Due as soon as possible, and still open: the state in which the ASAP timer counts up. A completed
 * todo keeps its [Todo.asapSince], so it can still say how long it took, but is no longer ASAP. */
val Todo.isAsap: Boolean get() = asapSince != null && state != TodoState.COMPLETE

/** How long this todo has been ASAP: up to [now] while it is open, up to its completion once done. */
fun Todo.asapElapsedMillis(now: Long = nowMillis()): Long? {
    val since = asapSince ?: return null
    val until = if (state == TodoState.COMPLETE) completedAt ?: now else now
    return (until - since).coerceAtLeast(0L)
}

/** The deadline one cycle after [day] for a todo repeating by [repeat] on [weekdays]: a WEEKLY repeat
 * with weekdays chosen goes to the next of those days (strictly after [day]); everything else is the
 * plain interval, which is also what no chosen weekdays means. */
fun nextCycleDeadline(
    repeat: TodoRepeat,
    weekdays: Int,
    day: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    val chosen = Weekdays.clean(weekdays)
    if (repeat != TodoRepeat.WEEKLY || chosen == 0) return repeat.nextDeadline(day, zone)
    var next = day
    // At most seven steps: [chosen] is not empty, so some day within a week is in it.
    do {
        next = plusCalendar(next, DatePeriod(days = 1), zone)
    } while (!Weekdays.contains(chosen, Weekdays.isoDayOf(next, zone)))
    return next
}

/** The deadline one cycle later than [day] (a start-of-day timestamp). */
fun TodoRepeat.nextDeadline(day: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    when (this) {
        TodoRepeat.NONE -> day
        TodoRepeat.DAILY -> plusCalendar(day, DatePeriod(days = 1), zone)
        TodoRepeat.WEEKLY -> plusCalendar(day, DatePeriod(days = 7), zone)
        TodoRepeat.MONTHLY -> plusCalendar(day, DatePeriod(months = 1), zone)
    }

/**
 * This todo after its finished cycles have been settled, or null when nothing is due to happen.
 * The first overdue cycle is judged by the todo's state and every later one was necessarily
 * missed. Deterministic in its inputs, so two clients settling the same cycle write the same row.
 */
fun Todo.settleCycles(todayStart: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Todo? {
    val day = deadline ?: return null
    if (repeatInterval == TodoRepeat.NONE || isDeleted || day >= todayStart) return null
    var next: Long = day
    var completed = repeatCompletedCount
    var missed = repeatMissedCount
    var judged = false
    while (next < todayStart) {
        if (!judged && state == TodoState.COMPLETE) completed++ else missed++
        judged = true
        next = nextCycleDeadline(repeatInterval, repeatWeekdays, next, zone)
    }
    return copy(
        deadline = next,
        state = TodoState.INCOMPLETE,
        completedAt = null,
        repeatCompletedCount = completed,
        repeatMissedCount = missed
    )
}
