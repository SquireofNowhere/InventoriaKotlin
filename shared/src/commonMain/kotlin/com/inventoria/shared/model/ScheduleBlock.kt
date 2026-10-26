package com.inventoria.shared.model

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/**
 * users/$uid/schedule_blocks: a stretch of a day designated for something. It scores nothing, and
 * starts nothing unless [autoStart] says so. [dayStart] is a start-of-day timestamp and the minutes
 * are since midnight.
 */
@Serializable
data class ScheduleBlock(
    val id: String = "",
    val title: String = "",
    val kind: TaskKind = TaskKind.GRAPHITE,
    val taskTypeId: String? = null,
    val dayStart: Long = 0L,
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 60,
    val repeatWeekly: Boolean = false,
    val repeatDaily: Boolean = false,
    // The weekdays a weekly block falls on, as a Weekdays bit set; zero is just the weekday of
    // dayStart. Only read together with repeatWeekly, which stays set whenever this is.
    val repeatWeekdays: Int = 0,
    // Last day (start-of-day, inclusive) the block shows on; null is no end. Removing a repeating
    // block going forward sets this rather than deleting it, so past days keep it.
    val endDay: Long? = null,
    // Start a tracked session by itself when the block begins, and optionally end it with the
    // block. Only the phone acts on these; autoStop means nothing without autoStart.
    val autoStart: Boolean = false,
    val autoStop: Boolean = false,
    val notes: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
) {
    /** Whether this block shows on [day]: its own day, or a later (same-weekday) day when repeating. */
    fun occursOn(day: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        if (endDay != null && day > endDay) return false
        val chosen = Weekdays.clean(repeatWeekdays)
        if (repeatWeekly && !repeatDaily && chosen != 0) {
            // Chosen weekdays replace "the weekday of dayStart", so the first day only counts when it is one of them.
            return day >= dayStart && Weekdays.contains(chosen, Weekdays.isoDayOf(day, zone))
        }
        if (day == dayStart) return true
        if (day < dayStart) return false
        if (repeatDaily) return true
        if (!repeatWeekly) return false
        return dayOfWeek(dayStart, zone) == dayOfWeek(day, zone)
    }
}
