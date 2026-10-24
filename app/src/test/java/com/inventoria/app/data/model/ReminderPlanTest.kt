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

    // --- calendar schedules ----------------------------------------------------------------
    // 10 October 2026 is a Saturday, so the 12th is a Monday and the 16th a Friday.

    private fun daily(minute: Int = 8 * 60) = ReminderSchedule(ScheduleKind.DAILY, minute)
    private fun weekly(vararg days: Int, minute: Int = 8 * 60) =
        ReminderSchedule(ScheduleKind.WEEKLY, minute, weekdays = days.toSet())
    private fun monthly(day: Int, minute: Int = 9 * 60, every: Int = 0, until: Int = minute) =
        ReminderSchedule(ScheduleKind.MONTHLY, minute, dayOfMonth = day, burstEveryMinutes = every, burstUntilMinute = until)
    private fun once(date: Int, minute: Int = 10 * 60) = ReminderSchedule(ScheduleKind.ONCE, minute, date = date)

    @Test
    fun `schedules survive encoding and parsing next to the other rules`() {
        val plan = ReminderPlan.of(
            listOf(0, 240),
            ReminderSpan(2, ReminderUnit.HOUR),
            listOf(daily(), weekly(1, 5), monthly(15, every = 5, until = 17 * 60), once(20261103))
        )
        assertEquals("before=4h,0m every=2h on=d@0800,w15@0800,m15@0900~5-1700,o20261103@1000", plan.encode())
        assertEquals(plan, ReminderPlan.parse(plan.encode()))
    }

    @Test
    fun `a plan with only schedules is not empty and needs no deadline`() {
        val plan = ReminderPlan.of(emptyList(), null, listOf(daily()))
        assertTrue(!plan.isEmpty)
        assertTrue(!plan.needsDeadline)
        assertTrue(ReminderPlan.of(listOf(60), null).needsDeadline)
    }

    @Test
    fun `schedules that cannot ring are dropped`() {
        val plan = ReminderPlan.parse("on=d@0800,zz,w9@0800,m40@0800,o20260231@0800,d@2561,m31@0800")
        assertEquals(listOf(daily(), monthly(31, minute = 8 * 60)), plan.schedules)
    }

    @Test
    fun `a repeat on the day is lifted to five minutes and needs a later end`() {
        val fast = ReminderSchedule(ScheduleKind.DAILY, 9 * 60, burstEveryMinutes = 1, burstUntilMinute = 10 * 60).normalised()!!
        assertEquals(5, fast.burstEveryMinutes)
        assertEquals(13, fast.ringsPerDay)
        val noRoom = ReminderSchedule(ScheduleKind.DAILY, 9 * 60, burstEveryMinutes = 10, burstUntilMinute = 9 * 60).normalised()!!
        assertEquals(0, noRoom.burstEveryMinutes)
        assertEquals(1, noRoom.ringsPerDay)
    }

    @Test
    fun `a plan keeps at most twenty schedules, each once`() {
        val many = (0 until 30).map { daily(it) } + daily(0)
        assertEquals(ReminderPlan.MAX_SCHEDULES, ReminderPlan.of(emptyList(), null, many).schedules.size)
    }

    @Test
    fun `a daily schedule rings at its time every day`() {
        assertEquals(at(2026, 10, 10, 8), daily().nextAfter(at(2026, 10, 10, 7)))
        assertEquals(at(2026, 10, 11, 8), daily().nextAfter(at(2026, 10, 10, 8)))
        assertEquals(at(2026, 10, 11, 8), daily().nextAfter(at(2026, 10, 10, 20)))
    }

    @Test
    fun `a weekly schedule rings on the chosen weekdays only`() {
        val monAndFri = weekly(1, 5)
        assertEquals(at(2026, 10, 12, 8), monAndFri.nextAfter(at(2026, 10, 10, 12)))
        assertEquals(at(2026, 10, 16, 8), monAndFri.nextAfter(at(2026, 10, 12, 8)))
        assertEquals(at(2026, 10, 19, 8), monAndFri.nextAfter(at(2026, 10, 16, 8)))
    }

    @Test
    fun `a monthly schedule rings on its day and clamps to short months`() {
        assertEquals(at(2026, 10, 15, 9), monthly(15).nextAfter(at(2026, 10, 1)))
        assertEquals(at(2026, 11, 15, 9), monthly(15).nextAfter(at(2026, 10, 15, 9)))
        assertEquals(at(2026, 2, 28, 9), monthly(31).nextAfter(at(2026, 2, 1)))
        assertEquals(at(2026, 3, 31, 9), monthly(31).nextAfter(at(2026, 2, 28, 9)))
    }

    @Test
    fun `a one-off rings once and then never again`() {
        val s = once(20261103)
        assertEquals(at(2026, 11, 3, 10), s.nextAfter(at(2026, 10, 1)))
        assertNull(s.nextAfter(at(2026, 11, 3, 10)))
        assertNull(s.nextAfter(at(2027, 1, 1)))
    }

    @Test
    fun `a day can carry many rings`() {
        val s = monthly(15, every = 5, until = 17 * 60)
        assertEquals(97, s.ringsPerDay)
        assertEquals(at(2026, 10, 15, 9, 5), s.nextAfter(at(2026, 10, 15, 9, 2)))
        assertEquals(at(2026, 10, 15, 12, 30), s.nextAfter(at(2026, 10, 15, 12, 29)))
        assertEquals(at(2026, 10, 15, 17), s.nextAfter(at(2026, 10, 15, 16, 57)))
        assertEquals(at(2026, 11, 15, 9), s.nextAfter(at(2026, 10, 15, 17)))
        // 200 reminders on one day is just a 5-minute run; a whole day from midnight is 288.
        assertEquals(288, ReminderSchedule(ScheduleKind.DAILY, 0, burstEveryMinutes = 5, burstUntilMinute = 23 * 60 + 55).ringsPerDay)
    }

    @Test
    fun `schedules ring with no deadline at all`() {
        val t = todo(deadlineDay = null, minuteOfDay = null, plan = ReminderPlan.of(listOf(60), null, listOf(daily())))
        assertTrue(t.hasReminder)
        // The lead time counted from nothing is gone; the schedule is not.
        assertTrue(t.reminders().leadMinutes.isEmpty())
        assertEquals(listOf(daily()), t.reminders().schedules)
        assertEquals(at(2026, 10, 10, 8), t.nextReminderAfter(at(2026, 10, 10, 7)))
        assertNull(t.copy(state = TodoState.COMPLETE).nextReminderAfter(at(2026, 10, 10, 7)))
        assertNull(t.copy(isDeleted = true).nextReminderAfter(at(2026, 10, 10, 7)))
    }

    @Test
    fun `the soonest of the deadline reminders and the schedules wins`() {
        val t = todo(plan = ReminderPlan.of(listOf(60), null, listOf(daily())))
        // Lead: 16:00 on the 10th. Schedule: 08:00 every day.
        assertEquals(at(2026, 10, 10, 8), t.nextReminderAfter(at(2026, 10, 10, 7)))
        assertEquals(at(2026, 10, 10, 16), t.nextReminderAfter(at(2026, 10, 10, 9)))
        // Past the deadline the schedule keeps going until the todo is ticked off.
        assertEquals(at(2026, 10, 11, 8), t.nextReminderAfter(at(2026, 10, 10, 18)))
    }

    @Test
    fun `asking only about one-offs ignores recurring schedules`() {
        val t = todo(plan = ReminderPlan.of(emptyList(), null, listOf(daily(), once(20261103))))
        assertEquals(at(2026, 11, 3, 10), t.nextReminderAfter(at(2026, 10, 10, 7), includeRepeats = false))
    }

    @Test
    fun `schedule wording reads naturally`() {
        assertEquals("Every day at 08:00", daily().describe())
        assertEquals("Weekdays at 08:00", weekly(1, 2, 3, 4, 5).describe())
        assertEquals("Weekends at 08:00", weekly(6, 7).describe())
        assertEquals("Every Mon, Fri at 08:00", weekly(1, 5).describe())
        assertEquals("Monthly on the 15th at 09:00", monthly(15).describe())
        assertEquals("Monthly on the 22nd at 09:00", monthly(22).describe())
        assertEquals("Monthly on the 11th at 09:00", monthly(11).describe())
        assertEquals(
            "Monthly on the 15th at 09:00, then every 5 min until 17:00",
            monthly(15, every = 5, until = 17 * 60).describe()
        )
        assertEquals("3 Nov 2026 at 10:00", once(20261103).describe())
        val both = ReminderPlan.of(listOf(0), null, listOf(daily(), weekly(1)))
        assertEquals("At due time · Every day at 08:00 · Every Mon at 08:00", both.describe())
        assertEquals("At due time · 2 schedules", both.summary())
    }
}
