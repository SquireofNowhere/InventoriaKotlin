package com.inventoria.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.model.startOfDay
import com.inventoria.shared.remote.InventoriaSnapshot
import kotlinx.coroutines.delay

/** A clock that ticks every second, for running timers. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = nowMillis()
        }
    }
    return now
}

@Composable
fun TodayScreen(snapshot: InventoriaSnapshot) {
    val now = rememberNow()
    val todayStart = startOfDay(now)
    val todaysTasks = snapshot.tasks.filter { it.startTime >= todayStart }
    val running = snapshot.tasks.filter { it.isRunning }
    val openTodos = snapshot.todos.filter { it.state != TodoState.COMPLETE }
    val dueToday = openTodos.filter { it.deadline == todayStart }
    val overdue = openTodos.filter { (it.deadline ?: Long.MAX_VALUE) < todayStart }
    val blocks = snapshot.scheduleBlocks.filter { it.occursOn(todayStart) }.sortedBy { it.startMinuteOfDay }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Today", formatDay(now))

        if (running.isNotEmpty()) {
            SectionHeader("Now")
            running.forEach { task ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KindDot(task.kind.color, size = 14)
                        Column(Modifier.weight(1f)) {
                            Text(task.name.ifBlank { "Untitled task" }, style = MaterialTheme.typography.titleMedium)
                            Text("${task.kind.label} · since ${formatTime(task.startTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatDuration(task.elapsedMillis(now)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        SectionHeader("At a glance")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Score today", todaysTasks.sumOf { it.score }.let { if (it > 0) "+$it" else "$it" }, Modifier.weight(1f))
            StatCard("Tracked", formatDuration(todaysTasks.sumOf { it.elapsedMillis(now) }), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Due today", dueToday.size.toString(), Modifier.weight(1f), detail = if (overdue.isNotEmpty()) "${overdue.size} overdue" else null)
            StatCard("Items", snapshot.items.size.toString(), Modifier.weight(1f), detail = snapshot.collections.size.let { if (it == 1) "1 collection" else "$it collections" })
        }

        if (blocks.isNotEmpty()) {
            SectionHeader("Schedule")
            blocks.forEach { block ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KindDot(block.kind.color)
                    Text("${formatMinuteOfDay(block.startMinuteOfDay)}–${formatMinuteOfDay(block.endMinuteOfDay)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(block.title.ifBlank { "Untitled block" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (dueToday.isNotEmpty() || overdue.isNotEmpty()) {
            SectionHeader("Due")
            (overdue + dueToday).sortedBy { it.priority?.ordinal ?: Int.MAX_VALUE }.forEach { todo ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KindDot(todo.kind.color)
                    Text(todo.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    todo.priority?.let { Pill(it.name) }
                    if ((todo.deadline ?: 0) < todayStart) Pill("Overdue", MaterialTheme.colorScheme.errorContainer)
                }
            }
        }
        Row(Modifier.padding(24.dp)) {}
    }
}
