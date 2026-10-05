package com.inventoria.mcp

import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskKind
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReportsTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(day).atTime(hour, minute).atZone(utc).toInstant().toEpochMilli()

    private fun segment(start: Long, end: Long, score: Int = 0, kind: TaskKind = TaskKind.PEACOCK, name: String = "Work", type: String? = null) =
        Task(id = "t$start", groupId = "g$start", name = name, kind = kind, taskTypeId = type, startTime = start, endTime = end, duration = end - start, score = score)

    private fun report(tasks: List<Task>, from: String, to: String, group: ReportGroup, now: Long = at("2030-01-01", 0)) =
        buildTimeReport(tasks, at(from, 0), at(to, 0), group, now, utc) { it ?: "(no type)" }

    @Test
    fun aSegmentCrossingMidnightIsSplitByTimeAndPoints() {
        // 22:00 to 02:00 is four hours, half each side, carrying 240 minute-points (+4 hour-points).
        val rows = report(listOf(segment(at("2026-10-01", 22), at("2026-10-02", 2), score = 240)), "2026-10-01", "2026-10-03", ReportGroup.DAY)
        assertEquals(listOf("2026-10-01", "2026-10-02"), rows.map { it.key })
        assertEquals(120L, rows[0].millis / 60_000)
        assertEquals(120L, rows[1].millis / 60_000)
        assertEquals(2.0, rows[0].points, 1e-9)
        assertEquals(2.0, rows[1].points, 1e-9)
    }

    @Test
    fun theRangeEdgeClipsASegment() {
        // Only the hour inside 2026-10-02 counts.
        val rows = report(listOf(segment(at("2026-10-01", 23), at("2026-10-02", 1), score = 120)), "2026-10-02", "2026-10-03", ReportGroup.KIND)
        assertEquals(60L, rows.single().millis / 60_000)
        assertEquals(1.0, rows.single().points, 1e-9)
    }

    @Test
    fun segmentsOutsideTheRangeAreIgnored() {
        assertEquals(0, report(listOf(segment(at("2026-09-01", 9), at("2026-09-01", 10))), "2026-10-01", "2026-10-08", ReportGroup.NAME).size)
    }

    @Test
    fun groupingSumsAndSortsBiggestFirst() {
        val rows = report(
            listOf(
                segment(at("2026-10-01", 9), at("2026-10-01", 10), name = "Email"),
                segment(at("2026-10-01", 11), at("2026-10-01", 14), name = "Deep work"),
                segment(at("2026-10-01", 15), at("2026-10-01", 16), name = "Email")
            ),
            "2026-10-01", "2026-10-02", ReportGroup.NAME
        )
        assertEquals(listOf("Deep work", "Email"), rows.map { it.key })
        assertEquals(120L, rows[1].millis / 60_000)
        assertEquals(2, rows[1].segments)
    }

    @Test
    fun taskTypeGroupingNamesUntypedSegments() {
        val rows = report(listOf(segment(at("2026-10-01", 9), at("2026-10-01", 10))), "2026-10-01", "2026-10-02", ReportGroup.TASK_TYPE)
        assertEquals("(no type)", rows.single().key)
    }

    @Test
    fun aRunningSegmentCountsUpToNowWithNoPoints() {
        val running = Task(id = "r", groupId = "g", name = "Live", startTime = at("2026-10-01", 9), isRunning = true, score = 0)
        val rows = report(listOf(running), "2026-10-01", "2026-10-02", ReportGroup.KIND, now = at("2026-10-01", 10, 30))
        assertEquals(90L, rows.single().millis / 60_000)
        assertEquals(0.0, rows.single().points, 1e-9)
    }

    @Test
    fun sniffsImagesByTheirBytesNotTheirName() {
        assertEquals("image/jpeg", sniffImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))?.contentType)
        assertEquals("image/png", sniffImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0))?.contentType)
        assertEquals("image/gif", sniffImage("GIF89a".toByteArray())?.contentType)
        assertEquals("image/webp", sniffImage("RIFF....WEBPVP8 ".toByteArray())?.contentType)
        assertNull(sniffImage("RIFF....WAVEfmt ".toByteArray()))
        assertNull(sniffImage("#!/bin/sh\necho hi".toByteArray()))
        assertNull(sniffImage(ByteArray(0)))
        assertNotNull(sniffImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }
}
