package com.inventoria.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodoTest {
    private val zone = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds()

    @Test
    fun monthlyClampsToTheShorterMonth() {
        assertEquals(day(2026, 2, 28), TodoRepeat.MONTHLY.nextDeadline(day(2026, 1, 31), zone))
        assertEquals(day(2026, 3, 28), TodoRepeat.MONTHLY.nextDeadline(day(2026, 2, 28), zone))
    }

    @Test
    fun settlingJudgesTheFirstCycleAndMissesTheRest() {
        val todo = Todo(
            deadline = day(2026, 9, 1),
            repeatInterval = TodoRepeat.DAILY,
            state = TodoState.COMPLETE,
            completedAt = 123L
        )
        val settled = todo.settleCycles(day(2026, 9, 4), zone)!!
        assertEquals(day(2026, 9, 4), settled.deadline)
        assertEquals(TodoState.INCOMPLETE, settled.state)
        assertNull(settled.completedAt)
        assertEquals(1, settled.repeatCompletedCount)
        assertEquals(2, settled.repeatMissedCount)
    }

    @Test
    fun nothingToSettleBeforeTheDeadlineDayEnds() {
        val todo = Todo(deadline = day(2026, 9, 4), repeatInterval = TodoRepeat.WEEKLY)
        assertNull(todo.settleCycles(day(2026, 9, 4), zone))
        assertNull(todo.copy(repeatInterval = TodoRepeat.NONE).settleCycles(day(2026, 10, 1), zone))
    }

    @Test
    fun allDayReminderRingsAtNineMinusTheOffset() {
        val todo = Todo(deadline = day(2026, 9, 4), reminderOffsetMinutes = 60)
        assertEquals(day(2026, 9, 4) + 8 * 3_600_000L, todo.reminderTriggerAt())
        assertNull(todo.copy(state = TodoState.COMPLETE).reminderTriggerAt())
    }
}
