package com.inventoria.app.ui.screens.todo

import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.model.TodoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoSortGroupTest {

    private fun todo(
        id: String,
        deadline: Long? = null,
        minute: Int? = null,
        priority: TodoPriority? = null,
        state: TodoState = TodoState.INCOMPLETE,
        kind: TaskKind = TaskKind.GRAPHITE,
        parent: String? = null,
        title: String = id
    ) = Todo(
        id = id, title = title, deadline = deadline, deadlineMinuteOfDay = minute, priority = priority,
        state = state, kind = kind, parentTodoId = parent
    )

    private fun ids(list: List<Todo>, sort: TodoSortOption, withinDay: Boolean = false, sink: Boolean = true) =
        list.sortedWith(sort.comparator(withinDay, sink)).map { it.id }

    @Test
    fun `soonest deadline first with undated last`() {
        val list = listOf(todo("none"), todo("late", deadline = 300), todo("soon", deadline = 100))
        assertEquals(listOf("soon", "late", "none"), ids(list, TodoSortOption.DEADLINE_ASC))
    }

    @Test
    fun `latest deadline first still leaves undated last`() {
        val list = listOf(todo("none"), todo("late", deadline = 300), todo("soon", deadline = 100))
        assertEquals(listOf("late", "soon", "none"), ids(list, TodoSortOption.DEADLINE_DESC))
    }

    @Test
    fun `finished work sinks to the end when asked`() {
        val list = listOf(todo("done", deadline = 100, state = TodoState.COMPLETE), todo("open", deadline = 200))
        assertEquals(listOf("open", "done"), ids(list, TodoSortOption.DEADLINE_ASC))
        assertEquals(listOf("done", "open"), ids(list, TodoSortOption.DEADLINE_ASC, sink = false))
    }

    @Test
    fun `within a day only the time of day orders, untimed last and ties keep their order`() {
        val list = listOf(todo("allday1"), todo("noon", minute = 720), todo("allday2"), todo("morning", minute = 480))
        assertEquals(
            listOf("morning", "noon", "allday1", "allday2"),
            ids(list, TodoSortOption.DEADLINE_ASC, withinDay = true, sink = false)
        )
    }

    @Test
    fun `priority runs A1 to C3 with unprioritised last`() {
        val list = listOf(todo("none"), todo("c", priority = TodoPriority.C1), todo("a", priority = TodoPriority.A1))
        assertEquals(listOf("a", "c", "none"), ids(list, TodoSortOption.PRIORITY))
    }

    @Test
    fun `names sort without regard to case`() {
        val list = listOf(todo("1", title = "banana"), todo("2", title = "Apple"), todo("3", title = "cherry"))
        assertEquals(listOf("2", "1", "3"), ids(list, TodoSortOption.NAME_ASC))
        assertEquals(listOf("3", "1", "2"), ids(list, TodoSortOption.NAME_DESC))
    }

    @Test
    fun `no grouping is one list without a header`() {
        val groups = TodoSections.grouped(
            listOf(todo("a", deadline = 100), todo("b")), TodoGroupOption.NONE, TodoSortOption.DEADLINE_ASC
        )
        assertEquals(1, groups.size)
        assertNull(groups[0].title)
        assertEquals(listOf("a", "b"), groups[0].entries.map { it.todo.id })
    }

    @Test
    fun `priority groups run A to C and the unprioritised come last`() {
        val groups = TodoSections.grouped(
            listOf(
                todo("none"),
                todo("b2", priority = TodoPriority.B2),
                todo("a1", priority = TodoPriority.A1),
                todo("a3", priority = TodoPriority.A3)
            ),
            TodoGroupOption.PRIORITY,
            TodoSortOption.PRIORITY
        )
        assertEquals(listOf("Priority A", "Priority B", "No priority"), groups.map { it.title })
        assertEquals(listOf("a1", "a3"), groups[0].entries.map { it.todo.id })
    }

    @Test
    fun `a sub-todo in another group keeps a breadcrumb to its parent`() {
        val groups = TodoSections.grouped(
            listOf(
                todo("parent", priority = TodoPriority.A1, title = "Parent"),
                todo("child", priority = TodoPriority.B1, parent = "parent")
            ),
            TodoGroupOption.PRIORITY,
            TodoSortOption.PRIORITY
        )
        val child = groups.first { it.title == "Priority B" }.entries.single()
        assertEquals(0, child.depth)
        assertEquals("Parent", child.parentName)
    }

    @Test
    fun `a sub-todo in the same list nests under its parent whatever the sort`() {
        val groups = TodoSections.grouped(
            listOf(todo("z-child", parent = "a-parent", title = "Z"), todo("a-parent", title = "A"), todo("m", title = "M")),
            TodoGroupOption.NONE,
            TodoSortOption.NAME_DESC
        )
        assertEquals(listOf("m", "a-parent", "z-child"), groups[0].entries.map { it.todo.id })
        assertEquals(listOf(0, 0, 1), groups[0].entries.map { it.depth })
    }

    @Test
    fun `hiding completed drops finished todos from every group`() {
        val groups = TodoSections.grouped(
            listOf(todo("open"), todo("done", state = TodoState.COMPLETE)),
            TodoGroupOption.NONE,
            TodoSortOption.NAME_ASC,
            hideCompleted = true
        )
        assertEquals(listOf("open"), groups.single().entries.map { it.todo.id })
    }
}
