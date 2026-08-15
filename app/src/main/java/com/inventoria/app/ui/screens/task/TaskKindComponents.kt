package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.theme.Success

@Composable
fun TaskKindChip(
    kind: TaskKind,
    modifier: Modifier = Modifier
) {
    val jColor = Color(kind.colorValue)
    val backgroundColor = jColor.copy(alpha = 0.15f)
    val contentColor = Color(kind.colorValue)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            
            val label = if (kind.displayName.contains(" • ")) {
                kind.displayName.split(" • ").last()
            } else {
                kind.displayName.substringAfter(" ").trim()
            }
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (kind.productivityValue != 0) {
                Text(
                    text = if (kind.productivityValue > 0) "+${kind.productivityValue}" else kind.productivityValue.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (kind.productivityValue > 0) Success else Color(0xFFFF4D4D)
                )
            }
        }
    }
}

/**
 * A task's Type ("Eating"), rendered as a small caption sitting above the task's own name so the
 * two tiers read as separate things: the type is the shared activity, the name is this particular
 * instance of it ("Eating with V"). Without this the type was stored and used for autofill and
 * reporting but never actually shown on a card, so a task looked like nothing but a free-text name.
 *
 * Deliberately quieter than [TaskKindChip] -- the Kind owns a color and a score and earns a chip;
 * the type is a plain label and shouldn't compete with the name it sits above.
 */
@Composable
fun TaskTypeLabel(
    typeName: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Category,
            contentDescription = "Task type",
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = typeName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Picker for a task's Type, anchored on the [TaskTypeLabel] itself -- same "the chip is the
 * button" pattern [TaskKindDropdownMenu] uses, so the thing you read is the thing you tap.
 *
 * Shows "Set type" when there's nothing to display, which is the whole point of the control: until
 * this existed a type could only be stamped by picking a Type row out of the name autocomplete, so
 * a task that was named any other way could never be typed at all, and a wrong type could never be
 * corrected. Selecting null clears it.
 *
 * A type that's been soft-deleted resolves to no name and so reads as "Set type" -- the id stays on
 * the task either way, matching how the cards render it.
 */
@Composable
fun TaskTypeDropdownMenu(
    selectedTypeId: String?,
    taskTypes: List<TaskType>,
    onTypeSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = selectedTypeId?.let { id -> taskTypes.firstOrNull { it.id == id }?.name }

    Box(modifier = modifier) {
        TaskTypeLabel(
            typeName = selectedName ?: "Set type",
            modifier = if (enabled) Modifier.clickable { expanded = true } else Modifier
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        "No type",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { onTypeSelected(null); expanded = false }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            taskTypes.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = type.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (type.id == selectedTypeId) FontWeight.Bold else null
                        )
                    },
                    onClick = { onTypeSelected(type.id); expanded = false }
                )
            }
        }
    }
}

@Composable
fun TaskKindDropdownMenu(
    selectedKind: TaskKind,
    onKindSelected: (TaskKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TaskKindChip(
            kind = selectedKind,
            modifier = Modifier.clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val categories = TaskKind.entries.groupBy { it.category }
            categories.forEach { (category, kinds) ->
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                kinds.forEach { kind ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(kind.colorValue))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = kind.displayName.split(" • ").last(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (kind.productivityValue != 0) {
                                    val color = if (kind.productivityValue > 0) Success else Color(0xFFFF4D4D)
                                    val sign = if (kind.productivityValue > 0) "+" else ""
                                    Text(
                                        text = "$sign${kind.productivityValue}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onKindSelected(kind)
                            expanded = false
                        }
                    )
                }
                if (category != categories.keys.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

/** Tier-colored chip for a Todo priority (A1 best .. C3 worst), or a neutral "No Priority" chip
 * when unset. Color is by LETTER tier only (A=red/urgent, B=orange, C=green), not per-exact-
 * level, since the 9-way distinction matters far less at a glance than which third it's in. */
@Composable
fun TodoPriorityChip(
    priority: TodoPriority?,
    modifier: Modifier = Modifier
) {
    val color = when (priority?.name?.firstOrNull()) {
        'A' -> Color(0xFFE53935)
        'B' -> Color(0xFFFB8C00)
        'C' -> Color(0xFF43A047)
        else -> Color.Gray
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = priority?.name ?: "No Priority",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Shared between TodoEditDialog (allowUnset = true, a Todo can genuinely have no priority) and
 * the Settings procrastination-cutoff picker (allowUnset = false, a cutoff tier is always set --
 * "unset" isn't a meaningful cutoff value). */
@Composable
fun TodoPriorityDropdownMenu(
    selectedPriority: TodoPriority?,
    onPrioritySelected: (TodoPriority?) -> Unit,
    modifier: Modifier = Modifier,
    allowUnset: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TodoPriorityChip(
            priority = selectedPriority,
            modifier = Modifier.clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowUnset) {
                DropdownMenuItem(
                    text = { Text("No Priority", style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onPrioritySelected(null); expanded = false }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            val tiers = TodoPriority.entries.groupBy { it.name.first() }
            tiers.forEach { (letter, levels) ->
                Text(
                    text = "Tier $letter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                levels.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.name, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onPrioritySelected(level); expanded = false }
                    )
                }
                if (letter != tiers.keys.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

