package com.inventoria.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same cases as the Android app's ReminderPlanTest: the two copies of the maths must agree. */
class ReminderPlanTest {
    private val zone = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds()
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = day(y, m, d) + (h * 60L + min) * 60_000L

    private fun todo(
        deadlineDay: Long? = day(2026, 10, 10),
        minuteOfDay: Int? = 17 * 60,
        plan: ReminderPlan = ReminderPlan.NONE
    ) = Todo(id = "t", deadline = deadlineDay, deadlineMinuteOfDay = minuteOfDay).withReminders(plan)

    private fun Todo.next(now: Long, includeRepeats: Boolean = true) = nextReminderAfter(now, includeRepeats, zone)

    @Test
    fun aPlanSurvivesEncodingAndParsing() {
        val plan = ReminderPlan.of(listOf(0, 240, 1440), ReminderSpan(2, ReminderUnit.HOUR))
        assertEquals("before=1d,4h,0m every=2h", plan.encode())
        assertEquals(plan, ReminderPlan.parse(plan.encode()))
    }

    @Test
    fun partsThatCannotBeReadAreDropped() {
        val plan = ReminderPlan.parse("before=4h,zz,3mo every=banana later=1")
        assertEquals(listOf(240), plan.leadMinutes)
        assertNull(plan.every)
        assertTrue(ReminderPlan.parse("").isEmpty)
    }

    @Test
    fun aRepeatTighterThanFiveMinutesIsLiftedToFive() {
        assertEquals(ReminderSpan(5, ReminderUnit.MINUTE), ReminderPlan.parse("every=1m").every)
    }

    @Test
    fun aLegacyTodoRingsForItsSingleOffset() {
        val legacy = Todo(id = "t", deadline = day(2026, 10, 10), reminderOffsetMinutes = 60)
        assertEquals(ReminderPlan.of(listOf(60), null), legacy.reminders())
    }

    @Test
    fun settingAPlanMirrorsTheClosestLeadAndClearsWithTheDeadline() {
        val plan = ReminderPlan.of(listOf(60, 360), ReminderSpan(1, ReminderUnit.DAY))
        assertEquals(60, todo(plan = plan).reminderOffsetMinutes)
        assertTrue(!todo(deadlineDay = null, plan = plan).hasReminder)
    }

    @Test
    fun severalLeadTimesRingOneAfterAnotherThenStop() {
        val t = todo(plan = ReminderPlan.of(listOf(360, 300, 240), null))
        assertEquals(at(2026, 10, 10, 11), t.next(at(2026, 10, 10, 10)))
        assertEquals(at(2026, 10, 10, 12), t.next(at(2026, 10, 10, 11)))
        assertEquals(at(2026, 10, 10, 13), t.next(at(2026, 10, 10, 12, 30)))
        assertNull(t.next(at(2026, 10, 10, 13)))
    }

    @Test
    fun everyTwoHoursIsCountedBackFromTheDueTime() {
        val t = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(2, ReminderUnit.HOUR)))
        assertEquals(at(2026, 10, 10, 11), t.next(at(2026, 10, 10, 10, 30)))
        assertEquals(at(2026, 10, 10, 17), t.next(at(2026, 10, 10, 15)))
        assertNull(t.next(at(2026, 10, 10, 17)))
    }

    @Test
    fun everyDayAndEveryWeekLandOnTheDueTimeOfDay() {
        val daily = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.DAY)))
        assertEquals(at(2026, 10, 6, 17), daily.next(at(2026, 10, 6, 12)))
        assertEquals(at(2026, 10, 7, 17), daily.next(at(2026, 10, 6, 17)))
        val weekly = todo(plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.WEEK)))
        assertEquals(at(2026, 10, 3, 17), weekly.next(at(2026, 9, 28)))
    }

    @Test
    fun everyMonthClampsToAShorterMonthWithoutDrifting() {
        val t = todo(
            deadlineDay = day(2026, 3, 31),
            minuteOfDay = 9 * 60,
            plan = ReminderPlan.of(emptyList(), ReminderSpan(1, ReminderUnit.MONTH))
        )
        assertEquals(at(2026, 2, 28, 9), t.next(at(2026, 2, 1)))
        assertEquals(at(2026, 1, 31, 9), t.next(at(2026, 1, 1)))
    }

    @Test
    fun theSoonestOfTheLeadTimesAndTheRepeatWins() {
        val t = todo(plan = ReminderPlan.of(listOf(60), ReminderSpan(1, ReminderUnit.DAY)))
        assertEquals(at(2026, 10, 9, 17), t.next(at(2026, 10, 9, 12)))
        assertEquals(at(2026, 10, 10, 16), t.next(at(2026, 10, 9, 12), includeRepeats = false))
    }
}
