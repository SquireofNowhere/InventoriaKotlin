package com.inventoria.shared.model

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/**
 * users/$uid/schedule_blocks: a stretch of a day designated for something. Cosmetic -- it scores
 * nothing. [dayStart] is a start-of-day timestamp and the minutes are since midnight.
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
    val notes: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
) {
    /** Whether this block shows on [day]: its own day, or a later (same-weekday) day when repeating. */
    fun occursOn(day: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        if (day == dayStart) return true
        if (day < dayStart) return false
        if (repeatDaily) return true
        if (!repeatWeekly) return false
        return dayOfWeek(dayStart, zone) == dayOfWeek(day, zone)
    }
}
