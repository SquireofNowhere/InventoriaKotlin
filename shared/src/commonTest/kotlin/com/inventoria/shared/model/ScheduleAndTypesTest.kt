package com.inventoria.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleAndTypesTest {
    private val zone = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds()

    @Test
    fun weeklyBlockOnlyRecursOnItsWeekday() {
        val block = ScheduleBlock(dayStart = day(2026, 9, 7), repeatWeekly = true) // a Monday
        assertTrue(block.occursOn(day(2026, 9, 14), zone))
        assertFalse(block.occursOn(day(2026, 9, 15), zone))
        assertFalse(block.occursOn(day(2026, 8, 31), zone))
    }

    @Test
    fun averageLabelRoundsToOneDecimalWithSign() {
        fun label(avg: Double?) = TaskTypeStats("t", "T", 1, avg, null, 0).averageLabel
        assertEquals("+0.5", label(0.5))
        assertEquals("-1.3", label(-1.333))
        assertEquals("0.0", label(0.04))
        assertEquals("--", label(null))
    }

    @Test
    fun modalTypeTiesGoToARealType() {
        val tasks = listOf(
            Task(id = "a", taskTypeId = null, startTime = 3),
            Task(id = "b", taskTypeId = "type_eating", startTime = 1)
        )
        assertEquals("type_eating", modalTypeIdFor(tasks))
    }

    @Test
    fun defaultTypeIdsAreStable() {
        assertEquals("type_screen_time", defaultTaskTypeId("Screen Time"))
        assertEquals("type_self_care", defaultTaskTypeId("Self-Care"))
    }
}
