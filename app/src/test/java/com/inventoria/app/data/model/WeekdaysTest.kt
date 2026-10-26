package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 2026-10-26 is a Monday, so the 26th..30th are Monday..Friday and the 31st/1st the weekend. */
class WeekdaysTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis

    private val monFri = Weekdays.maskOf(listOf(1, 5))

    // --- the set itself ---

    @Test
    fun `days map to bits from Monday up`() {
        assertEquals(0b0000001, Weekdays.bit(1))
        assertEquals(0b1000000, Weekdays.bit(7))
        assertEquals(0b0010001, monFri)
        assertEquals(listOf(1, 5), Weekdays.daysOf(monFri))
        assertTrue(Weekdays.contains(monFri, 5))
        assertFalse(Weekdays.contains(monFri, 2))
    }

    @Test
    fun `days outside one to seven are ignored`() {
        assertEquals(0, Weekdays.maskOf(listOf(0, 8, -1)))
        assertFalse(Weekdays.contains(0b1111111, 0))
        assertEquals(0b1111111, Weekdays.clean(0b11111111))
    }

    @Test
    fun `a set is described in words`() {
        assertEquals("", Weekdays.describe(0))
        assertEquals("Every day", Weekdays.describe(Weekdays.ALL))
        assertEquals("Weekdays", Weekdays.describe(Weekdays.WORKDAYS))
        assertEquals("Weekends", Weekdays.describe(Weekdays.WEEKEND))
        assertEquals("Mon, Wed, Fri", Weekdays.describe(Weekdays.maskOf(listOf(1, 3, 5))))
    }

    @Test
    fun `the iso day of a date`() {
        assertEquals(1, Weekdays.isoDayOf(day(2026, 10, 26)))
        assertEquals(4, Weekdays.isoDayOf(day(2026, 10, 29)))
        assertEquals(7, Weekdays.isoDayOf(day(2026, 10, 25)))
    }

    // --- a todo repeating on chosen days ---

    @Test
    fun `a weekly repeat with days goes to the next chosen day`() {
        assertEquals(day(2026, 10, 30), nextCycleDeadline(TodoRepeat.WEEKLY, monFri, day(2026, 10, 26)))
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, monFri, day(2026, 10, 30)))
        // from a day that is not itself chosen
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, monFri, day(2026, 10, 31)))
    }

    @Test
    fun `a single chosen day is the same day next week`() {
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, Weekdays.bit(1), day(2026, 10, 26)))
    }

    @Test
    fun `no chosen days is a plain week, and other intervals ignore the days`() {
        assertEquals(day(2026, 11, 2), nextCycleDeadline(TodoRepeat.WEEKLY, 0, day(2026, 10, 26)))
        assertEquals(day(2026, 10, 27), nextCycleDeadline(TodoRepeat.DAILY, monFri, day(2026, 10, 26)))
        assertEquals(day(2026, 11, 26), nextCycleDeadline(TodoRepeat.MONTHLY, monFri, day(2026, 10, 26)))
        assertEquals(day(2026, 10, 26), nextCycleDeadline(TodoRepeat.NONE, monFri, day(2026, 10, 26)))
    }

    private fun repeating(deadline: Long, mask: Int, state: TodoState = TodoState.INCOMPLETE) = Todo(
        id = "t", deadline = deadline, repeatInterval = TodoRepeat.WEEKLY, repeatWeekdays = mask, state = state,
        completedAt = if (state == TodoState.COMPLETE) 1L else null
    )

    @Test
    fun `settling a cycle moves to the next chosen day`() {
        val settled = repeating(day(2026, 10, 23), monFri, TodoState.COMPLETE).settleCycles(day(2026, 10, 26))!!
        assertEquals(day(2026, 10, 26), settled.deadline)
        assertEquals(1, settled.repeatCompletedCount)
        assertEquals(0, settled.repeatMissedCount)
    }

    @Test
    fun `several missed cycles are each counted on the chosen days`() {
        // Due Wed 21st (not itself chosen); today is Tue 27th. Cycles ended: Wed 21, Fri 23, Mon 26.
        val settled = repeating(day(2026, 10, 21), monFri).settleCycles(day(2026, 10, 27))!!
        assertEquals(3, settled.repeatMissedCount)
        assertEquals(day(2026, 10, 30), settled.deadline)
    }

    // --- a block repeating on chosen days ---

    private fun weekly(start: Long, mask: Int, endDay: Long? = null, weekly: Boolean = true, daily: Boolean = false) =
        ScheduleBlock(id = "b", dayStart = start, repeatWeekly = weekly, repeatDaily = daily, repeatWeekdays = mask, endDay = endDay)

    @Test
    fun `a block falls on its chosen weekdays from its first day`() {
        val block = weekly(day(2026, 10, 26), Weekdays.maskOf(listOf(1, 3, 5)))
        assertTrue(block.occursOn(day(2026, 10, 26)))
        assertFalse(block.occursOn(day(2026, 10, 27)))
        assertTrue(block.occursOn(day(2026, 10, 28)))
        assertTrue(block.occursOn(day(2026, 10, 30)))
        assertFalse(block.occursOn(day(2026, 10, 31)))
        assertTrue(block.occursOn(day(2026, 11, 2)))
        // never before it starts, even on a chosen weekday
        assertFalse(block.occursOn(day(2026, 10, 23)))
    }

    @Test
    fun `the first day only counts when it is a chosen weekday`() {
        // starts on a Tuesday, repeats Mon and Fri
        val block = weekly(day(2026, 10, 27), monFri)
        assertFalse(block.occursOn(day(2026, 10, 27)))
        assertTrue(block.occursOn(day(2026, 10, 30)))
        assertTrue(block.occursOn(day(2026, 11, 2)))
    }

    @Test
    fun `no chosen days keeps the old weekly rule`() {
        val block = weekly(day(2026, 10, 26), 0)
        assertTrue(block.occursOn(day(2026, 11, 2)))
        assertFalse(block.occursOn(day(2026, 10, 28)))
    }

    @Test
    fun `chosen days are ignored unless the block is weekly`() {
        val daily = weekly(day(2026, 10, 26), monFri, weekly = false, daily = true)
        assertTrue(daily.occursOn(day(2026, 10, 28)))
        val once = weekly(day(2026, 10, 26), monFri, weekly = false)
        assertTrue(once.occursOn(day(2026, 10, 26)))
        assertFalse(once.occursOn(day(2026, 10, 30)))
    }

    @Test
    fun `an end day still ends a block on chosen days`() {
        val block = weekly(day(2026, 10, 26), monFri, endDay = day(2026, 10, 30))
        assertTrue(block.occursOn(day(2026, 10, 30)))
        assertFalse(block.occursOn(day(2026, 11, 2)))
    }

    @Test
    fun `the next auto start finds the next chosen day`() {
        val block = weekly(day(2026, 10, 26), monFri).copy(autoStart = true, startMinuteOfDay = 8 * 60)
        val saturdayNoon = day(2026, 10, 31) + 12 * 60 * 60_000L
        val next = block.nextStartAfter(saturdayNoon)
        assertEquals(day(2026, 11, 2) + 8 * 60 * 60_000L, next)
    }

    @Test
    fun `a block that never repeats has no next start after its day`() {
        val block = ScheduleBlock(id = "b", dayStart = day(2026, 10, 26), autoStart = true, startMinuteOfDay = 8 * 60)
        assertNull(block.nextStartAfter(day(2026, 10, 27)))
    }
}
