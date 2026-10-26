package com.inventoria.app.data.model

/**
 * An ASAP todo's elapsed time as the row shows it, coarser the longer it has been: seconds only for
 * the first hour (it is a timer, so it should visibly move), then hours and minutes, then days and
 * hours. "45s", "14m 05s", "3h 12m", "2d 4h".
 */
fun formatAsapElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = totalSeconds % 86_400 / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}
