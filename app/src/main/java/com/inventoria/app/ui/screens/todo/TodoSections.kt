package com.inventoria.app.ui.screens.todo

import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.util.getStartOfDay

/** One flattened row in a todo hierarchy display -- [depth] and [parentName] mirror
 * TaskTrackerScreen's ActiveSessionTreeEntry (same DFS-pre-order-over-a-parent-pointer shape),
 * except a todo's parent can live in a different day section than the child (differing
 * deadlines), so [parentName] is populated even at depth 0 whenever the parent exists but isn't
 * part of the current scoped list -- the child still needs its "sub-todo of" breadcrumb even
 * though it can't be visually nested under a parent that isn't here. [childProgress] is
 * (completed, total) direct children, computed globally regardless of scope, so a parent shows
 * accurate progress even if some children are filed under a different day. [effectiveState] is
 * what should actually be displayed/clicked: [todo]'s own stored state, EXCEPT when it isn't
 * already COMPLETE and has at least one COMPLETE direct child, which displays as IN_PROGRESS
 * regardless of what's actually stored -- a live-computed override, never written back to the
 * todo itself. [hasVisibleChildren] is whether this entry would actually nest anything *in this
 * list* -- not whether the todo has children at all, since a child filed under a different day
 * cannot be folded away from here -- and [isCollapsed] is that fold being applied, which is why a
 * collapsed entry still reports [childProgress] (the only thing left saying what is hidden). */
data class TodoTreeEntry(
    val todo: Todo,
    val depth: Int,
    val parentName: String?,
    val childProgress: Pair<Int, Int>?,
    val effectiveState: TodoState,
    val hasVisibleChildren: Boolean = false,
    val isCollapsed: Boolean = false
)

/** One day's worth of dated todos. [visibleTodos] is what actually gets rendered under this
 * section -- for "Today" that also includes currently-overdue todos pulled in from other days
 * (carry-over, no cloning), while [totalDueCount]/[completedDueCount] stay keyed strictly to
 * todos whose OWN deadline is this day, so a day's completion percentage doesn't get skewed by
 * whatever happens to be visually parked under Today while overdue. */
data class TodoDaySection(
    val dayStart: Long,
    val visibleTodos: List<TodoTreeEntry>,
    val totalDueCount: Int,
    val completedDueCount: Int
)

/**
 * How the todo list is cut into day sections and nested into trees -- the one definition of
 * "what belongs under Today", shared by the Todos screen, the Today tab and the Today's Todos
 * home-screen widget, so all three agree to the row.
 *
 * Pure functions over a todo list: no repository, no clock beyond [nowMillis] (which defaults to
 * the wall clock and exists so a test or a midnight-rollover check can pin the day).
 */
object TodoSections {

    /**
     * Every dated section: Today first (including the overdue carry-over), then upcoming days
     * soonest first, then past days most recent first.
     *
     * [hideCompleted] and [collapsedIds] only ever remove *rows*. Everything a header counts --
     * childCounts, ownDueByDeadline, and the ancestor walk in effectiveSectionDay -- is computed
     * over the full list, so folding a branch away or hiding finished work never makes a day's
     * "X% Done" or a parent's "2/3 sub-todos complete" quietly disagree with reality.
     */
    fun build(
        all: List<Todo>,
        hideCompleted: Boolean = false,
        collapsedIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<TodoDaySection> {
        val todayStart = getStartOfDay(nowMillis)
        val byId = all.associateBy { it.id }
        val childCounts = computeChildCounts(all)

        // "Due" stats (the day header's X% Done bar) are strictly about todos with their OWN
        // deadline -- a deadline-less child inheriting a section from its parent doesn't make it
        // "due" that day for percentage purposes.
        val ownDueByDeadline = all.filter { it.deadline != null }.groupBy { it.deadline!! }

        val sectionDayById = all.associate { it.id to effectiveSectionDay(it, byId, todayStart) }
        val rendered = if (hideCompleted) withoutCompleted(all, byId) else all
        val bySectionDay = rendered.filter { sectionDayById[it.id] != null }.groupBy { sectionDayById[it.id]!! }

        fun sectionFor(day: Long): TodoDaySection {
            val ownForDay = ownDueByDeadline[day] ?: emptyList()
            return TodoDaySection(
                dayStart = day,
                visibleTodos = buildTodoTree(sortedByDeadlineTime(bySectionDay[day]!!), byId, childCounts, collapsedIds),
                totalDueCount = ownForDay.size,
                completedDueCount = ownForDay.count { it.state == TodoState.COMPLETE }
            )
        }

        val sections = mutableListOf<TodoDaySection>()

        if (bySectionDay[todayStart]?.isNotEmpty() == true) sections.add(sectionFor(todayStart))

        // Upcoming days, soonest first.
        bySectionDay.keys.filter { it > todayStart }.sorted().forEach { sections.add(sectionFor(it)) }

        // Past days, most recent first -- overdue-and-incomplete todos (and any deadline-less
        // children following them) already resolved to Today above via effectiveSectionDay, so
        // whatever's left here is exactly what's since been completed.
        bySectionDay.keys.filter { it < todayStart }.sortedDescending().forEach { sections.add(sectionFor(it)) }

        return sections
    }

    /** The Today section's rows alone, or nothing when nothing is due or overdue today. */
    fun today(
        all: List<Todo>,
        hideCompleted: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TodoTreeEntry> {
        val todayStart = getStartOfDay(nowMillis)
        return build(all, hideCompleted, nowMillis = nowMillis)
            .firstOrNull { it.dayStart == todayStart }
            ?.visibleTodos
            .orEmpty()
    }

    /** The "No Deadline" list: todos whose whole ancestor chain carries no deadline. */
    fun undated(
        all: List<Todo>,
        hideCompleted: Boolean = false,
        collapsedIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<TodoTreeEntry> {
        val byId = all.associateBy { it.id }
        val childCounts = computeChildCounts(all)
        val todayStart = getStartOfDay(nowMillis)
        val undated = (if (hideCompleted) withoutCompleted(all, byId) else all)
            .filter { effectiveSectionDay(it, byId, todayStart) == null }
        return buildTodoTree(undated, byId, childCounts, collapsedIds)
    }

    private fun computeChildCounts(all: List<Todo>): Map<String, Pair<Int, Int>> =
        all.filter { it.parentTodoId != null }
            .groupBy { it.parentTodoId!! }
            .mapValues { (_, children) -> children.count { it.state == TodoState.COMPLETE } to children.size }

    /** DFS pre-order over [scoped], grouping strictly within it (a child only nests under its
     * parent when both share this scope, e.g. the same day section) -- copies
     * TaskTrackerScreen's buildActiveSessionTree shape exactly: group by parent pointer, roots
     * are items whose parent is null OR outside the current scope, recurse depth-first. */
    private fun buildTodoTree(
        scoped: List<Todo>,
        allTodosById: Map<String, Todo>,
        childCounts: Map<String, Pair<Int, Int>>,
        collapsedIds: Set<String> = emptySet()
    ): List<TodoTreeEntry> {
        val scopedIds = scoped.map { it.id }.toSet()
        val childrenByParentId = scoped.groupBy { it.parentTodoId }
        val result = mutableListOf<TodoTreeEntry>()

        fun visit(todo: Todo, depth: Int) {
            val parentName = todo.parentTodoId?.let { allTodosById[it]?.title }
            val progress = childCounts[todo.id]
            // Any completed direct child reads as "in progress" (unless this todo is already
            // COMPLETE itself, which is never downgraded) -- purely a display computation, never
            // written back. Covers both a genuine partial mix and the "only child, now done" case.
            val effectiveState = if (todo.state != TodoState.COMPLETE && progress != null && progress.first >= 1) {
                TodoState.IN_PROGRESS
            } else {
                todo.state
            }
            // Children *in this list*, which is not the same as children at all: one filed under a
            // different day is not something this row can fold away, so it must not offer to.
            val children = childrenByParentId[todo.id].orEmpty()
            val collapsed = children.isNotEmpty() && todo.id in collapsedIds
            result.add(
                TodoTreeEntry(todo, depth, parentName, progress, effectiveState, children.isNotEmpty(), collapsed)
            )
            if (!collapsed) children.forEach { child -> visit(child, depth + 1) }
        }

        scoped.filter { it.parentTodoId == null || it.parentTodoId !in scopedIds }
            .forEach { visit(it, 0) }
        return result
    }

    /** Which day a todo visually belongs to: its own deadline if it has one -- redirected to
     * today if that deadline has passed and it's still incomplete (the overdue carry-over) --
     * else walks up parentTodoId until it finds a dated ancestor and inherits THAT ancestor's
     * resolved day (so a deadline-less child sits with its parent, including following the
     * parent into Today if the parent itself is overdue), else null (the "No Deadline" section,
     * only reached when nothing in the whole ancestor chain has a deadline at all).
     *
     * A *completed* sub-todo also defers to its parent, even when it has a deadline of its own.
     * Ticking one off otherwise tore it out of the group it belonged to and refiled it alone under
     * its own date, which is precisely the moment the hierarchy is most worth keeping intact: the
     * parent's "2/3 sub-todos complete" line pointed at rows that had scattered across the list.
     * Only display moves -- the day header's own X% Done still counts it against the day it was
     * genuinely due, which is why [build] keys those counts off `deadline` directly. */
    private fun effectiveSectionDay(todo: Todo, byId: Map<String, Todo>, todayStart: Long): Long? {
        var current: Todo? = todo
        // parentTodoId cycles are prevented when parents are assigned (see
        // TodoViewModel.invalidParentIds), but a cycle arriving from sync has no such gate and
        // would spin here forever.
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.id)) {
            val parent = current.parentTodoId?.let { byId[it] }
            val defersToParent = current.state == TodoState.COMPLETE && parent != null
            val deadline = current.deadline
            if (deadline != null && !defersToParent) {
                return if (current.state != TodoState.COMPLETE && deadline < todayStart) todayStart else deadline
            }
            current = parent
        }
        return null
    }

    /**
     * Drops completed todos, keeping any that still have unfinished work hanging off them.
     *
     * A completed parent is not noise while one of its children is outstanding -- it is the thing
     * that child is nested under, and removing it would strand the child at the top level looking
     * unrelated to anything. So the rule is "keep every incomplete todo, and every ancestor of
     * one", rather than a flat state filter.
     */
    private fun withoutCompleted(all: List<Todo>, byId: Map<String, Todo>): List<Todo> {
        val keep = mutableSetOf<String>()
        all.filter { it.state != TodoState.COMPLETE }.forEach { todo ->
            var current: Todo? = todo
            // add() returning false means this ancestor chain has already been walked -- which also
            // stops a synced parent cycle from looping.
            while (current != null && keep.add(current.id)) {
                current = current.parentTodoId?.let { byId[it] }
            }
        }
        return all.filter { it.id in keep }
    }

    /** Within a day, todos carrying a deadline time come first in chronological order, all-day
     * ones after them. sortedBy is stable, so everything untimed keeps the DAO's createdAt DESC
     * order untouched, and so do timed todos sharing a minute. */
    private fun sortedByDeadlineTime(todos: List<Todo>): List<Todo> =
        todos.sortedBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }
}
