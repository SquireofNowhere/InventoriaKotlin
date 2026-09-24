package com.inventoria.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.shared.model.Task
import com.inventoria.shared.model.startOfDay
import com.inventoria.shared.remote.InventoriaSnapshot

/** How much history the list renders; older sessions stay on the phone's History screen for now. */
private const val RECENT_TASK_LIMIT = 300

@Composable
fun TasksScreen(snapshot: InventoriaSnapshot) {
    val now = rememberNow()
    val typeNames = remember(snapshot.taskTypes) { snapshot.taskTypes.associate { it.id to it.name } }
    val days = remember(snapshot.tasks) {
        snapshot.tasks
            .sortedByDescending { it.startTime }
            .take(RECENT_TASK_LIMIT)
            .groupBy { startOfDay(it.startTime) }
            .entries.toList()
    }

    if (days.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Tasks")
            EmptyState("No tracked sessions yet. Start one from the Android app.")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Tasks", "Most recent ${minOf(snapshot.tasks.size, RECENT_TASK_LIMIT)} sessions") }
        days.forEach { (day, tasks) ->
            item(key = "day-$day") {
                val score = tasks.sumOf { it.score }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDay(day).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(
                        "${formatDuration(tasks.sumOf { it.elapsedMillis(now) })} · score ${if (score > 0) "+$score" else "$score"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(tasks, key = { it.id }) { task -> TaskRow(task, typeNames[task.taskTypeId], now) }
        }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}

@Composable
private fun TaskRow(task: Task, typeName: String?, now: Long) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KindDot(task.kind.color)
            Column(Modifier.weight(1f)) {
                Text(task.name.ifBlank { "Untitled task" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val end = task.endTime ?: if (task.isRunning) null else task.startTime + task.duration
                val range = "${formatTime(task.startTime)}–${end?.let(::formatTime) ?: "now"}"
                Text(
                    listOfNotNull(range, typeName, task.kind.label, "interruption".takeIf { task.interruptedGroupId != null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(task.elapsedMillis(now)), style = MaterialTheme.typography.bodyLarge, fontWeight = if (task.isRunning) FontWeight.Bold else FontWeight.Normal)
                if (task.score != 0) {
                    Text(if (task.score > 0) "+${task.score}" else "${task.score}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
