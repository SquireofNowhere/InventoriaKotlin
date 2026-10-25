package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutoStartPlanTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, h, min, 0)
    }.timeInMillis

    private fun daily(minute: Int = 8 * 60) = ReminderSchedule(ScheduleKind.DAILY, minute)

    // 10 October 2026 is a Saturday.
    private fun todo(
        deadlineDay: Long? = at(2026, 10, 10),
        minuteOfDay: Int? = 17 * 60,
        plan: AutoStartPlan = AutoStartPlan.NONE,
        state: TodoState = TodoState.INCOMPLETE
    ) = Todo(id = "t", deadline = deadlineDay, deadlineMinuteOfDay = minuteOfDay, state = state).withAutoStart(plan)

    // --- the stored text -------------------------------------------------------------------

    @Test
    fun `a plan survives encoding and parsing`() {
        val plan = AutoStartPlan.of(15, listOf(ReminderSchedule(ScheduleKind.WEEKLY, 8 * 60, weekdays = setOf(1, 5))), 45)
        assertEquals("before=15 on=w15@0800 run=45", plan.encode())
        assertEquals(plan, AutoStartPlan.parse(plan.encode()))
    }

    @Test
    fun `parts that cannot be read are dropped instead of failing`() {
        val plan = AutoStartPlan.parse("before=zz on=d@0800,nope run=0 later=1")
        assertNull(plan.beforeDueMinutes)
        assertEquals(listOf(daily()), plan.schedules)
        assertNull(plan.runMinutes)
        assertTrue(AutoStartPlan.parse("").isEmpty)
    }

    @Test
    fun `a repeat on the day is dropped from a start`() {
        val bursty = ReminderSchedule(ScheduleKind.DAILY, 9 * 60, burstEveryMinutes = 5, burstUntilMinute = 17 * 60)
        assertEquals(0, AutoStartPlan.of(null, listOf(bursty), null).schedules.single().burstEveryMinutes)
    }

    @Test
    fun `a length with nothing to start it is not a plan`() {
        assertTrue(AutoStartPlan.of(null, emptyList(), 45).isEmpty)
        assertEquals("Not automatic", AutoStartPlan.of(null, emptyList(), 45).describe())
    }

    @Test
    fun `the started session runs under the length as its plan and stop`() {
        val session = AutoStartPlan.of(0, emptyList(), 45).sessionPlan()
        assertEquals(45, session.goalMinutes)
        assertTrue(session.stopsAtGoal)
        assertTrue(AutoStartPlan.of(0, emptyList(), null).sessionPlan().isEmpty)
    }

    // --- when it starts --------------------------------------------------------------------

    @Test
    fun `it starts a set time before the due moment`() {
        val t = todo(plan = AutoStartPlan.of(30, emptyList(), null))
        assertEquals(at(2026, 10, 10, 16, 30), t.nextAutoStartAfter(at(2026, 10, 10, 9)))
        assertNull(t.nextAutoStartAfter(at(2026, 10, 10, 16, 30)))
    }

    @Test
    fun `an all-day todo counts from nine in the morning`() {
        val t = todo(minuteOfDay = null, plan = AutoStartPlan.of(60, emptyList(), null))
        assertEquals(at(2026, 10, 10, 8), t.nextAutoStartAfter(at(2026, 10, 9)))
    }

    @Test
    fun `a schedule starts it with no deadline at all`() {
        val t = todo(deadlineDay = null, minuteOfDay = null, plan = AutoStartPlan.of(30, listOf(daily()), 25))
        // The part that counts from a deadline is gone; the schedule and the length are not.
        assertNull(t.autoStartPlan().beforeDueMinutes)
        assertEquals(listOf(daily()), t.autoStartPlan().schedules)
        assertEquals(25, t.autoStartPlan().runMinutes)
        assertEquals(at(2026, 10, 10, 8), t.nextAutoStartAfter(at(2026, 10, 10, 7)))
    }

    @Test
    fun `the soonest of the deadline start and the schedules wins`() {
        val t = todo(plan = AutoStartPlan.of(30, listOf(daily()), null))
        assertEquals(at(2026, 10, 10, 8), t.nextAutoStartAfter(at(2026, 10, 10, 7)))
        assertEquals(at(2026, 10, 10, 16, 30), t.nextAutoStartAfter(at(2026, 10, 10, 9)))
        assertEquals(at(2026, 10, 11, 8), t.nextAutoStartAfter(at(2026, 10, 10, 18)))
    }

    @Test
    fun `a finished or deleted todo does not start`() {
        val plan = AutoStartPlan.of(0, listOf(daily()), null)
        assertNull(todo(plan = plan, state = TodoState.COMPLETE).nextAutoStartAfter(at(2026, 10, 10, 7)))
        assertNull(todo(plan = plan).copy(isDeleted = true).nextAutoStartAfter(at(2026, 10, 10, 7)))
        assertNull(todo().nextAutoStartAfter(at(2026, 10, 10, 7)))
    }

    @Test
    fun `the plan reads back in words`() {
        assertEquals("At the due time", AutoStartPlan.of(0, emptyList(), null).describe())
        assertEquals(
            "15 min before it's due · Every day at 08:00 · then stops after 1 hr",
            AutoStartPlan.of(15, listOf(daily()), 60).describe()
        )
    }

    // --- schedule blocks -------------------------------------------------------------------

    private fun block(
        autoStart: Boolean = true,
        repeatDaily: Boolean = false,
        repeatWeekly: Boolean = false,
        endDay: Long? = null
    ) = ScheduleBlock(
        id = "b",
        title = "Deep work",
        dayStart = at(2026, 10, 10),
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 11 * 60,
        repeatDaily = repeatDaily,
        repeatWeekly = repeatWeekly,
        endDay = endDay,
        autoStart = autoStart
    )

    @Test
    fun `a one-off block starts once`() {
        assertEquals(at(2026, 10, 10, 9), block().nextStartAfter(at(2026, 10, 10, 8)))
        assertNull(block().nextStartAfter(at(2026, 10, 10, 9)))
        assertEquals(at(2026, 10, 10, 9), block().nextStartAfter(at(2026, 10, 1)))
    }

    @Test
    fun `a daily block starts every day`() {
        val b = block(repeatDaily = true)
        assertEquals(at(2026, 10, 10, 9), b.nextStartAfter(at(2026, 10, 10, 8)))
        assertEquals(at(2026, 10, 11, 9), b.nextStartAfter(at(2026, 10, 10, 9)))
    }

    @Test
    fun `a weekly block starts on its weekday`() {
        assertEquals(at(2026, 10, 17, 9), block(repeatWeekly = true).nextStartAfter(at(2026, 10, 10, 10)))
    }

    @Test
    fun `a block that is off, ended or deleted does not start`() {
        assertNull(block(autoStart = false, repeatDaily = true).nextStartAfter(at(2026, 10, 10, 8)))
        assertNull(block(repeatDaily = true, endDay = at(2026, 10, 10)).nextStartAfter(at(2026, 10, 10, 10)))
        assertNull(block(repeatDaily = true).copy(isDeleted = true).nextStartAfter(at(2026, 10, 10, 8)))
    }
}
