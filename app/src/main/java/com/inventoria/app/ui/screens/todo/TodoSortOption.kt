package com.inventoria.app.ui.screens.todo

import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState

/** How todos are ordered within whatever they are grouped into -- the Todos screen's counterpart
 * of the Inventory screen's SortOption. DEADLINE_ASC is the default and is exactly the order the
 * list always had. Stored by name in settings. */
enum class TodoSortOption(val displayName: String) {
    DEADLINE_ASC("Deadline (soonest)"),
    DEADLINE_DESC("Deadline (latest)"),
    PRIORITY("Priority (A1 first)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    CREATED_DESC("Recently Created"),
    UPDATED_DESC("Recently Updated");

    /**
     * The order for this option, over a stable sort: ties keep the DAO's order, which is newest
     * first. Undated and unprioritised todos always go last, whichever way the rest runs.
     *
     * [withinDay] is the date-grouped list, where a day section already fixes the date and only
     * the time of day is left to order by -- comparing the full deadline there would let a carried
     * overdue todo jump ahead of today's own, which is not what the day view has ever done.
     * [sinkCompleted] pushes finished work to the end, for the lists with no day sections to do
     * that for it (past days sit at the bottom of the date view already).
     */
    fun comparator(withinDay: Boolean, sinkCompleted: Boolean): Comparator<Todo> {
        val base: Comparator<Todo> = when (this) {
            DEADLINE_ASC ->
                if (withinDay) compareBy<Todo> { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }
                else compareBy<Todo> { it.deadline == null }
                    .thenBy { it.deadline ?: 0L }
                    .thenBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }
            DEADLINE_DESC ->
                if (withinDay) compareBy<Todo> { it.deadlineMinuteOfDay == null }.thenByDescending { it.deadlineMinuteOfDay ?: 0 }
                else compareBy<Todo> { it.deadline == null }
                    .thenByDescending { it.deadline ?: 0L }
                    .thenBy { it.deadlineMinuteOfDay == null }
                    .thenByDescending { it.deadlineMinuteOfDay ?: 0 }
            PRIORITY -> compareBy<Todo> { it.priority == null }.thenBy { it.priority?.ordinal ?: 0 }
            NAME_ASC -> compareBy<Todo, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            NAME_DESC -> compareByDescending<Todo, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            CREATED_DESC -> compareByDescending<Todo> { it.createdAt }
            UPDATED_DESC -> compareByDescending<Todo> { it.updatedAt }
        }
        return if (sinkCompleted) compareBy<Todo> { it.state == TodoState.COMPLETE }.then(base) else base
    }
}

/** What the Todos list is cut into. DATE is the day sections (Today, upcoming days, past days)
 * and the default; NONE is one plain list. Stored by name in settings. */
enum class TodoGroupOption(val displayName: String) {
    DATE("Date"),
    NONE("No Grouping"),
    PRIORITY("Priority"),
    KIND("Kind")
}
