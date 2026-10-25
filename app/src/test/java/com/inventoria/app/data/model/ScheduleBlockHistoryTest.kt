package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleBlockHistoryTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis

    private val today = day(2026, 10, 25)
    private val yesterday = day(2026, 10, 24)

    private fun block(
        start: Long,
        daily: Boolean = false,
        weekly: Boolean = false,
        startMinute: Int = 6 * 60,
        endDay: Long? = null
    ) = ScheduleBlock(
        id = "b", dayStart = start, startMinuteOfDay = startMinute, endMinuteOfDay = startMinute + 60,
        repeatDaily = daily, repeatWeekly = weekly, endDay = endDay
    )

    @Test
    fun `a block shows through its end day and not after`() {
        val b = block(day(2026, 10, 1), daily = true, endDay = day(2026, 10, 10))
        assertTrue(b.occursOn(day(2026, 10, 3)))
        assertTrue(b.occursOn(day(2026, 10, 10)))
        assertFalse(b.occursOn(day(2026, 10, 11)))
    }

    @Test
    fun `a block with no end day carries on forever`() {
        assertTrue(block(day(2026, 10, 1), daily = true).occursOn(day(2030, 1, 1)))
    }

    @Test
    fun `removing a daily block before today's start keeps up to yesterday`() {
        val b = block(day(2026, 10, 1), daily = true, startMinute = 18 * 60)
        assertEquals(yesterday, b.historyEnd(today, nowMinute = 9 * 60))
    }

    @Test
    fun `removing it after today's start keeps today as well`() {
        val b = block(day(2026, 10, 1), daily = true, startMinute = 6 * 60)
        assertEquals(today, b.historyEnd(today, nowMinute = 9 * 60))
    }

    @Test
    fun `a weekly block that does not land today keeps up to yesterday`() {
        // 2026-10-25 is a Sunday; this block's weekday is the 1st's (a Thursday).
        val b = block(day(2026, 10, 1), weekly = true, startMinute = 6 * 60)
        assertEquals(yesterday, b.historyEnd(today, nowMinute = 23 * 60))
    }

    @Test
    fun `a block that has not started has no history to keep`() {
        val b = block(day(2026, 11, 1), daily = true)
        assertNull(b.historyEnd(today, nowMinute = 9 * 60))
        assertFalse(b.hasHistory(today, nowMinute = 9 * 60))
    }

    @Test
    fun `a repeating block created today but not yet started has no history`() {
        val b = block(today, daily = true, startMinute = 18 * 60)
        assertNull(b.historyEnd(today, nowMinute = 9 * 60))
        assertFalse(b.hasHistory(today, nowMinute = 9 * 60))
    }

    @Test
    fun `a one-off cannot be ended but does have history once it has happened`() {
        val b = block(yesterday)
        assertNull(b.historyEnd(today, nowMinute = 9 * 60))
        assertTrue(b.hasHistory(today, nowMinute = 9 * 60))
    }

    @Test
    fun `a block that already stops earlier has nothing further to end`() {
        val b = block(day(2026, 10, 1), daily = true, endDay = day(2026, 10, 10))
        assertNull(b.historyEnd(today, nowMinute = 9 * 60))
        assertTrue(b.hasHistory(today, nowMinute = 9 * 60))
    }
}
