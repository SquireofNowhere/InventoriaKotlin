package com.inventoria.mcp

import com.inventoria.shared.model.Task
import java.time.Instant
import java.time.ZoneId

enum class ReportGroup { DAY, TASK_TYPE, KIND, NAME }

/** One line of a time report: how long, how many points, in how many segments. */
class ReportRow(val key: String) {
    var millis = 0L
    var points = 0.0
    var segments = 0
}

/**
 * Adds up tracked time inside [from, to) -- both epoch millis -- grouped by [group].
 *
 * A segment that straddles a boundary (midnight when grouping by day, or the edge of the range) is
 * split by how much of its span lies on each side, and its frozen score is split in the same
 * proportion: the phone does this (Task.pointsWithin), so a day's total here matches the app's.
 * Points are the app's hour-points, the stored minute-points over 60. A segment still running has
 * no frozen score yet, so it adds time but no points.
 */
fun buildTimeReport(
    tasks: List<Task>,
    from: Long,
    to: Long,
    group: ReportGroup,
    now: Long,
    zone: ZoneId,
    typeName: (String?) -> String
): List<ReportRow> {
    val rows = LinkedHashMap<String, ReportRow>()

    fun add(task: Task, key: String, start: Long, end: Long, windowStart: Long, windowEnd: Long) {
        val span = end - start
        val overlap = if (span <= 0L) {
            // A zero-length segment is a point: it belongs to the window it falls in, with all of its score.
            if (start in windowStart until windowEnd) 0L else return
        } else {
            (minOf(end, windowEnd) - maxOf(start, windowStart)).takeIf { it > 0L } ?: return
        }
        val fraction = if (span <= 0L) 1.0 else overlap.toDouble() / span
        val row = rows.getOrPut(key) { ReportRow(key) }
        row.millis += overlap
        row.points += task.score / 60.0 * fraction
        row.segments += 1
    }

    for (task in tasks) {
        val start = task.startTime
        val end = if (task.isRunning) now else task.endTime ?: (start + task.duration)
        if (end < from || start >= to) continue
        when (group) {
            ReportGroup.DAY -> {
                var day = Instant.ofEpochMilli(maxOf(start, from)).atZone(zone).toLocalDate()
                val last = minOf(end, to)
                while (true) {
                    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    add(task, day.toString(), start, end, maxOf(dayStart, from), minOf(dayEnd, to))
                    if (dayEnd >= last) break
                    day = day.plusDays(1)
                }
            }
            ReportGroup.TASK_TYPE -> add(task, typeName(task.taskTypeId), start, end, from, to)
            ReportGroup.KIND -> add(task, task.kind.name, start, end, from, to)
            ReportGroup.NAME -> add(task, task.name.trim().ifEmpty { "(unnamed)" }, start, end, from, to)
        }
    }

    val list = rows.values.toList()
    return if (group == ReportGroup.DAY) list.sortedBy { it.key } else list.sortedByDescending { it.millis }
}
