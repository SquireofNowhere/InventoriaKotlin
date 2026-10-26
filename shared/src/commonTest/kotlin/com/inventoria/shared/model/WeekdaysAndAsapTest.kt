package com.inventoria.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2026-10-26 is a Monday, so the 26th..30th are Monday..Friday. */
class WeekdaysAndAsapTest {
    private val zone = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds()
    private val monFri = Weekdays.maskOf(listOf(1, 5))

    @Test
    fun `a set of days is described in words`() {
        assertEquals("", Weekdays.describe(0))
        assertEquals("Every day", Weekdays.describe(Weekdays.ALL))
        assertEquals("Weekdays", Weekdays.describe(Weekdays.WORKDAYS))
        assertEquals("Weekends", Weekdays.describe(Weekdays.WEEKEND))
        assertEquals("Mon, Fri", Weekdays.describe(monFri))
    }

    @Test
    fun `the iso day of a date`() {
        assertEquals(1, Weekdays.isoDayOf(day(2026, 10, 26), zone))
        assertEquals(7, Weekdays.isoDayOf(day(2026, 10, 25), zone))
    }

    @Test
    fun `a weekly repeat goes to the next chosen day, and plain weekly to a week on`() {
        assertEquals(day(2026, 10, 30), nextCycleDeadline(TodoRepeat.WEEKLY, monFri, day(2026, 10, 26), zone))
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, monFri, day(2026, 10, 30), zone))
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, 0, day(2026, 10, 26), zone))
        assertEquals(day(2026, 10, 27), nextCycleDeadline(TodoRepeat.DAILY, monFri, day(2026, 10, 26), zone))
    }

    @Test
    fun `settling counts each missed cycle on the chosen days`() {
        val todo = Todo(
            id = "t", deadline = day(2026, 10, 21), repeatInterval = TodoRepeat.WEEKLY, repeatWeekdays = monFri
        )
        val settled = todo.settleCycles(day(2026, 10, 27), zone)!!
        assertEquals(3, settled.repeatMissedCount) // Wed 21, Fri 23, Mon 26
        assertEquals(day(2026, 10, 30), settled.deadline)
    }

    @Test
    fun `a block falls on its chosen weekdays only`() {
        val block = ScheduleBlock(
            id = "b", dayStart = day(2026, 10, 26), repeatWeekly = true, repeatWeekdays = Weekdays.maskOf(listOf(1, 3, 5))
        )
        assertTrue(block.occursOn(day(2026, 10, 26), zone))
        assertFalse(block.occursOn(day(2026, 10, 27), zone))
        assertTrue(block.occursOn(day(2026, 10, 28), zone))
        assertFalse(block.occursOn(day(2026, 10, 23), zone))
        assertTrue(block.occursOn(day(2026, 11, 2), zone))
    }

    @Test
    fun `a block starting on a day it does not repeat on skips it`() {
        val block = ScheduleBlock(id = "b", dayStart = day(2026, 10, 27), repeatWeekly = true, repeatWeekdays = monFri)
        assertFalse(block.occursOn(day(2026, 10, 27), zone))
        assertTrue(block.occursOn(day(2026, 10, 30), zone))
    }

    @Test
    fun `no chosen days, or not weekly, keeps the old rules`() {
        val plain = ScheduleBlock(id = "b", dayStart = day(2026, 10, 26), repeatWeekly = true)
        assertTrue(plain.occursOn(day(2026, 11, 2), zone))
        assertFalse(plain.occursOn(day(2026, 10, 28), zone))
        val daily = ScheduleBlock(id = "b", dayStart = day(2026, 10, 26), repeatDaily = true, repeatWeekdays = monFri)
        assertTrue(daily.occursOn(day(2026, 10, 28), zone))
        val ended = ScheduleBlock(
            id = "b", dayStart = day(2026, 10, 26), repeatWeekly = true, repeatWeekdays = monFri, endDay = day(2026, 10, 30)
        )
        assertFalse(ended.occursOn(day(2026, 11, 2), zone))
    }

    @Test
    fun `a todo is ASAP while open and its time freezes when done`() {
        val open = Todo(id = "a", asapSince = 1_000L)
        assertTrue(open.isAsap)
        assertEquals(4_000L, open.asapElapsedMillis(5_000L))
        val done = open.copy(state = TodoState.COMPLETE, completedAt = 3_000L)
        assertFalse(done.isAsap)
        assertEquals(2_000L, done.asapElapsedMillis(99_000L))
        assertNull(Todo(id = "b").asapElapsedMillis(5_000L))
        assertEquals(0L, Todo(id = "c", asapSince = 9_000L).asapElapsedMillis(5_000L))
    }
}
