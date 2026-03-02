package com.inventoria.app.ui.screens.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.Success

@Composable
fun TaskKindChip(kind: TaskKind, modifier: Modifier = Modifier) {
    val color = Color(kind.colorValue)
    val displayName = kind.displayName
    
    // Extracts the descriptive part (e.g., "Waiting", "Growth")
    val taskTypeLabel = if (displayName.contains(" • ")) {
        displayName.split(" • ").last()
    } else {
        displayName.substringAfter(" ").trim()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = taskTypeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TaskKindDropdownMenu(
    selectedKind: TaskKind,
    onKindSelected: (TaskKind) -> Unit = { _: TaskKind -> },
    onKindSelectedAction: (TaskKind) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Helper to extract the descriptive name
    fun getTaskTypeLabel(kind: TaskKind): String {
        return if (kind.displayName.contains(" • ")) {
            kind.displayName.split(" • ").last()
        } else {
            kind.displayName.substringAfter(" ").trim()
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(selectedKind.colorValue))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = getTaskTypeLabel(selectedKind),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
        ) {
            TaskCategory.values().forEach { category ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    },
                    onClick = {},
                    enabled = false
                )

                TaskKind.values().filter { it.category == category }.forEach { kind ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(kind.colorValue))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = getTaskTypeLabel(kind),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = kind.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (kind.productivityValue != 0) {
                                    val scoreText = if (kind.productivityValue > 0) "+${kind.productivityValue}" else "${kind.productivityValue}"
                                    val scoreColor = if (kind.productivityValue > 0) Success else Color(0xFFF44336)
                                    Text(
                                        text = scoreText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            // Call both if set, though typically only one is used
                            onKindSelected(kind)
                            onKindSelectedAction(kind)
                            expanded = false
                        }
                    )
                }
                if (category != TaskCategory.values().last()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// Overload for backward compatibility with existing code
@Composable
fun TaskKindDropdownMenu(
    selectedKind: TaskKind,
    onKindSelected: (TaskKind) -> Unit,
    modifier: Modifier = Modifier
) {
    TaskKindDropdownMenu(
        selectedKind = selectedKind,
        onKindSelectedAction = onKindSelected,
        modifier = modifier
    )
}

@Composable
fun ProductivityScoreCard(
    personalScore: Int,
    socialScore: Int,
    modifier: Modifier = Modifier
) {
    val totalScore = personalScore + socialScore
    val totalColor = when {
        totalScore > 0 -> Success
        totalScore < 0 -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daily Productivity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreIndicator(label = "Personal", score = personalScore)
                    ScoreIndicator(label = "Social", score = socialScore)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (totalScore >= 0) "+$totalScore" else "$totalScore",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = totalColor
                )
                Text(
                    text = "Total Points",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScoreIndicator(label: String, score: Int) {
    val color = when {
        score > 0 -> Success
        score < 0 -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (score >= 0) "+$score" else "$score",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun isColorDark(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance < 0.5
}
