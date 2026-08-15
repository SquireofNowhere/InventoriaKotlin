package com.inventoria.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A calendar day's worth of entries, most recent day first, generic over the item type so it
 * fits time-tracked segments, sessions, or todos alike -- callers only supply how to read a
 * day-start timestamp off of whatever T is. */
data class DayBucket<T>(val dayStart: Long, val items: List<T>)

fun <T> bucketByDay(items: List<T>, dayStartOf: (T) -> Long): List<DayBucket<T>> =
    items.groupBy(dayStartOf)
        .toList()
        .sortedByDescending { it.first }
        .map { (day, group) -> DayBucket(day, group) }

fun getStartOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Originally written assuming timestamps are always in the past (Task History never shows
 * anything but completed/historical data), so the "just show the weekday name" window only had a
 * lower bound. Reused for future-dated Todo deadlines too now, so the window is symmetric: within
 * 3 days either direction gets a bare weekday name, further out (past OR future) gets "dd MMM". */
fun formatCardDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
        return ""
    }
    val threeDaysAgo = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -3)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val threeDaysAhead = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 3)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return if (timestamp in threeDaysAgo..threeDaysAhead) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun getDayLabel(timestamp: Long): String {
    val dateInfo = formatCardDate(timestamp)
    if (dateInfo.isEmpty()) return "Today"
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val isTomorrow = tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    return when {
        isYesterday -> "Yesterday"
        isTomorrow -> "Tomorrow"
        else -> dateInfo
    }
}

/** Minutes-since-midnight (a Todo's optional deadline time) as "HH:mm" -- 24-hour, matching the
 * TimePickerDialog the Task tracker already opens with is24HourView = true. */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

fun formatSimpleDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
