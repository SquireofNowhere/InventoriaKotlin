package com.inventoria.app.ui.screens.task

import com.inventoria.app.data.model.Task
import kotlin.math.roundToLong

/**
 * A task's stored [Task.score] is minute-points (Kind value x minutes x momentum), frozen when the
 * segment ended. Everything the user sees is in hour-points instead, so one hour of a +3 Kind reads
 * as +3 -- the same as ticking off one +3 Todo. Converting at read time (rather than rewriting the
 * stored scores) keeps existing history and sync data untouched.
 */
private const val MINUTES_PER_HOUR = 60.0

val Task.points: Double get() = score / MINUTES_PER_HOUR

/**
 * The part of this task's points that fall in [from, to). A task that crosses midnight is split by
 * how much of its span lies on each side, so a day's points and the lifetime total always agree.
 * A still-running task (no end time) has no frozen score yet, so it counts whole.
 */
fun Task.overlapFraction(from: Long, to: Long): Double {
    val end = endTime ?: return if (startTime < to) 1.0 else 0.0
    val span = end - startTime
    if (span <= 0L) return if (startTime in from until to) 1.0 else 0.0
    val overlap = minOf(end, to) - maxOf(startTime, from)
    return if (overlap <= 0L) 0.0 else overlap.toDouble() / span
}

fun Task.pointsWithin(from: Long, to: Long): Double = points * overlapFraction(from, to)

/** "+3", "+1.5", "-2", "0". Rounds to one decimal first so a tiny negative never prints "-0.0". */
fun formatPoints(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return when {
        tenths == 0L -> "0"
        tenths % 10L == 0L -> if (tenths > 0) "+${tenths / 10}" else "${tenths / 10}"
        else -> {
            val text = "%.1f".format(tenths / 10.0)
            if (tenths > 0) "+$text" else text
        }
    }
}

/** True when [value] would show as a positive score, so colouring agrees with the printed sign. */
fun isPositivePoints(value: Double): Boolean = (value * 10).roundToLong() >= 0
