package com.inventoria.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskTimePlanTest {
    @Test
    fun encodesAndParsesBackToTheSamePlan() {
        val plan = TaskTimePlan.of(
            goalMinutes = 90,
            ringAtMinutes = listOf(45, 25),
            ringEveryMinutes = 30,
            stopAtGoal = true,
            stopAtMillis = 1_730_000_000_000L
        )
        assertEquals("goal=90 at=25,45 every=30 stop=goal stopat=1730000000000", plan.encode())
        assertEquals(plan, TaskTimePlan.parse(plan.encode()))
    }

    @Test
    fun blankTextIsNoPlan() {
        assertTrue(TaskTimePlan.parse("").isEmpty)
        assertTrue(TaskTimePlan.parse("   ").isEmpty)
        assertEquals("", TaskTimePlan.NONE.encode())
    }

    @Test
    fun stoppingAtTheGoalNeedsAGoalToStopAt() {
        val plan = TaskTimePlan.of(stopAtGoal = true)
        assertFalse(plan.stopAtGoal)
        assertEquals("", plan.encode())
    }

    @Test
    fun outOfRangeValuesAreDroppedAndTheRepeatIsLiftedToTheFloor() {
        assertNull(TaskTimePlan.of(goalMinutes = 0).goalMinutes)
        assertNull(TaskTimePlan.of(goalMinutes = TaskTimePlan.MAX_MINUTES + 1).goalMinutes)
        assertEquals(TaskTimePlan.MIN_EVERY_MINUTES, TaskTimePlan.of(ringEveryMinutes = 1).ringEveryMinutes)
        assertNull(TaskTimePlan.of(stopAtMillis = 0L).stopAtMillis)
    }

    @Test
    fun marksAreSortedUniqueAndCapped() {
        assertEquals(listOf(10, 25), TaskTimePlan.of(ringAtMinutes = listOf(25, 10, 25, 0, -3)).ringAtMinutes)
        val many = TaskTimePlan.of(ringAtMinutes = (1..40).toList()).ringAtMinutes
        assertEquals(TaskTimePlan.MAX_MARKS, many.size)
        assertEquals(1, many.first())
    }

    @Test
    fun unreadableTextIsDroppedRatherThanFailing() {
        val plan = TaskTimePlan.parse("goal=abc at=x,5 bogus every=")
        assertNull(plan.goalMinutes)
        assertEquals(listOf(5), plan.ringAtMinutes)
        assertNull(plan.ringEveryMinutes)
    }

    @Test
    fun describesAPlanInWords() {
        val utc = TimeZone.UTC
        val fivePm = LocalDate(2026, 10, 26).atStartOfDayIn(utc).toEpochMilliseconds() + 17 * 60 * 60_000L
        val plan = TaskTimePlan.of(goalMinutes = 90, ringAtMinutes = listOf(25, 45), ringEveryMinutes = 30, stopAtMillis = fivePm)
        assertEquals(
            "Planned 1 hr 30 min · rings at 25 min, 45 min · rings every 30 min · stops at 17:00",
            plan.describe(utc)
        )
        assertEquals("No plan", TaskTimePlan.NONE.describe(utc))
    }

    @Test
    fun labelsMinutesTheWayThePhoneDoes() {
        assertEquals("25 min", TaskTimePlan.minutesLabel(25))
        assertEquals("1 hr", TaskTimePlan.minutesLabel(60))
        assertEquals("1 hr 30 min", TaskTimePlan.minutesLabel(90))
        assertEquals("1 day", TaskTimePlan.minutesLabel(24 * 60))
        assertEquals("2 days", TaskTimePlan.minutesLabel(48 * 60))
    }

    @Test
    fun aSessionFollowsThePlanOnItsLatestSegment() {
        val old = Task(id = "a", startTime = 1_000L, timePlan = "goal=30")
        val latest = Task(id = "b", startTime = 2_000L, timePlan = "goal=60 every=15")
        assertEquals(60, listOf(old, latest).sessionTimePlan().goalMinutes)
        assertEquals(60, listOf(latest, old).sessionTimePlan().goalMinutes)
        assertTrue(emptyList<Task>().sessionTimePlan().isEmpty)
    }
}
