package com.inventoria.mcp

import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoRepeat
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.Weekdays
import com.inventoria.shared.remote.FirebaseConfig
import com.inventoria.shared.remote.RealtimeDatabaseRest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeekdaysAndAsapTest {
    private val monday = parseDay("2026-10-26")
    private val tuesday = parseDay("2026-10-27")
    private val monFri = Weekdays.maskOf(listOf(1, 5))

    private fun strings(vararg values: String) = JsonArray(values.map { JsonPrimitive(it) })

    /** A vault that was never connected: enough for the arguments that do not touch the database. */
    private fun offlineVault() = Vault(
        RealtimeDatabaseRest(HttpClient(OkHttp), FirebaseConfig("key", "https://offline.invalid", "")) { error("offline") },
        "owner"
    )

    private fun applied(base: Todo, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Todo =
        runBlocking { applyTodoArgs(offlineVault(), Args(buildJsonObject(build)), base) }

    // --- reading day names ---

    @Test
    fun `days are read by name, number or group`() {
        assertEquals(monFri, parseWeekdays(listOf("mon", "Friday")))
        assertEquals(Weekdays.bit(7), parseWeekdays(listOf("sun")))
        assertEquals(Weekdays.bit(3), parseWeekdays(listOf("3")))
        assertEquals(Weekdays.WORKDAYS, parseWeekdays(listOf("weekdays")))
        assertEquals(Weekdays.WEEKEND, parseWeekdays(listOf("Weekends")))
        assertEquals(Weekdays.ALL, parseWeekdays(listOf("everyday")))
        assertEquals(0, parseWeekdays(emptyList()))
    }

    @Test
    fun `a word that is not a day is refused`() {
        assertFailsWith<ToolError> { parseWeekdays(listOf("mon", "someday")) }
        assertFailsWith<ToolError> { parseWeekdays(listOf("8")) }
    }

    @Test
    fun `only the start's own weekday is stored as plain weekly`() {
        assertEquals(0, storedWeekdays(Weekdays.bit(1), monday))
        assertEquals(monFri, storedWeekdays(monFri, monday))
        assertEquals(Weekdays.bit(1), storedWeekdays(Weekdays.bit(1), tuesday)) // chosen on purpose, not the start's day
        assertEquals(0, storedWeekdays(0, monday))
    }

    // --- a block's days ---

    private fun blockArgs(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = Args(buildJsonObject(build))

    @Test
    fun `a weekly block keeps the days it was sent`() {
        val args = blockArgs { put("weekdays", strings("mon", "fri")) }
        assertEquals(monFri, weekdaysFor(args, weekly = true, startDay = monday, current = 0))
    }

    @Test
    fun `a block with no days sent keeps what it had, and an empty list clears them`() {
        assertEquals(monFri, weekdaysFor(blockArgs { }, weekly = true, startDay = monday, current = monFri))
        assertEquals(0, weekdaysFor(blockArgs { put("weekdays", strings()) }, weekly = true, startDay = monday, current = monFri))
        assertEquals(0, weekdaysFor(blockArgs { put("weekdays", JsonNull) }, weekly = true, startDay = monday, current = monFri))
    }

    @Test
    fun `days for a block that is not weekly are refused, and dropped when it stops being weekly`() {
        assertFailsWith<ToolError> {
            weekdaysFor(blockArgs { put("weekdays", strings("mon")) }, weekly = false, startDay = monday, current = 0)
        }
        assertEquals(0, weekdaysFor(blockArgs { }, weekly = false, startDay = monday, current = monFri))
    }

    // --- a todo's days ---

    @Test
    fun `a weekly todo can repeat on chosen days`() {
        val todo = applied(Todo(id = "t", title = "x")) {
            put("due_date", "2026-10-26")
            put("repeat", "WEEKLY")
            put("repeat_days", strings("mon", "fri"))
        }
        assertEquals(TodoRepeat.WEEKLY, todo.repeatInterval)
        assertEquals(monFri, todo.repeatWeekdays)
    }

    @Test
    fun `chosen days need a weekly repeat and go when it stops being weekly`() {
        assertFailsWith<ToolError> {
            applied(Todo(id = "t", title = "x", deadline = monday, repeatInterval = TodoRepeat.DAILY)) {
                put("repeat_days", strings("mon"))
            }
        }
        val weekly = Todo(id = "t", title = "x", deadline = monday, repeatInterval = TodoRepeat.WEEKLY, repeatWeekdays = monFri)
        assertEquals(0, applied(weekly) { put("repeat", "DAILY") }.repeatWeekdays)
        assertEquals(monFri, applied(weekly) { put("title", "renamed") }.repeatWeekdays)
        assertEquals(0, applied(weekly) { put("repeat_days", strings()) }.repeatWeekdays)
    }

    // --- ASAP ---

    @Test
    fun `tagging a todo ASAP clears its date, time and repeat and starts the timer`() {
        val dated = Todo(
            id = "t", title = "x", deadline = monday, deadlineMinuteOfDay = 600,
            repeatInterval = TodoRepeat.WEEKLY, repeatWeekdays = monFri
        )
        val asap = applied(dated) { put("asap", true) }
        assertNull(asap.deadline)
        assertNull(asap.deadlineMinuteOfDay)
        assertEquals(TodoRepeat.NONE, asap.repeatInterval)
        assertEquals(0, asap.repeatWeekdays)
        assertNotNull(asap.asapSince)
    }

    @Test
    fun `tagging again keeps the original start, and removing the tag clears it`() {
        val already = Todo(id = "t", title = "x", asapSince = 1_234L)
        assertEquals(1_234L, applied(already) { put("asap", true) }.asapSince)
        assertNull(applied(already) { put("asap", false) }.asapSince)
        assertNull(applied(already) { put("asap", JsonNull) }.asapSince)
    }

    @Test
    fun `a todo is ASAP or due on a date, not both`() {
        assertFailsWith<ToolError> {
            applied(Todo(id = "t", title = "x")) { put("asap", true); put("due_date", "2026-10-26") }
        }
        // an ASAP todo given a date without saying it stops being ASAP
        assertFailsWith<ToolError> {
            applied(Todo(id = "t", title = "x", asapSince = 5L)) { put("due_date", "2026-10-26") }
        }
        // ... is fine when it does
        val dated = applied(Todo(id = "t", title = "x", asapSince = 5L)) { put("asap", false); put("due_date", "2026-10-26") }
        assertNull(dated.asapSince)
        assertEquals(monday, dated.deadline)
    }

    @Test
    fun `a finished ASAP todo can be given a date`() {
        val done = Todo(id = "t", title = "x", asapSince = 5L, state = TodoState.COMPLETE, completedAt = 9L)
        assertEquals(monday, applied(done) { put("due_date", "2026-10-26") }.deadline)
    }
}
