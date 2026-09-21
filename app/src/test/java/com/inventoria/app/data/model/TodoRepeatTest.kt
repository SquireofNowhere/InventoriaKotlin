package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TodoRepeatTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis

    private fun todo(
        deadline: Long?,
        repeat: TodoRepeat,
        state: TodoState = TodoState.INCOMPLETE,
        completed: Int = 0,
        missed: Int = 0
    ) = Todo(
        id = "t",
        deadline = deadline,
        repeatInterval = repeat,
        state = state,
        completedAt = if (state == TodoState.COMPLETE) 1L else null,
        repeatCompletedCount = completed,
        repeatMissedCount = missed
    )

    @Test
    fun `a cycle still running is left alone`() {
        val today = day(2026, 9, 21)
        assertNull(todo(today, TodoRepeat.DAILY).settleCycles(today))
        assertNull(todo(day(2026, 9, 22), TodoRepeat.DAILY).settleCycles(today))
    }

    @Test
    fun `a one-off never settles`() {
        assertNull(todo(day(2026, 9, 1), TodoRepeat.NONE).settleCycles(day(2026, 9, 21)))
        assertNull(todo(null, TodoRepeat.DAILY).settleCycles(day(2026, 9, 21)))
    }

    @Test
    fun `a completed cycle counts as completed and starts over incomplete`() {
        val settled = todo(day(2026, 9, 20), TodoRepeat.DAILY, TodoState.COMPLETE)
            .settleCycles(day(2026, 9, 21))!!
        assertEquals(1, settled.repeatCompletedCount)
        assertEquals(0, settled.repeatMissedCount)
        assertEquals(day(2026, 9, 21), settled.deadline)
        assertEquals(TodoState.INCOMPLETE, settled.state)
        assertNull(settled.completedAt)
    }

    @Test
    fun `an unfinished cycle counts as missed, in progress included`() {
        listOf(TodoState.INCOMPLETE, TodoState.IN_PROGRESS).forEach { state ->
            val settled = todo(day(2026, 9, 20), TodoRepeat.DAILY, state, completed = 4, missed = 2)
                .settleCycles(day(2026, 9, 21))!!
            assertEquals(4, settled.repeatCompletedCount)
            assertEquals(3, settled.repeatMissedCount)
        }
    }

    @Test
    fun `cycles skipped while away count as missed after the first is judged`() {
        // Completed on the 14th, app not opened until the 18th: 14th completed, 15-17 missed.
        val settled = todo(day(2026, 9, 14), TodoRepeat.DAILY, TodoState.COMPLETE)
            .settleCycles(day(2026, 9, 18))!!
        assertEquals(1, settled.repeatCompletedCount)
        assertEquals(3, settled.repeatMissedCount)
        assertEquals(day(2026, 9, 18), settled.deadline)
    }

    @Test
    fun `weekly moves a week at a time and skips whole weeks`() {
        val settled = todo(day(2026, 9, 1), TodoRepeat.WEEKLY)
            .settleCycles(day(2026, 9, 21))!!
        // Due the 1st, 8th and 15th all ended before the 21st; next live one is the 22nd.
        assertEquals(3, settled.repeatMissedCount)
        assertEquals(day(2026, 9, 22), settled.deadline)
    }

    @Test
    fun `monthly clamps to a shorter month`() {
        assertEquals(day(2027, 2, 28), TodoRepeat.MONTHLY.nextDeadline(day(2027, 1, 31)))
    }
}
