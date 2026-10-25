package com.inventoria.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskTimePlanTest {

    private val minute = 60_000L

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, h, min, 0)
    }.timeInMillis

    // --- the stored text -------------------------------------------------------------------

    @Test
    fun `a plan survives encoding and parsing`() {
        val plan = TaskTimePlan.of(
            goalMinutes = 90,
            ringAtMinutes = listOf(45, 25, 25),
            ringEveryMinutes = 30,
            stopAtGoal = true,
            stopAtMillis = 1_730_000_000_000L
        )
        assertEquals("goal=90 at=25,45 every=30 stop=goal stopat=1730000000000", plan.encode())
        assertEquals(plan, TaskTimePlan.parse(plan.encode()))
    }

    @Test
    fun `parts that cannot be read are dropped instead of failing`() {
        val plan = TaskTimePlan.parse("goal=0 at=5,x,99999 every=1 stop=goal later=1")
        assertNull(plan.goalMinutes)
        assertEquals(listOf(5), plan.ringAtMinutes)
        // A repeat tighter than five minutes is lifted to five, as reminders are.
        assertEquals(5, plan.ringEveryMinutes)
        // Stopping at a goal needs a goal.
        assertTrue(!plan.stopAtGoal)
        assertTrue(TaskTimePlan.parse("").isEmpty)
        assertTrue(TaskTimePlan.parse("   ").isEmpty)
    }

    @Test
    fun `a plan holds at most twenty marks`() {
        assertEquals(TaskTimePlan.MAX_MARKS, TaskTimePlan.of(ringAtMinutes = (1..40).toList()).ringAtMinutes.size)
    }

    // --- when it rings ---------------------------------------------------------------------

    @Test
    fun `the next ring is the soonest of the marks, the goal and the repeat`() {
        val plan = TaskTimePlan.of(goalMinutes = 60, ringAtMinutes = listOf(25, 45), ringEveryMinutes = 30)
        assertEquals(25 * minute, plan.nextRingElapsedAfter(0))
        assertEquals(30 * minute, plan.nextRingElapsedAfter(25 * minute))
        assertEquals(45 * minute, plan.nextRingElapsedAfter(30 * minute))
        assertEquals(60 * minute, plan.nextRingElapsedAfter(45 * minute))
        // Past the goal only the repeat is left.
        assertEquals(90 * minute, plan.nextRingElapsedAfter(60 * minute))
    }

    @Test
    fun `marks alone run out`() {
        val plan = TaskTimePlan.of(ringAtMinutes = listOf(25))
        assertEquals(25 * minute, plan.nextRingElapsedAfter(24 * minute))
        assertNull(plan.nextRingElapsedAfter(25 * minute))
    }

    @Test
    fun `a plan with nothing to ring for never rings`() {
        assertNull(TaskTimePlan.NONE.nextRingElapsedAfter(0))
        assertNull(TaskTimePlan.of(stopAtMillis = 5L).nextRingElapsedAfter(0))
    }

    // --- when it stops ---------------------------------------------------------------------

    @Test
    fun `stopping at the goal is counted from the time already run`() {
        val plan = TaskTimePlan.of(goalMinutes = 60, stopAtGoal = true)
        val now = at(2026, 10, 10, 12)
        assertEquals(now + 10 * minute, plan.stopInstant(50 * minute, now))
        // Already over: the instant it should have ended, in the past.
        assertEquals(now - 10 * minute, plan.stopInstant(70 * minute, now))
    }

    @Test
    fun `a goal without stop-at-goal only rings`() {
        val plan = TaskTimePlan.of(goalMinutes = 60)
        assertNull(plan.stopInstant(50 * minute, 0L))
        assertEquals(60 * minute, plan.nextRingElapsedAfter(50 * minute))
    }

    @Test
    fun `the earlier of the goal stop and the clock stop wins`() {
        val now = at(2026, 10, 10, 12)
        val clock = now + 5 * minute
        val plan = TaskTimePlan.of(goalMinutes = 60, stopAtGoal = true, stopAtMillis = clock)
        assertEquals(clock, plan.stopInstant(50 * minute, now))
        assertEquals(now + 1 * minute, plan.stopInstant(59 * minute, now))
    }

    @Test
    fun `the next event is the ring or the stop, whichever comes first`() {
        val now = at(2026, 10, 10, 12)
        val plan = TaskTimePlan.of(goalMinutes = 60, ringAtMinutes = listOf(25), stopAtGoal = true)
        assertEquals(now + 5 * minute, plan.nextEventAt(20 * minute, now))
        // Past the mark, the goal is next -- as both a ring and the stop.
        assertEquals(now + 35 * minute, plan.nextEventAt(25 * minute, now))
    }

    // --- the session's clock ---------------------------------------------------------------

    @Test
    fun `a session's time is its finished segments plus the running one`() {
        val now = at(2026, 10, 10, 12)
        val segments = listOf(
            Task(id = "a", groupId = "g", startTime = now - 60 * minute, duration = 10 * minute),
            Task(id = "b", groupId = "g", startTime = now - 2 * minute, isRunning = true)
        )
        assertEquals(12 * minute, sessionElapsedMillis(segments, now))
        assertEquals(10 * minute, sessionElapsedMillis(segments.take(1), now))
    }

    @Test
    fun `a session follows the plan on its latest segment`() {
        val old = Task(id = "a", groupId = "g", startTime = 1_000, timePlan = "goal=30")
        val latest = Task(id = "b", groupId = "g", startTime = 2_000, timePlan = "goal=45")
        assertEquals(45, listOf(old, latest).sessionTimePlan().goalMinutes)
        assertTrue(emptyList<Task>().sessionTimePlan().isEmpty)
    }

    // --- wording ---------------------------------------------------------------------------

    @Test
    fun `lengths are spelled the way people say them`() {
        assertEquals("25 min", TaskTimePlan.minutesLabel(25))
        assertEquals("1 hr", TaskTimePlan.minutesLabel(60))
        assertEquals("1 hr 30 min", TaskTimePlan.minutesLabel(90))
        assertEquals("1 day", TaskTimePlan.minutesLabel(24 * 60))
        assertEquals("2 days", TaskTimePlan.minutesLabel(48 * 60))
        assertEquals("under a minute", TaskTimePlan.elapsedLabel(30_000))
        assertEquals("1 hr 5 min", TaskTimePlan.elapsedLabel(65 * minute))
    }

    @Test
    fun `the plan reads back in words`() {
        assertEquals("No plan", TaskTimePlan.NONE.describe())
        assertEquals(
            "Planned 1 hr · rings at 25 min, 45 min · rings every 30 min · stops when the plan is up",
            TaskTimePlan.of(goalMinutes = 60, ringAtMinutes = listOf(25, 45), ringEveryMinutes = 30, stopAtGoal = true).describe()
        )
    }

    @Test
    fun `a clock stop is the next time the clock reads that minute`() {
        assertEquals(at(2026, 10, 11, 17), TaskTimePlan.nextClockInstant(17 * 60, at(2026, 10, 10, 18)))
        assertEquals(at(2026, 10, 10, 19), TaskTimePlan.nextClockInstant(19 * 60, at(2026, 10, 10, 18)))
        // Exactly now is already gone: the next one is tomorrow's.
        assertEquals(at(2026, 10, 11, 18), TaskTimePlan.nextClockInstant(18 * 60, at(2026, 10, 10, 18)))
        assertEquals("17:00", TaskTimePlan.clockLabel(at(2026, 10, 10, 17)))
    }
}
