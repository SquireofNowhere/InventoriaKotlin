package com.inventoria.shared.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber

/**
 * A set of weekdays as one small number, for a repeat that falls on some days of the week rather
 * than every one: bit 0 is Monday, bit 6 is Sunday. Zero means no choice was made, and every user
 * of it reads that as "the weekday the thing already falls on", so a todo or block from before this
 * existed keeps repeating exactly as it did.
 *
 * Days are ISO numbers, 1 = Monday ... 7 = Sunday, the same numbering a reminder schedule's
 * `w15@0800` uses. The Android app keeps an identical copy.
 */
object Weekdays {
    const val ALL = 0b1111111
    const val WORKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    private val NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun bit(isoDay: Int): Int = 1 shl (isoDay - 1)

    fun contains(mask: Int, isoDay: Int): Boolean = isoDay in 1..7 && (mask and bit(isoDay)) != 0

    fun maskOf(days: Collection<Int>): Int = days.filter { it in 1..7 }.fold(0) { mask, day -> mask or bit(day) }

    fun daysOf(mask: Int): List<Int> = (1..7).filter { contains(mask, it) }

    /** Only the seven bits that mean something. */
    fun clean(mask: Int): Int = mask and ALL

    /** The ISO day (1 = Monday) of a start-of-day [millis] in [zone]. */
    fun isoDayOf(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Int =
        dayOfWeek(millis, zone).isoDayNumber

    fun name(isoDay: Int): String = NAMES[isoDay - 1]

    /** "Every day", "Weekdays", "Weekends", or the days by name: "Mon, Wed, Fri". */
    fun describe(mask: Int): String = when (val m = clean(mask)) {
        0 -> ""
        ALL -> "Every day"
        WORKDAYS -> "Weekdays"
        WEEKEND -> "Weekends"
        else -> daysOf(m).joinToString(", ") { name(it) }
    }
}
