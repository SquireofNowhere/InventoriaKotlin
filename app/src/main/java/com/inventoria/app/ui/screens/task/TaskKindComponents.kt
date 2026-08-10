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
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskCategory
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

