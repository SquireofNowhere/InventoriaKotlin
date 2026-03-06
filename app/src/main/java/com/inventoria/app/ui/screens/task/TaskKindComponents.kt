package com.inventoria.app.ui.screens.task

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.model.TaskCategory
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.theme.Success

@Composable
fun TaskKindChip(
    kind: TaskKind,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(kind.colorValue).copy(alpha = 0.15f)
    val contentColor = Color(kind.colorValue)

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
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
                    text = if (kind.productivityValue > 0) "+${kind.productivityValue}" else "${kind.productivityValue}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (kind.productivityValue > 0) Success else Color(0xFFF44336)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskKindDropdownMenu(
    selectedKind: TaskKind,
    onKindSelected: (TaskKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        InputChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                val label = if (selectedKind.displayName.contains(" • ")) {
                    selectedKind.displayName.split(" • ").last()
                } else {
                    selectedKind.displayName.substringAfter(" ").trim()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label)
                    if (selectedKind.productivityValue != 0) {
                        Text(
                            text = if (selectedKind.productivityValue > 0) "+${selectedKind.productivityValue}" else "${selectedKind.productivityValue}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selectedKind.productivityValue > 0) Success else Color(0xFFF44336)
                        )
                    }
                }
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(selectedKind.colorValue))
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = InputChipDefaults.inputChipColors(
                selectedContainerColor = Color(selectedKind.colorValue).copy(alpha = 0.1f),
                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = InputChipDefaults.inputChipBorder(
                borderColor = Color(selectedKind.colorValue).copy(alpha = 0.3f),
                selectedBorderColor = Color(selectedKind.colorValue).copy(alpha = 0.5f)
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            val allKinds = TaskKind.values()
            allKinds.forEachIndexed { index, kind ->
                // Add category header
                if (index == 0 || kind.category != allKinds[index - 1].category) {
                    val categoryName = when(kind.category) {
                        TaskCategory.NEUTRAL -> "Default Tasks"
                        TaskCategory.PERSONAL -> "Productivity Tasks"
                        TaskCategory.SOCIAL -> "Social Tasks"
                    }
                    Text(
                        text = categoryName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(kind.colorValue))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(kind.displayName, style = MaterialTheme.typography.bodyMedium)
                                    if (kind.productivityValue != 0) {
                                        Text(
                                            text = if (kind.productivityValue > 0) "+${kind.productivityValue}" else "${kind.productivityValue}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (kind.productivityValue > 0) Success else Color(0xFFF44336)
                                        )
                                    }
                                }
                                Text(
                                    kind.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onKindSelected(kind)
                        expanded = false
                    }
                )

                // Divider only between groups (not between items in the same group)
                if (index < allKinds.size - 1 && kind.category != allKinds[index + 1].category) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProductivityScoreCard(
    personalScore: Int,
    socialScore: Int,
    scoreBreakdown: List<Pair<TaskKind, Int>> = emptyList(),
    onViewStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val totalScore = personalScore + socialScore
    val totalColor = when {
        totalScore > 0 -> Success
        totalScore < 0 -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (scoreBreakdown.isNotEmpty()) expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Daily Productivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (scoreBreakdown.isNotEmpty()) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Points Breakdown",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        TextButton(
                            onClick = onViewStats,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View All Stats", style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    scoreBreakdown.forEach { (kind, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(kind.colorValue))
                                )
                                Spacer(Modifier.width(8.dp))
                                val label = if (kind.displayName.contains(" • ")) {
                                    kind.displayName.split(" • ").last()
                                } else {
                                    kind.displayName.substringAfter(" ").trim()
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = if (score >= 0) "+$score" else "$score",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (score > 0) Success else if (score < 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
