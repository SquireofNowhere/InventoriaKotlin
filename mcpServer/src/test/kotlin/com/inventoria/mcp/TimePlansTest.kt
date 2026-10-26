package com.inventoria.mcp

import com.inventoria.shared.model.Task
import com.inventoria.shared.model.TaskTimePlan
import com.inventoria.shared.model.Todo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimePlansTest {
    private val now = parseInstant("2026-10-26T12:00")

    private fun ints(vararg values: Int) = JsonArray(values.map { JsonPrimitive(it) })
    private fun strings(vararg values: String) = JsonArray(values.map { JsonPrimitive(it) })

    // --- a session's time plan ---

    @Test
    fun planFieldsApplyOnTopOfWhatIsThere() {
        val base = TaskTimePlan.of(goalMinutes = 60, ringEveryMinutes = 30)
        val args = Args(buildJsonObject { put("ring_at_minutes", ints(25, 45)) })
        val plan = applyTimePlanArgs(args, base, now)
        assertEquals(60, plan.goalMinutes)
        assertEquals(30, plan.ringEveryMinutes)
        assertEquals(listOf(25, 45), plan.ringAtMinutes)
    }

    @Test
    fun nullClearsOnlyItsOwnField() {
        val base = TaskTimePlan.of(goalMinutes = 60, ringAtMinutes = listOf(25), ringEveryMinutes = 30)
        val plan = applyTimePlanArgs(Args(buildJsonObject { put("ring_every_minutes", JsonNull) }), base, now)
        assertNull(plan.ringEveryMinutes)
        assertEquals(60, plan.goalMinutes)
        assertEquals(listOf(25), plan.ringAtMinutes)
        assertTrue(applyTimePlanArgs(Args(buildJsonObject { put("ring_at_minutes", ints()) }), base, now).ringAtMinutes.isEmpty())
    }

    @Test
    fun removingTheGoalTakesTheStopAtItAlong() {
        val base = TaskTimePlan.of(goalMinutes = 60, stopAtGoal = true)
        val plan = applyTimePlanArgs(Args(buildJsonObject { put("goal_minutes", JsonNull) }), base, now)
        assertNull(plan.goalMinutes)
        assertEquals(false, plan.stopAtGoal)
    }

    @Test
    fun stoppingAtAGoalThatIsNotThereIsAnError() {
        assertFailsWith<ToolError> {
            applyTimePlanArgs(Args(buildJsonObject { put("stop_at_goal", true) }), TaskTimePlan.NONE, now)
        }
        val plan = applyTimePlanArgs(
            Args(buildJsonObject { put("goal_minutes", 45); put("stop_at_goal", true) }), TaskTimePlan.NONE, now
        )
        assertTrue(plan.stopAtGoal)
    }

    @Test
    fun outOfRangePlanValuesAreRefusedNotClamped() {
        fun refused(key: String, value: kotlinx.serialization.json.JsonElement) = assertFailsWith<ToolError> {
            applyTimePlanArgs(Args(buildJsonObject { put(key, value) }), TaskTimePlan.NONE, now)
        }
        refused("goal_minutes", JsonPrimitive(0))
        refused("goal_minutes", JsonPrimitive(TaskTimePlan.MAX_MINUTES + 1))
        refused("ring_every_minutes", JsonPrimitive(TaskTimePlan.MIN_EVERY_MINUTES - 1))
        refused("ring_at_minutes", ints(0))
        refused("ring_at_minutes", ints(*IntArray(TaskTimePlan.MAX_MARKS + 1) { it + 1 }))
    }

    @Test
    fun aStopTimeMustStillBeAhead() {
        val future = applyTimePlanArgs(Args(buildJsonObject { put("stop_at", "2026-10-26T17:00") }), TaskTimePlan.NONE, now)
        assertEquals(parseInstant("2026-10-26T17:00"), future.stopAtMillis)
        assertFailsWith<ToolError> {
            applyTimePlanArgs(Args(buildJsonObject { put("stop_at", "2026-10-26T09:00") }), TaskTimePlan.NONE, now)
        }
        val cleared = applyTimePlanArgs(Args(buildJsonObject { put("stop_at", JsonNull) }), future, now)
        assertNull(cleared.stopAtMillis)
    }

    @Test
    fun knowsWhetherAnyPlanFieldWasSent() {
        assertTrue(hasTimePlanArgs(Args(buildJsonObject { put("stop_at_goal", false) })))
        assertEquals(false, hasTimePlanArgs(Args(buildJsonObject { put("name", "x") })))
    }

    // --- a finished segment's times ---

    private val start = parseInstant("2026-10-26T08:00")
    private val end = parseInstant("2026-10-26T09:00")
    private val segment = Task(id = "t", startTime = start, endTime = end, duration = end - start)
    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun sendingStartAndEndSetsBoth() {
        val (s, e) = resolveSegmentTimes(segment, "2026-10-26T07:30", "2026-10-26T10:00", null, now)
        assertEquals(parseInstant("2026-10-26T07:30"), s)
        assertEquals(parseInstant("2026-10-26T10:00"), e)
    }

    @Test
    fun whatIsLeftOutStays() {
        assertEquals(start to parseInstant("2026-10-26T09:30"), resolveSegmentTimes(segment, null, "2026-10-26T09:30", null, now))
        assertEquals(parseInstant("2026-10-26T08:30") to end, resolveSegmentTimes(segment, "2026-10-26T08:30", null, null, now))
    }

    @Test
    fun aDurationCountsFromTheStart() {
        assertEquals(start to start + minutes(90), resolveSegmentTimes(segment, null, null, 90, now))
        val moved = parseInstant("2026-10-26T10:00")
        assertEquals(moved to moved + minutes(15), resolveSegmentTimes(segment, "2026-10-26T10:00", null, 15, now))
    }

    @Test
    fun badTimesAreRefused() {
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, null, null, null, now) }
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, null, "2026-10-26T09:30", 30, now) }
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, null, null, 0, now) }
        // the end must be after the start, whichever of the two moved
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, "2026-10-26T09:00", null, null, now) }
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, null, "2026-10-26T07:00", null, now) }
        // and the time has to be over already
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, null, "2026-10-26T13:00", null, now) }
        assertFailsWith<ToolError> { resolveSegmentTimes(segment, "2026-10-26T11:30", null, 60, now) }
    }

    @Test
    fun aSegmentWithNoRecordedEndFallsBackOnItsDuration() {
        val noEnd = Task(id = "t", startTime = start, endTime = null, duration = minutes(45))
        assertEquals(start to start + minutes(45), resolveSegmentTimes(noEnd, null, null, 45, now))
        assertEquals(start + minutes(10) to start + minutes(45), resolveSegmentTimes(noEnd, "2026-10-26T08:10", null, null, now))
    }

    @Test
    fun findsOtherSegmentsAnEditLandsOn() {
        val before = Task(id = "a", startTime = start - minutes(120), endTime = start - minutes(60))
        val touching = Task(id = "b", startTime = end, endTime = end + minutes(30))
        val inside = Task(id = "c", startTime = start + minutes(10), endTime = start + minutes(20))
        val deleted = Task(id = "d", startTime = start, endTime = end, isDeleted = true)
        val running = Task(id = "e", startTime = start + minutes(30), endTime = null, isRunning = true)
        val hits = overlappingSegments(listOf(segment, before, touching, inside, deleted, running), segment, start, end)
        assertEquals(listOf("c", "e"), hits.map { it.id })
    }

    // --- a todo's auto start ---

    private val dated = Todo(id = "x", deadline = parseDay("2026-11-01"))
    private val undated = Todo(id = "y")

    @Test
    fun noAutoStartFieldLeavesTheTodoAlone() {
        assertSame(dated, applyAutoStartArgs(Args(buildJsonObject { put("title", "x") }), dated))
    }

    @Test
    fun autoStartFieldsBuildAPlan() {
        val args = Args(buildJsonObject {
            put("auto_start_before", "15m")
            put("auto_start_schedules", strings("w15@0800"))
            put("auto_start_run_minutes", 45)
        })
        assertEquals("before=15 on=w15@0800 run=45", applyAutoStartArgs(args, dated).autoStart)
    }

    @Test
    fun eachFieldChangesOnlyItself() {
        val todo = dated.copy(autoStart = "before=15 on=d@0800 run=30")
        assertEquals("before=15 on=d@0800", applyAutoStartArgs(Args(buildJsonObject { put("auto_start_run_minutes", JsonNull) }), todo).autoStart)
        assertEquals("on=d@0800 run=30", applyAutoStartArgs(Args(buildJsonObject { put("auto_start_before", JsonNull) }), todo).autoStart)
        assertEquals("before=15 run=30", applyAutoStartArgs(Args(buildJsonObject { put("auto_start_schedules", strings()) }), todo).autoStart)
    }

    @Test
    fun calendarStartsNeedNoDeadlineButADueRelativeOneDoes() {
        val onCalendar = Args(buildJsonObject { put("auto_start_schedules", strings("d@0800")) })
        assertEquals("on=d@0800", applyAutoStartArgs(onCalendar, undated).autoStart)
        assertFailsWith<ToolError> {
            applyAutoStartArgs(Args(buildJsonObject { put("auto_start_before", "15m") }), undated)
        }
    }

    @Test
    fun aRunLengthGoesWhenTheLastStartDoes() {
        val todo = undated.copy(autoStart = "on=d@0800 run=30")
        assertEquals("", applyAutoStartArgs(Args(buildJsonObject { put("auto_start_schedules", strings()) }), todo).autoStart)
        assertFailsWith<ToolError> {
            applyAutoStartArgs(Args(buildJsonObject { put("auto_start_run_minutes", 30) }), undated)
        }
    }

    @Test
    fun badAutoStartValuesAreRefused() {
        fun refused(key: String, value: kotlinx.serialization.json.JsonElement) = assertFailsWith<ToolError> {
            applyAutoStartArgs(Args(buildJsonObject { put(key, value) }), dated)
        }
        refused("auto_start_before", JsonPrimitive("soon"))
        refused("auto_start_schedules", strings("nonsense"))
        refused("auto_start_schedules", JsonArray((1..11).map { JsonPrimitive("o2026110${it % 9 + 1}@0800") }))
        refused("auto_start_run_minutes", JsonPrimitive(0))
    }
}
