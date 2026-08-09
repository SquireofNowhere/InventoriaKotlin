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

@Entity
data class Todo(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    // Date-only (start-of-day millis); null means no deadline, never overdue, never penalized.
    @get:PropertyName("deadline") @set:PropertyName("deadline") var deadline: Long? = null,
    // Hierarchy: unlimited depth via self-reference, GitHub-Projects-style sub-todos.
    @get:PropertyName("parentTodoId") @set:PropertyName("parentTodoId") var parentTodoId: String? = null,
    @get:PropertyName("state") @set:PropertyName("state") var state: TodoState = TodoState.INCOMPLETE,
    // Only meaningful (non-null) while state == COMPLETE.
    @get:PropertyName("completedAt") @set:PropertyName("completedAt") var completedAt: Long? = null,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    // Set while a Task started from this todo's Start button is running/paused; cleared once its
    // session stops and the user answers the completion check-in (whichever way).
    @get:PropertyName("activeSessionGroupId") @set:PropertyName("activeSessionGroupId") var activeSessionGroupId: String? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
)
