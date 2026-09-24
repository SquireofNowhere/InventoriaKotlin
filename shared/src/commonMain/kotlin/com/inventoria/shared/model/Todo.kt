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
    val reminderOffsetMinutes: Int? = null,
    val priority: TodoPriority? = null,
    val parentTodoId: String? = null,
    val state: TodoState = TodoState.INCOMPLETE,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    // Vestigial on Android since v2.12; kept so a round-trip through the web never drops it.
    val activeSessionGroupId: String? = null,
    val repeatInterval: TodoRepeat = TodoRepeat.NONE,
    val repeatCompletedCount: Int = 0,
    val repeatMissedCount: Int = 0,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
)

/** Where an all-day deadline's alarm lands when the todo carries no time of its own. */
const val ALL_DAY_REMINDER_MINUTE_OF_DAY = 9 * 60

/** When this todo's alarm should fire, or null when nothing should ring. */
fun Todo.reminderTriggerAt(): Long? {
    val day = deadline ?: return null
    val offset = reminderOffsetMinutes ?: return null
    if (isDeleted || state == TodoState.COMPLETE) return null
    val minuteOfDay = deadlineMinuteOfDay ?: ALL_DAY_REMINDER_MINUTE_OF_DAY
    return day + minuteOfDay * 60_000L - offset * 60_000L
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
        next = repeatInterval.nextDeadline(next, zone)
    }
    return copy(
        deadline = next,
        state = TodoState.INCOMPLETE,
        completedAt = null,
        repeatCompletedCount = completed,
        repeatMissedCount = missed
    )
}
