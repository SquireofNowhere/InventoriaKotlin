package com.inventoria.app.widget.todo

import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.util.getStartOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TodoWidgetRowsTest {

    // Noon on a fixed day, so "today" is pinned whatever the machine's clock says.
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 7, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val todayStart = getStartOfDay(now)

    private fun dayOffset(days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun todo(id: String, dayOffset: Int? = null, state: TodoState = TodoState.INCOMPLETE, parent: String? = null) =
        Todo(id = id, title = id, deadline = dayOffset?.let { dayOffset(it) }, state = state, parentTodoId = parent)

    @Test
    fun `upcoming is the days after today, soonest first, each row stamped with its day`() {
        val all = listOf(todo("later", 5), todo("tomorrow", 1), todo("today", 0), todo("undated"))
        val rows = TodoWidgetRows.upcoming(all, now)
        assertEquals(listOf("tomorrow", "later"), rows.map { it.entry.todo.id })
        assertEquals(listOf(dayOffset(1), dayOffset(5)), rows.map { it.dayStart })
    }

    @Test
    fun `upcoming stops a week out and leaves finished work off`() {
        val all = listOf(
            todo("edge", TodoWidgetRows.UPCOMING_DAYS),
            todo("beyond", TodoWidgetRows.UPCOMING_DAYS + 1),
            todo("done", 2, state = TodoState.COMPLETE)
        )
        assertEquals(listOf("edge"), TodoWidgetRows.upcoming(all, now).map { it.entry.todo.id })
    }

    @Test
    fun `a sub-todo nests under its parent on the same day`() {
        val all = listOf(todo("parent", 2), todo("child", 2, parent = "parent"))
        val rows = TodoWidgetRows.upcoming(all, now)
        assertEquals(listOf("parent", "child"), rows.map { it.entry.todo.id })
        assertEquals(listOf(0, 1), rows.map { it.entry.depth })
    }

    @Test
    fun `today rows carry no day label`() {
        val rows = TodoWidgetRows.today(listOf(todo("today", 0), todo("tomorrow", 1)), now)
        assertEquals(listOf("today"), rows.map { it.entry.todo.id })
        assertTrue(rows.all { it.dayStart == null })
    }

    @Test
    fun `overdue counts open rows from before today only`() {
        val all = listOf(todo("late", -3), todo("lateDone", -2, state = TodoState.COMPLETE), todo("today", 0))
        val rows = TodoWidgetRows.today(all, now)
        assertEquals(listOf("late", "today"), rows.map { it.entry.todo.id })
        assertEquals(1, TodoWidgetRows.overdueCount(rows, now))
    }
}
