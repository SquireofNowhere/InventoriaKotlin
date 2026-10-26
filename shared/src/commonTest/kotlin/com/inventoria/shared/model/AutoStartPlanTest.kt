package com.inventoria.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoStartPlanTest {
    private fun schedule(token: String) = ReminderSchedule.parse(token)!!

    @Test
    fun encodesAndParsesBackToTheSamePlan() {
        val plan = AutoStartPlan.of(15, listOf(schedule("w15@0800")), 45)
        assertEquals("before=15 on=w15@0800 run=45", plan.encode())
        assertEquals(plan, AutoStartPlan.parse(plan.encode()))
    }

    @Test
    fun blankTextIsNotAutomatic() {
        assertTrue(AutoStartPlan.parse("").isEmpty)
        assertEquals("", AutoStartPlan.NONE.encode())
        assertEquals("Not automatic", AutoStartPlan.NONE.describe())
    }

    @Test
    fun aRepeatOnTheDayMeansNothingForAStartAndIsDropped() {
        val plan = AutoStartPlan.of(null, listOf(schedule("d@0800~5-1700")), null)
        assertEquals("on=d@0800", plan.encode())
    }

    @Test
    fun aRunLengthWithNothingToStartItDoesNotMakeThePlanNonEmpty() {
        assertTrue(AutoStartPlan.of(null, emptyList(), 30).isEmpty)
    }

    @Test
    fun outOfRangeValuesAreDropped() {
        assertEquals("", AutoStartPlan.of(-1, emptyList(), 0).encode())
        assertEquals("", AutoStartPlan.of(AutoStartPlan.MAX_BEFORE_MINUTES + 1, emptyList(), null).encode())
    }

    @Test
    fun withNoDeadlineOnlyTheBeforeDuePartGoes() {
        val plan = AutoStartPlan.of(15, listOf(schedule("d@0800")), 30)
        assertEquals("on=d@0800 run=30", Todo(deadline = null).withAutoStart(plan).autoStart)
        assertEquals("before=15 on=d@0800 run=30", Todo(deadline = 1_000L).withAutoStart(plan).autoStart)
    }

    @Test
    fun aTodoReadsBackItsOwnPlan() {
        val todo = Todo(deadline = 1_000L).withAutoStart(AutoStartPlan.of(0, emptyList(), 25))
        assertEquals(0, todo.autoStartPlan().beforeDueMinutes)
        assertEquals(25, todo.autoStartPlan().runMinutes)
    }

    @Test
    fun describesAPlanInWords() {
        val text = AutoStartPlan.of(15, listOf(schedule("d@0800")), 45).describe()
        assertTrue(text.startsWith("15 min before it's due"), text)
        assertTrue(text.endsWith("then stops after 45 min"), text)
        assertEquals("At the due time", AutoStartPlan.of(0, emptyList(), null).describe())
    }
}
