package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

@Entity
data class Todo(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    // Date-only (start-of-day millis); null means no deadline, never overdue, never penalized.
    @get:PropertyName("deadline") @set:PropertyName("deadline") var deadline: Long? = null,
    // Hierarchy: unlimited depth via self-reference, GitHub-Projects-style sub-todos.
    @get:PropertyName("parentTodoId") @set:PropertyName("parentTodoId") var parentTodoId: String? = null,
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted") var isCompleted: Boolean = false,
    @get:PropertyName("completedAt") @set:PropertyName("completedAt") var completedAt: Long? = null,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    // Set while a Task started from this todo's Start button is running/paused; cleared once its
    // session stops and the user answers the completion check-in (whichever way).
    @get:PropertyName("activeSessionGroupId") @set:PropertyName("activeSessionGroupId") var activeSessionGroupId: String? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
)
