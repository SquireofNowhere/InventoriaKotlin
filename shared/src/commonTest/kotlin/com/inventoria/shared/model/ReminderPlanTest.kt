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

    // Calendar schedules. 10 October 2026 is a Saturday, so the 12th is a Monday and the 16th a Friday.

    private fun daily(minute: Int = 8 * 60) = ReminderSchedule(ScheduleKind.DAILY, minute)
    private fun weekly(vararg days: Int, minute: Int = 8 * 60) =
        ReminderSchedule(ScheduleKind.WEEKLY, minute, weekdays = days.toSet())
    private fun monthly(day: Int, minute: Int = 9 * 60, every: Int = 0, until: Int = minute) =
        ReminderSchedule(ScheduleKind.MONTHLY, minute, dayOfMonth = day, burstEveryMinutes = every, burstUntilMinute = until)
    private fun once(date: Int, minute: Int = 10 * 60) = ReminderSchedule(ScheduleKind.ONCE, minute, date = date)
    private fun ReminderSchedule.next(now: Long) = nextAfter(now, zone)

    @Test
    fun schedulesSurviveEncodingAndParsingNextToTheOtherRules() {
        val plan = ReminderPlan.of(
            listOf(0, 240),
            ReminderSpan(2, ReminderUnit.HOUR),
            listOf(daily(), weekly(1, 5), monthly(15, every = 5, until = 17 * 60), once(20261103))
        )
        assertEquals("before=4h,0m every=2h on=d@0800,w15@0800,m15@0900~5-1700,o20261103@1000", plan.encode())
        assertEquals(plan, ReminderPlan.parse(plan.encode()))
    }

    @Test
    fun aPlanWithOnlySchedulesIsNotEmptyAndNeedsNoDeadline() {
        val plan = ReminderPlan.of(emptyList(), null, listOf(daily()))
        assertTrue(!plan.isEmpty)
        assertTrue(!plan.needsDeadline)
        assertTrue(ReminderPlan.of(listOf(60), null).needsDeadline)
    }

    @Test
    fun schedulesThatCannotRingAreDropped() {
        val plan = ReminderPlan.parse("on=d@0800,zz,w9@0800,m40@0800,o20260231@0800,d@2561,m31@0800")
        assertEquals(listOf(daily(), monthly(31, minute = 8 * 60)), plan.schedules)
    }

    @Test
    fun aRepeatOnTheDayIsLiftedToFiveMinutesAndNeedsALaterEnd() {
        val fast = ReminderSchedule(ScheduleKind.DAILY, 9 * 60, burstEveryMinutes = 1, burstUntilMinute = 10 * 60).normalised()!!
        assertEquals(5, fast.burstEveryMinutes)
        assertEquals(13, fast.ringsPerDay)
        val noRoom = ReminderSchedule(ScheduleKind.DAILY, 9 * 60, burstEveryMinutes = 10, burstUntilMinute = 9 * 60).normalised()!!
        assertEquals(0, noRoom.burstEveryMinutes)
    }

    @Test
    fun aDailyScheduleRingsAtItsTimeEveryDay() {
        assertEquals(at(2026, 10, 10, 8), daily().next(at(2026, 10, 10, 7)))
        assertEquals(at(2026, 10, 11, 8), daily().next(at(2026, 10, 10, 8)))
    }

    @Test
    fun aWeeklyScheduleRingsOnTheChosenWeekdaysOnly() {
        val monAndFri = weekly(1, 5)
        assertEquals(at(2026, 10, 12, 8), monAndFri.next(at(2026, 10, 10, 12)))
        assertEquals(at(2026, 10, 16, 8), monAndFri.next(at(2026, 10, 12, 8)))
        assertEquals(at(2026, 10, 19, 8), monAndFri.next(at(2026, 10, 16, 8)))
    }

    @Test
    fun aMonthlyScheduleRingsOnItsDayAndClampsToShortMonths() {
        assertEquals(at(2026, 10, 15, 9), monthly(15).next(at(2026, 10, 1)))
        assertEquals(at(2026, 11, 15, 9), monthly(15).next(at(2026, 10, 15, 9)))
        assertEquals(at(2026, 2, 28, 9), monthly(31).next(at(2026, 2, 1)))
        assertEquals(at(2026, 3, 31, 9), monthly(31).next(at(2026, 2, 28, 9)))
    }

    @Test
    fun aOneOffRingsOnceAndThenNeverAgain() {
        val s = once(20261103)
        assertEquals(at(2026, 11, 3, 10), s.next(at(2026, 10, 1)))
        assertNull(s.next(at(2026, 11, 3, 10)))
    }

    @Test
    fun aDayCanCarryManyRings() {
        val s = monthly(15, every = 5, until = 17 * 60)
        assertEquals(97, s.ringsPerDay)
        assertEquals(at(2026, 10, 15, 9, 5), s.next(at(2026, 10, 15, 9, 2)))
        assertEquals(at(2026, 10, 15, 12, 30), s.next(at(2026, 10, 15, 12, 29)))
        assertEquals(at(2026, 10, 15, 17), s.next(at(2026, 10, 15, 16, 57)))
        assertEquals(at(2026, 11, 15, 9), s.next(at(2026, 10, 15, 17)))
    }

    @Test
    fun schedulesRingWithNoDeadlineAtAll() {
        val t = todo(deadlineDay = null, minuteOfDay = null, plan = ReminderPlan.of(listOf(60), null, listOf(daily())))
        assertTrue(t.hasReminder)
        assertTrue(t.reminders().leadMinutes.isEmpty())
        assertEquals(listOf(daily()), t.reminders().schedules)
        assertEquals(at(2026, 10, 10, 8), t.next(at(2026, 10, 10, 7)))
        assertNull(t.copy(state = TodoState.COMPLETE).next(at(2026, 10, 10, 7)))
    }

    @Test
    fun theSoonestOfTheDeadlineRemindersAndTheSchedulesWins() {
        val t = todo(plan = ReminderPlan.of(listOf(60), null, listOf(daily())))
        assertEquals(at(2026, 10, 10, 8), t.next(at(2026, 10, 10, 7)))
        assertEquals(at(2026, 10, 10, 16), t.next(at(2026, 10, 10, 9)))
        assertEquals(at(2026, 10, 11, 8), t.next(at(2026, 10, 10, 18)))
    }

    @Test
    fun askingOnlyAboutOneOffsIgnoresRecurringSchedules() {
        val t = todo(plan = ReminderPlan.of(emptyList(), null, listOf(daily(), once(20261103))))
        assertEquals(at(2026, 11, 3, 10), t.next(at(2026, 10, 10, 7), includeRepeats = false))
    }

    @Test
    fun scheduleWordingReadsNaturally() {
        assertEquals("Every day at 08:00", daily().describe())
        assertEquals("Weekdays at 08:00", weekly(1, 2, 3, 4, 5).describe())
        assertEquals("Every Mon, Fri at 08:00", weekly(1, 5).describe())
        assertEquals(
            "Monthly on the 15th at 09:00, then every 5 min until 17:00",
            monthly(15, every = 5, until = 17 * 60).describe()
        )
        assertEquals("3 Nov 2026 at 10:00", once(20261103).describe())
    }
}
