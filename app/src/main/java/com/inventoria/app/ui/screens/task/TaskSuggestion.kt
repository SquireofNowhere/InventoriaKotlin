package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TaskTypeStats
import com.inventoria.app.data.model.modalKindFor
import com.inventoria.app.data.model.modalTypeIdFor

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
     * A previously-used task name, and the kind and type that name has been carrying. Picking one
     * copies those three values onto the current session; it deliberately does NOT carry a group
     * id, because picking a name is no longer allowed to move a session into another session's
     * group -- see TaskRepository.updateSessionName.
     *
     * [typeId] is null for a name with no majority type yet ([modalTypeIdFor]), which is every name
     * in a history recorded before Task Types existed; those autofill without a type until enough
     * newly-typed instances outvote them. [typeName] is that type's display name, resolved at build
     * time purely so the dropdown row can show it; [typeId] is what gets stamped.
     */
    data class Recent(
        override val label: String,
        val kind: TaskKind,
        val typeId: String?,
        val typeName: String?
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
            // Only the label comes from the first occurrence; kind and type are both
            // whole-history calculations, so one odd retag doesn't become the name's identity.
            val first = sameName.first()
            val typeId = modalTypeIdFor(sameName)
            TaskSuggestion.Recent(
                label = first.name.trim(),
                kind = modalKindFor(sameName) ?: first.kind,
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

/**
 * The one task-name text field with the autofill dropdown attached, so every place a task gets
 * named offers the same suggestions with the same matching, ordering, and row rendering. The
 * active-session card and interruption dialog predate this and keep their own wiring (they need
 * TextFieldValue selection control and save-on-blur races handled); every dialog-hosted name
 * field goes through here.
 *
 * The field owns only the dropdown lifecycle (focus-gated, reopens on the next keystroke after a
 * dismiss -- same rules as the card). What a pick *stamps* is the caller's business via
 * [onPickType]/[onPickRecent], because it differs by context: a field naming a brand-new task
 * carries kind and type along; a field renaming an existing record may only want the label. The
 * label itself is always written through [onValueChange] before the pick callback runs.
 *
 * Focus is deliberately not cleared on a pick, matching the interruption dialog: after a Type
 * pick the user is expected to keep typing the specific name ("Eating" -> "Eating with V").
 */
@Composable
fun TaskNameAutofillField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    taskTypes: List<TaskType>,
    taskTypeStats: Map<String, TaskTypeStats>,
    suggestionSourceTasks: List<Task>,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onPickType: (TaskSuggestion.Type) -> Unit = {},
    onPickRecent: (TaskSuggestion.Recent) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    var dropdownDismissedByUser by remember { mutableStateOf(false) }
    LaunchedEffect(value) { dropdownDismissedByUser = false }
    val suggestions = remember(value, isFocused, dropdownDismissedByUser, taskTypes, taskTypeStats, suggestionSourceTasks) {
        if (!isFocused || dropdownDismissedByUser) emptyList()
        else buildTaskSuggestions(value, taskTypes, taskTypeStats, suggestionSourceTasks)
    }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = fieldModifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { dropdownDismissedByUser = true },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        when (suggestion) {
                            is TaskSuggestion.Type -> TaskTypeSuggestionLabel(suggestion)
                            is TaskSuggestion.Recent -> RecentSuggestionLabel(suggestion)
                        }
                    },
                    onClick = {
                        onValueChange(suggestion.label)
                        dropdownDismissedByUser = true
                        when (suggestion) {
                            is TaskSuggestion.Type -> onPickType(suggestion)
                            is TaskSuggestion.Recent -> onPickRecent(suggestion)
                        }
                    }
                )
            }
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
