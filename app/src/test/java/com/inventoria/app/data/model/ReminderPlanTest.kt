package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderPlanTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, h, min, 0)
    }.timeInMillis

    private fun todo(
        deadlineDay: Long? = at(2026, 10, 10),
        minuteOfDay: Int? = 17 * 60,
        plan: ReminderPlan = ReminderPlan.NONE,
        state: TodoState = TodoState.INCOMPLETE
    ) = Todo(
        id = "t",
        deadline = deadlineDay,
        deadlineMinuteOfDay = minuteOfDay,
        state = state
    ).withReminders(plan)

    // --- the stored text -------------------------------------------------------------------

    @Test
    fun `a plan survives encoding and parsing`() {
        val plan = ReminderPlan.of(listOf(0, 240, 1440), ReminderSpan(2, ReminderUnit.HOUR))
        assertEquals("before=1d,4h,0m every=2h", plan.encode())
        assertEquals(plan, ReminderPlan.parse(plan.encode()))
    }

    @Test
    fun `parts that cannot be read are dropped instead of failing`() {
        val plan = ReminderPlan.parse("before=4h,zz,3mo every=banana later=1")
        assertEquals(listOf(240), plan.leadMinutes)
        assertNull(plan.every)
        assertTrue(ReminderPlan.parse("").isEmpty)
        assertTrue(ReminderPlan.parse("   ").isEmpty)
    }

    @Test
    fun `a repeat tighter than five minutes is lifted to five`() {
        assertEquals(ReminderSpan(5, ReminderUnit.MINUTE), ReminderPlan.parse("every=1m").every)
        assertEquals(ReminderSpan(5, ReminderUnit.MINUTE), ReminderPlan.of(emptyList(), ReminderSpan(2, ReminderUnit.MINUTE)).every)
        assertEquals(ReminderSpan(10, ReminderUnit.MINUTE), ReminderPlan.parse("every=10m").every)
    }

    @Test
    fun `lead times are de-duplicated and kept in range`() {
        val plan = ReminderPlan.of(listOf(60, 60, -5, 120, ReminderPlan.MAX_LEAD_MINUTES + 1), null)
        assertEquals(listOf(120, 60), plan.leadMinutes)
    }

    @Test
    fun `a todo from before plans existed rings for its single offset`() {
        val legacy = Todo(id = "t", deadline = at(2026, 10, 10), reminderOffsetMinutes = 60)
        assertEquals(ReminderPlan.of(listOf(60), null), legacy.reminders())
        assertTrue(legacy.hasReminder)
        assertTrue(!Todo(id = "t", deadline = at(2026, 10, 10)).hasReminder)
    }

    @Test
    fun `setting a plan mirrors the closest lead and clears with the deadline`() {
        val plan = ReminderPlan.of(listOf(60, 360), ReminderSpan(1, ReminderUnit.DAY))
        val withDate = todo(plan = plan)
        assertEquals(60, withDate.reminderOffsetMinutes)
        assertEquals(plan, withDate.reminders())

        val noDate = todo(deadlineDay = null, plan = plan)
        assertNull(noDate.reminderOffsetMinutes)
        assertTrue(!noDate.hasReminder)
    }

    // --- lead times ------------------------------------------------------------------------

    @Test
    fun `several lead times ring one after another, then stop`() {
        val t = todo(plan = ReminderPlan.of(listOf(360, 300, 240), null))
        assertEquals(at(2026, 10, 10, 11), t.nextReminderAfter(at(2026, 10, 10, 10)))
        assertEquals(at(2026, 10, 10, 12), t.nextReminderAfter(at(2026, 10, 10, 11)))
        assertEquals(at(2026, 10, 10, 13), t.nextReminderAfter(at(2026, 10, 10, 12, 30)))
        assertNull(t.nextReminderAfter(at(2026, 10, 10, 13)))
    }

    @Test
    fun `an all-day deadline counts from nine in the morning`() {
        val t = todo(minuteOfDay = null, plan = ReminderPlan.of(listOf(60), null))
        assertEquals(at(2026, 10, 10, 8), t.nextReminderAfter(at(2026, 10, 1)))
    }

    @Test
    fun `nothing rings for a finished, deleted or undated todo`() {
        val plan = ReminderPlan.of(listOf(0), ReminderSpan(1, ReminderUnit.HOUR))
        val now = at(2026, 10, 1)
        assertNull(todo(plan = plan, state = TodoState.COMPLETE).nextReminderAfter(now))
        assertNull(todo(plan = plan).copy(isDeleted = true).nextReminderAfter(now))
        assertNull(todo(deadlineDay = null, plan = plan).nextReminderAfter(now))
        assertNull(todo().nextReminderAfter(now))
    }

    // --- repeating until the deadline --------------------------------------------------------

    @Test
    fun `every two hours is counted back from the due time and ends with it`() {
        val t = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(2, ReminderUnit.HOUR)))
        assertEquals(at(2026, 10, 10, 11), t.nextReminderAfter(at(2026, 10, 10, 10, 30)))
        assertEquals(at(2026, 10, 10, 13), t.nextReminderAfter(at(2026, 10, 10, 11)))
        assertEquals(at(2026, 10, 10, 17), t.nextReminderAfter(at(2026, 10, 10, 15)))
        assertNull(t.nextReminderAfter(at(2026, 10, 10, 17)))
    }

    @Test
    fun `every day rings at the due time of day`() {
        val t = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.DAY)))
        assertEquals(at(2026, 10, 6, 17), t.nextReminderAfter(at(2026, 10, 6, 12)))
        assertEquals(at(2026, 10, 7, 17), t.nextReminderAfter(at(2026, 10, 6, 17)))
        assertEquals(at(2026, 10, 10, 17), t.nextReminderAfter(at(2026, 10, 9, 17, 1)))
    }

    @Test
    fun `every week lands on the deadline's weekday`() {
        val t = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.WEEK)))
        assertEquals(at(2026, 10, 3, 17), t.nextReminderAfter(at(2026, 9, 28)))
        assertEquals(at(2026, 10, 10, 17), t.nextReminderAfter(at(2026, 10, 3, 17)))
    }

    @Test
    fun `every month clamps to a shorter month without drifting`() {
        val t = todo(
            deadlineDay = at(2026, 3, 31),
            minuteOfDay = 9 * 60,
            plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.MONTH))
        )
        assertEquals(at(2026, 2, 28, 9), t.nextReminderAfter(at(2026, 2, 1)))
        assertEquals(at(2026, 1, 31, 9), t.nextReminderAfter(at(2026, 1, 1)))
        assertEquals(at(2026, 3, 31, 9), t.nextReminderAfter(at(2026, 2, 28, 9)))
    }

    @Test
    fun `a deadline far away does not walk every step to find the next one`() {
        val t = todo(
            deadlineDay = at(2040, 1, 1),
            plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.DAY))
        )
        assertEquals(at(2026, 10, 6, 17), t.nextReminderAfter(at(2026, 10, 6, 12)))
    }

    @Test
    fun `the soonest of the lead times and the repeat wins`() {
        val t = todo(plan = ReminderPlan.of(listOf(60), ReminderSpan(1, ReminderUnit.DAY)))
        // Lead: 16:00 on the 10th. Repeat: 17:00 on the 9th, earlier than the lead.
        assertEquals(at(2026, 10, 9, 17), t.nextReminderAfter(at(2026, 10, 9, 12)))
        // With repeats off the lead is the only candidate.
        assertEquals(at(2026, 10, 10, 16), t.nextReminderAfter(at(2026, 10, 9, 12), includeRepeats = false))
    }

    @Test
    fun `the wording says what the plan does`() {
        assertEquals("No reminders", ReminderPlan.NONE.describe())
        assertEquals("At due time", ReminderPlan.AT_DUE.describe())
        assertEquals(
            "At due time, 6 hr, 4 hr before · every 2 hours until due",
            ReminderPlan.of(listOf(0, 240, 360), ReminderSpan(2, ReminderUnit.HOUR)).describe()
        )
        assertEquals("every week until due", ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.WEEK)).describe())
        assertEquals("in 3 hr 20 min", ReminderPlan.remainingLabel(200 * 60_000L))
        assertEquals("in 2 days", ReminderPlan.remainingLabel(49 * 3_600_000L))
    }
}
