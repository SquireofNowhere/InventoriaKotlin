package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

/** COMPLETE is only ever set directly (by the user, or a cascade from a completed ancestor).
 * IN_PROGRESS is a weaker "something above/below this says it's started" signal: a cascade from
 * a completed/un-completed ancestor lands here (never straight to COMPLETE), and a todo with a
 * genuine mix of complete/incomplete direct children *displays* as this too, without it ever
 * being written to that parent's own stored state -- see TodoViewModel.buildTodoTree's
 * effectiveState computation. */
enum class TodoState { INCOMPLETE, IN_PROGRESS, COMPLETE }

/** Franklin-Covey-style ABC-123 priority: letter tier (A most important) nested with a number
 * sub-rank (1 highest within its tier), forming one ordered scale from A1 (best) to C3 (worst).
 * Declared in that exact order so .ordinal doubles as the ranking -- see
 * TaskTrackerViewModel.categoryScoreToday's procrastination-penalty cutoff comparison. */
enum class TodoPriority { A1, A2, A3, B1, B2, B3, C1, C2, C3 }

@Entity
data class Todo(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    // The activity this todo is an instance of, same tier as Task.taskTypeId -- carried onto the
    // task when one is started from here, so work run out of the Todos screen lands typed instead
    // of voting "untyped" against its own name (see modalTypeIdFor). Null falls back to whatever
    // that name has already settled on.
    @get:PropertyName("taskTypeId") @set:PropertyName("taskTypeId") var taskTypeId: String? = null,
    // Date-only (start-of-day millis); null means no deadline, never overdue, never penalized.
    @get:PropertyName("deadline") @set:PropertyName("deadline") var deadline: Long? = null,
    // Optional time-of-day for that deadline, as minutes since midnight (0..1439); null means an
    // all-day deadline. Deliberately NOT folded into [deadline] itself: that field doubles as the
    // day-section grouping key (TodoViewModel.buildTodoSections groups on it directly) and as the
    // basis for the whole-days-overdue procrastination penalty, both of which only work while it
    // stays exactly a start-of-day value. Meaningless (and always cleared) when deadline is null.
    @get:PropertyName("deadlineMinuteOfDay") @set:PropertyName("deadlineMinuteOfDay") var deadlineMinuteOfDay: Int? = null,
    // Null means unprioritized -- always counts as procrastination if that penalty is enabled,
    // regardless of the configured cutoff tier.
    @get:PropertyName("priority") @set:PropertyName("priority") var priority: TodoPriority? = null,
    // Hierarchy: unlimited depth via self-reference, GitHub-Projects-style sub-todos.
    @get:PropertyName("parentTodoId") @set:PropertyName("parentTodoId") var parentTodoId: String? = null,
    @get:PropertyName("state") @set:PropertyName("state") var state: TodoState = TodoState.INCOMPLETE,
    // Only meaningful (non-null) while state == COMPLETE.
    @get:PropertyName("completedAt") @set:PropertyName("completedAt") var completedAt: Long? = null,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    // VESTIGIAL as of v2.12 -- nothing reads or writes this any more. "Is a session running for
    // this todo" is derived from the tasks themselves (TodoViewModel.todoIdsWithActiveSession),
    // because a stored pointer could not stay right: it survived a session being deleted rather
    // than stopped, stranding the todo as permanently in-progress with no Start button, and it
    // made two independently-synced entities each responsible for half of one fact.
    // The column stays only because dropping it needs a full Todo table rebuild (SQLite cannot
    // reliably DROP COLUMN across the API levels this app supports) -- old rows keep whatever
    // value they last had, and it is simply ignored.
    @get:PropertyName("activeSessionGroupId") @set:PropertyName("activeSessionGroupId") var activeSessionGroupId: String? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
)
