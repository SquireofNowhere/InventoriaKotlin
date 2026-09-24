package com.inventoria.web.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Instant

private val zone get() = TimeZone.currentSystemDefault()

fun localDateTime(millis: Long): LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)

private fun Int.pad2() = toString().padStart(2, '0')

/** "14:05" */
fun formatTime(millis: Long): String = localDateTime(millis).let { "${it.hour.pad2()}:${it.minute.pad2()}" }

/** "14:05" for a minutes-since-midnight value. */
fun formatMinuteOfDay(minute: Int): String = "${(minute / 60).pad2()}:${(minute % 60).pad2()}"

/** "Wed 24 Sep" */
fun formatDay(millis: Long): String = localDateTime(millis).let { d ->
    val weekday = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    "$weekday ${d.day} $month"
}

/** "2h 05m", "12m", "45s" */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes.toInt().pad2()}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** "1 234.50" -- plain grouping, since the currency preference lives in the Android settings. */
fun formatMoney(value: Double): String {
    val cents = (value * 100).roundToLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    return "$whole.${(cents % 100).toString().padStart(2, '0')}"
}
