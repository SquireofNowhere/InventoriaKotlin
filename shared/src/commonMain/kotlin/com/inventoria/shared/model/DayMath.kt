package com.inventoria.shared.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * The calendar arithmetic the Android app does with java.util.Calendar, in multiplatform form.
 * Every function takes the zone explicitly (defaulting to the device's), because "start of day"
 * and "same weekday" only mean something in one.
 */

/** Start-of-day millis of the day [millis] falls on. */
fun startOfDay(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
        .atStartOfDayIn(zone).toEpochMilliseconds()

fun dayOfWeek(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): DayOfWeek =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).dayOfWeek

/**
 * [millis] moved by a calendar [period], keeping its wall-clock time. A calendar add rather than
 * a multiple of 24h so a daylight-saving change never knocks a start-of-day value off midnight;
 * month steps clamp to the shorter month (31 Jan -> 28 Feb).
 */
fun plusCalendar(
    millis: Long,
    period: DatePeriod,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return LocalDateTime(local.date.plus(period), local.time).toInstant(zone).toEpochMilliseconds()
}
