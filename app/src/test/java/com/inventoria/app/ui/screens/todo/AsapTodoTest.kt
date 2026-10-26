package com.inventoria.app.ui.screens.todo

import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoRepeat
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.model.asapElapsedMillis
import com.inventoria.app.data.model.formatAsapElapsed
import com.inventoria.app.data.model.isAsap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AsapTodoTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis

    private val now = day(2026, 10, 26) + 10 * 3_600_000L
    private val today = day(2026, 10, 26)

    private fun todo(
        id: String,
        asapSince: Long? = null,
        deadline: Long? = null,
        state: TodoState = TodoState.INCOMPLETE,
        parent: String? = null,
        completedAt: Long? = null,
        createdAt: Long = 0L
    ) = Todo(
        id = id, title = id, asapSince = asapSince, deadline = deadline, state = state,
        parentTodoId = parent, completedAt = completedAt, createdAt = createdAt
    )

    private fun ids(entries: List<TodoTreeEntry>) = entries.map { it.todo.id }

    // --- the todo itself ---

    @Test
    fun `an open tagged todo is ASAP, a finished or untagged one is not`() {
        assertTrue(todo("a", asapSince = 5L).isAsap)
        assertFalse(todo("a", asapSince = 5L, state = TodoState.COMPLETE, completedAt = 9L).isAsap)
        assertFalse(todo("a").isAsap)
    }

    @Test
    fun `the timer counts up while open and freezes at completion`() {
        assertEquals(4_000L, todo("a", asapSince = 1_000L).asapElapsedMillis(5_000L))
        assertEquals(
            2_000L,
            todo("a", asapSince = 1_000L, state = TodoState.COMPLETE, completedAt = 3_000L).asapElapsedMillis(99_000L)
        )
        assertNull(todo("a").asapElapsedMillis(5_000L))
        // a clock that went backwards never gives a negative time
        assertEquals(0L, todo("a", asapSince = 9_000L).asapElapsedMillis(5_000L))
    }

    @Test
    fun `elapsed time is shown coarser the longer it is`() {
        assertEquals("0s", formatAsapElapsed(0))
        assertEquals("0s", formatAsapElapsed(-5_000))
        assertEquals("45s", formatAsapElapsed(45_000))
        assertEquals("14m 05s", formatAsapElapsed(14 * 60_000L + 5_000))
        assertEquals("59m 59s", formatAsapElapsed(3_599_000))
        assertEquals("1h 0m", formatAsapElapsed(3_600_000))
        assertEquals("3h 12m", formatAsapElapsed(3 * 3_600_000L + 12 * 60_000L + 30_000))
        assertEquals("2d 4h", formatAsapElapsed(2 * 86_400_000L + 4 * 3_600_000L + 59 * 60_000L))
    }

    // --- where it is listed ---

    @Test
    fun `the ASAP section holds open ASAP todos, longest waiting first`() {
        val all = listOf(
            todo("late", asapSince = 2_000L),
            todo("early", asapSince = 1_000L),
            todo("plain"),
            todo("done", asapSince = 500L, state = TodoState.COMPLETE, completedAt = 600L)
        )
        assertEquals(listOf("early", "late"), ids(TodoSections.asap(all)))
    }

    @Test
    fun `sub-todos without a date of their own follow their ASAP parent`() {
        val all = listOf(
            todo("parent", asapSince = 1_000L),
            todo("child", parent = "parent"),
            todo("dated child", parent = "parent", deadline = today),
            todo("elsewhere")
        )
        assertEquals(listOf("parent", "child"), ids(TodoSections.asap(all)))
        // the dated child stays on its own day
        assertEquals(listOf("dated child"), ids(TodoSections.build(all, nowMillis = now).single().visibleTodos))
    }

    @Test
    fun `an ASAP todo is not listed again under a day or under no deadline`() {
        val all = listOf(
            todo("asap", asapSince = 1_000L),
            todo("child", parent = "asap"),
            todo("dated", deadline = today),
            todo("plain")
        )
        assertEquals(listOf("dated"), TodoSections.build(all, nowMillis = now).flatMap { ids(it.visibleTodos) })
        assertEquals(listOf("plain"), ids(TodoSections.undated(all, nowMillis = now)))
    }

    @Test
    fun `a finished ASAP todo drops to the plain list and keeps its time`() {
        val done = todo("done", asapSince = 1_000L, state = TodoState.COMPLETE, completedAt = 5_000L)
        assertTrue(TodoSections.asap(listOf(done)).isEmpty())
        assertEquals(listOf("done"), ids(TodoSections.undated(listOf(done), nowMillis = now)))
        assertEquals(4_000L, done.asapElapsedMillis(now))
    }

    @Test
    fun `a stray deadline does not pull an ASAP todo out of its section`() {
        val both = todo("both", asapSince = 1_000L, deadline = today)
        assertEquals(listOf("both"), ids(TodoSections.asap(listOf(both))))
        assertTrue(TodoSections.build(listOf(both), nowMillis = now).isEmpty())
    }

    @Test
    fun `hiding completed work keeps the ASAP todo that is still open`() {
        val all = listOf(todo("open", asapSince = 1_000L), todo("other", state = TodoState.COMPLETE, completedAt = 1L))
        assertEquals(listOf("open"), ids(TodoSections.asap(all, hideCompleted = true)))
    }

    @Test
    fun `today leads with what is ASAP`() {
        val all = listOf(todo("due today", deadline = today), todo("asap", asapSince = 1_000L))
        assertEquals(listOf("asap", "due today"), ids(TodoSections.today(all, nowMillis = now)))
    }

    @Test
    fun `soonest-first puts ASAP ahead of everything, undated last`() {
        val all = listOf(todo("undated"), todo("dated", deadline = today), todo("asap", asapSince = 1_000L))
        val sorted = all.sortedWith(TodoSortOption.DEADLINE_ASC.comparator(withinDay = false, sinkCompleted = false))
        assertEquals(listOf("asap", "dated", "undated"), sorted.map { it.id })
    }

    @Test
    fun `chosen weekdays are named on the row, plain weekly is not`() {
        assertEquals("Weekly", repeatRowText(TodoRepeat.WEEKLY, 0, 0))
        assertEquals("Mon, Fri", repeatRowText(TodoRepeat.WEEKLY, 0, 0, weekdays = 0b0010001))
        assertEquals("Daily", repeatRowText(TodoRepeat.DAILY, 0, 0, weekdays = 0b0010001))
    }
}
