package com.inventoria.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import com.inventoria.shared.model.startOfDay
import com.inventoria.shared.remote.InventoriaSnapshot

/** A todo with its depth in the sub-todo tree, flattened for a lazy list. */
private data class TodoLine(val todo: Todo, val depth: Int)

@Composable
fun TodosScreen(snapshot: InventoriaSnapshot, onToggle: (Todo, Boolean) -> Unit) {
    val now = rememberNow()
    val todayStart = startOfDay(now)
    var showCompleted by remember { mutableStateOf(false) }

    val byParent = remember(snapshot.todos) { snapshot.todos.groupBy { it.parentTodoId } }
    val ids = remember(snapshot.todos) { snapshot.todos.map { it.id }.toSet() }
    // A todo whose parent is gone (deleted, or not synced yet) is shown as a root, not lost.
    val roots = snapshot.todos.filter { it.parentTodoId == null || it.parentTodoId !in ids }

    fun flatten(todo: Todo, depth: Int, out: MutableList<TodoLine>) {
        out += TodoLine(todo, depth)
        byParent[todo.id].orEmpty().sortedWith(todoOrder).forEach { flatten(it, depth + 1, out) }
    }
    fun section(filter: (Todo) -> Boolean): List<TodoLine> =
        buildList { roots.filter(filter).sortedWith(todoOrder).forEach { flatten(it, 0, this) } }

    val open = { t: Todo -> t.state != TodoState.COMPLETE }
    val sections = listOf(
        "Overdue" to section { open(it) && (it.deadline ?: Long.MAX_VALUE) < todayStart },
        "Today" to section { open(it) && it.deadline == todayStart },
        "Upcoming" to section { open(it) && (it.deadline ?: Long.MIN_VALUE) > todayStart },
        "No date" to section { open(it) && it.deadline == null }
    ).filter { it.second.isNotEmpty() }
    val completed = section { !open(it) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Todos", "${roots.count(open)} open · ${completed.count { it.depth == 0 }} done") }
        if (sections.isEmpty() && completed.isEmpty()) {
            item { EmptyState("No todos yet.") }
        }
        sections.forEach { (title, lines) ->
            item(key = "header-$title") { SectionHeader(title) }
            items(lines, key = { "$title-${it.todo.id}" }) { line -> TodoRow(line, todayStart, onToggle) }
        }
        if (completed.isNotEmpty()) {
            item(key = "completed-toggle") {
                TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(if (showCompleted) "Hide completed" else "Show completed (${completed.count { it.depth == 0 }})")
                }
            }
            if (showCompleted) {
                items(completed, key = { "done-${it.todo.id}" }) { line -> TodoRow(line, todayStart, onToggle) }
            }
        }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}

/** Priority first (A1 best, unprioritized last), then earliest deadline, then oldest. */
private val todoOrder = compareBy<Todo>(
    { it.priority?.ordinal ?: Int.MAX_VALUE },
    { it.deadline ?: Long.MAX_VALUE },
    { it.deadlineMinuteOfDay ?: Int.MAX_VALUE },
    { it.createdAt }
)

@Composable
private fun TodoRow(line: TodoLine, todayStart: Long, onToggle: (Todo, Boolean) -> Unit) {
    val todo = line.todo
    val done = todo.state == TodoState.COMPLETE
    Row(
        Modifier.fillMaxWidth().padding(start = (8 + line.depth * 28).dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggle(todo, it) })
        KindDot(todo.kind.color, size = 10)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                todo.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            val due = todo.deadline?.let { day ->
                val time = todo.deadlineMinuteOfDay?.let { " ${formatMinuteOfDay(it)}" } ?: ""
                if (day == todayStart) "Today$time" else formatDay(day) + time
            }
            val detail = listOfNotNull(
                due,
                todo.state.takeIf { it == TodoState.IN_PROGRESS }?.let { "In progress" },
                todo.description.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        todo.priority?.let { Pill(it.name) }
    }
}
