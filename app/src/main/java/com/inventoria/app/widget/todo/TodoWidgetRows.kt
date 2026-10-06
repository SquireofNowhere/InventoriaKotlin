package com.inventoria.app.widget.todo

import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.todo.TodoSections
import com.inventoria.app.ui.screens.todo.TodoTreeEntry
import com.inventoria.app.util.getStartOfDay
import java.util.Calendar

/**
 * A row of a todo widget: the tree entry as the Todos tab computes it, plus the day section it
 * sits under when the widget spans several days ([dayStart] is null for the Today widget, whose
 * one section needs no label).
 */
data class TodoWidgetRow(val entry: TodoTreeEntry, val dayStart: Long?)

/** What the todo widgets list, cut from the same [TodoSections] the Todos and Today tabs use. */
object TodoWidgetRows {

    /** How many days past today the Upcoming widget looks. */
    const val UPCOMING_DAYS = 7

    /** Today's section minus completed rows, as the Today's Todos widget shows it. */
    fun today(all: List<Todo>, nowMillis: Long = System.currentTimeMillis()): List<TodoWidgetRow> =
        TodoSections.today(all, hideCompleted = true, nowMillis = nowMillis).map { TodoWidgetRow(it, null) }

    /**
     * The days after today, soonest first, up to [UPCOMING_DAYS] ahead -- days with nothing due are
     * simply absent, and finished work is left out like it is on the Today widget. The day is
     * stamped on every row so the widget can label the root rows of each day.
     */
    fun upcoming(all: List<Todo>, nowMillis: Long = System.currentTimeMillis()): List<TodoWidgetRow> {
        val todayStart = getStartOfDay(nowMillis)
        // Calendar, not todayStart + 7 * 86_400_000: a day is 23 or 25 hours across a DST change.
        val horizon = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, UPCOMING_DAYS)
        }.timeInMillis
        return TodoSections.build(all, hideCompleted = true, nowMillis = nowMillis)
            .filter { it.dayStart > todayStart && it.dayStart <= horizon }
            .flatMap { section -> section.visibleTodos.map { TodoWidgetRow(it, section.dayStart) } }
    }

    /** How many of [rows] are past their day and still open -- the carry-over the Today widget flags. */
    fun overdueCount(rows: List<TodoWidgetRow>, nowMillis: Long = System.currentTimeMillis()): Int {
        val todayStart = getStartOfDay(nowMillis)
        return rows.count { row ->
            val deadline = row.entry.todo.deadline
            deadline != null && deadline < todayStart && row.entry.todo.state != TodoState.COMPLETE
        }
    }
}
