package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats

/**
 * One row in a name-field autofill dropdown.
 *
 * Replaces the old `Triple<String, String, TaskKind>` -- adding the type tier would have made it a
 * four-field tuple whose members are only distinguishable by position, and the two tiers now need
 * genuinely different click behaviour anyway.
 */
sealed interface TaskSuggestion {
    val label: String

    /**
     * A user-managed Task Type ("Eating"). Picking one stamps the type on the task and prefills
     * [mostUsedKind] when that type has history to learn from; the name stays for the user to
     * specialise ("Eating with V").
     */
    data class Type(
        val typeId: String,
        override val label: String,
        val mostUsedKind: TaskKind?,
        val taskCount: Int
    ) : TaskSuggestion

    /** A previously-used task name. Carries its own group and kind, as before. */
    data class Recent(
        override val label: String,
        val groupId: String,
        val kind: TaskKind
    ) : TaskSuggestion
}

/**
 * Types first, then recent names -- the point of the feature is to reach for the activity before
 * the wording. Within each tier, matches are ordered by how heavily used they are so the habitual
 * choices float up.
 *
 * [limit] applies per tier rather than to the combined list, so a flood of recent names can never
 * push every type off the bottom of the dropdown.
 */
fun buildTaskSuggestions(
    query: String,
    taskTypes: List<TaskType>,
    taskTypeStats: Map<String, TaskTypeStats>,
    allTasks: List<Task>,
    limit: Int = 5
): List<TaskSuggestion> {
    if (query.isBlank()) return emptyList()

    val types = taskTypes
        .filter { it.name.contains(query, ignoreCase = true) && !it.name.equals(query, ignoreCase = true) }
        .map { type ->
            val stats = taskTypeStats[type.id]
            TaskSuggestion.Type(
                typeId = type.id,
                label = type.name,
                mostUsedKind = stats?.mostUsedKind,
                taskCount = stats?.taskCount ?: 0
            )
        }
        .sortedByDescending { it.taskCount }
        .take(limit)

    val recents = allTasks
        .filter {
            !it.isDeleted &&
                it.name.isNotBlank() &&
                !it.name.startsWith("Task ") &&
                !it.name.equals("untitled", ignoreCase = true) &&
                it.name.contains(query, ignoreCase = true) &&
                !it.name.equals(query, ignoreCase = true)
        }
        .distinctBy { it.name.trim().lowercase() }
        .map { TaskSuggestion.Recent(it.name.trim(), it.groupId, it.kind) }
        .take(limit)

    return types + recents
}

/**
 * Renders a Type row so it reads as a different tier from the plain recent-name rows beneath it --
 * leading icon, bold label, and the Kind it will prefill. Without this the two tiers look
 * identical and the ordering alone doesn't communicate that one stamps a type and one doesn't.
 */
@Composable
fun TaskTypeSuggestionLabel(suggestion: TaskSuggestion.Type) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Category,
            contentDescription = "Task type",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(suggestion.label, fontWeight = FontWeight.Bold)
        suggestion.mostUsedKind?.let { kind ->
            Spacer(Modifier.width(6.dp))
            Text(
                text = kind.displayName.substringBefore(" •"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
