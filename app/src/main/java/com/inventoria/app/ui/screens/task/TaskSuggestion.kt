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

    /**
     * A previously-used task name. Carries its own group and kind, as before, plus whichever type
     * that *name* has settled on -- see [modalTypeIdFor]. [typeId] is null for a name with no
     * majority type yet, which is every name in a history recorded before Task Types existed;
     * those simply autofill without a type until enough newly-typed instances outvote them.
     *
     * [typeName] is that type's display name, resolved at build time purely so the dropdown row
     * can show it without re-resolving; [typeId] is what actually gets stamped.
     */
    data class Recent(
        override val label: String,
        val groupId: String,
        val kind: TaskKind,
        val typeId: String?,
        val typeName: String?
    ) : TaskSuggestion
}

/**
 * The type a name has settled on: the most common taskTypeId across every task carrying that name.
 *
 * Untyped tasks vote too, deliberately. A name with a long pre-Task-Types history therefore keeps
 * autofilling with no type until enough newly-typed instances outnumber the untyped ones -- the
 * type has to be *earned* rather than flipped by a single tap. The flip side is that a brand new
 * name gets its type immediately: one task, one vote, done.
 *
 * Ties go to a real type over "untyped", then to whichever was used most recently -- so the moment
 * a name draws level it starts suggesting something, rather than sitting on null forever.
 */
fun modalTypeIdFor(tasksWithSameName: List<Task>): String? =
    tasksWithSameName
        .groupBy { it.taskTypeId }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String?, List<Task>>> { it.value.size }
                .thenByDescending { it.key != null }
                .thenByDescending { entry -> entry.value.maxOf { it.startTime } }
        )
        .firstOrNull()
        ?.key

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

    // Grouped rather than distinctBy'd so each name's whole history is on hand for the type vote.
    // Every filter here is name-based, so a name is either wholly in or wholly out -- the group is
    // that name's complete history, not just the part that happened to match first.
    val recents = allTasks
        .filter {
            !it.isDeleted &&
                it.name.isNotBlank() &&
                !it.name.startsWith("Task ") &&
                !it.name.equals("untitled", ignoreCase = true) &&
                it.name.contains(query, ignoreCase = true) &&
                !it.name.equals(query, ignoreCase = true)
        }
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { sameName ->
            // Label/group/kind still come from the first occurrence, exactly as the old
            // distinctBy did; only the type is a whole-history calculation.
            val first = sameName.first()
            val typeId = modalTypeIdFor(sameName)
            TaskSuggestion.Recent(
                label = first.name.trim(),
                groupId = first.groupId,
                kind = first.kind,
                typeId = typeId,
                typeName = typeId?.let { id -> taskTypes.firstOrNull { it.id == id }?.name }
            )
        }
        .take(limit)

    return types + recents
}

/**
 * Renders a Type row so it reads as a different tier from the plain recent-name rows beneath it --
 * leading icon, bold label, and the Kind it will prefill. Without this the two tiers look
 * identical and the ordering alone doesn't communicate that one stamps a type and one doesn't.
 */
/**
 * Renders a Recent row with the type that name has settled on trailing it, so the type a pick is
 * about to stamp is visible before the tap rather than only afterwards on the card. Names with no
 * majority type yet render exactly as they always did -- a bare label.
 */
@Composable
fun RecentSuggestionLabel(suggestion: TaskSuggestion.Recent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(suggestion.label)
        suggestion.typeName?.let { name ->
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Category,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
