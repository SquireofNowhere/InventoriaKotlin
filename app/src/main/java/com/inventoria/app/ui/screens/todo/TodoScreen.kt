package com.inventoria.app.ui.screens.todo

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.task.TaskKindDropdownMenu
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import java.util.Calendar

private fun showDatePicker(context: Context, initialTime: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val result = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(result.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onNavigateBack: () -> Unit,
    viewModel: TodoViewModel
) {
    val allTodos by viewModel.todos.collectAsState()
    val todoSections by viewModel.todoSections.collectAsState()
    val undatedTodoEntries by viewModel.undatedTodoEntries.collectAsState()
    val isAddingNew by viewModel.isAddingNew.collectAsState()
    val pendingEditTodo by viewModel.pendingEditTodo.collectAsState()
    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }

    // Drag-and-drop parenting: each row reports its own on-screen Y range here as it's laid out
    // (below), so hover target detection is just "which range contains the current drag Y" --
    // no separate bounds-tracking pass needed since the list is already being composed anyway.
    val itemBoundsY = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }
    var draggedTodoId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val invalidDropTargets = remember(draggedTodoId, allTodos) {
        draggedTodoId?.let { viewModel.invalidParentIds(it, allTodos) } ?: emptySet()
    }
    val hoverTodoId = if (draggedTodoId != null) {
        itemBoundsY.entries.firstOrNull { (id, range) -> id !in invalidDropTargets && dragY in range }?.key
    } else null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Todos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startAddingTodo() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo")
            }
        }
    ) { padding ->
        if (todoSections.isEmpty() && undatedTodoEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No todos yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                todoSections.forEach { section ->
                    item(key = "day_${section.dayStart}") {
                        TodoDayHeader(section.dayStart, section.totalDueCount, section.completedDueCount)
                    }
                    items(section.visibleTodos, key = { it.todo.id }) { entry ->
                        TodoRow(
                            entry = entry,
                            todayStart = todayStart,
                            isDragged = draggedTodoId == entry.todo.id,
                            isHoverTarget = hoverTodoId == entry.todo.id,
                            onToggleCompleted = { viewModel.toggleComplete(entry.todo) },
                            onClick = { viewModel.startEditingTodo(entry.todo) },
                            onDelete = { viewModel.deleteTodo(entry.todo) },
                            onStart = { viewModel.startTaskFromTodo(entry.todo) },
                            onBoundsChanged = { range -> itemBoundsY[entry.todo.id] = range },
                            onDragStart = {
                                draggedTodoId = entry.todo.id
                                dragY = itemBoundsY[entry.todo.id]?.let { (it.start + it.endInclusive) / 2f } ?: 0f
                            },
                            onDragDelta = { deltaY -> dragY += deltaY },
                            onDragEnd = {
                                hoverTodoId?.let { target -> viewModel.setParent(entry.todo, target) }
                                draggedTodoId = null
                            }
                        )
                    }
                }
                if (undatedTodoEntries.isNotEmpty()) {
                    item(key = "no_deadline_header") {
                        Text(
                            text = "No Deadline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(undatedTodoEntries, key = { it.todo.id }) { entry ->
                        TodoRow(
                            entry = entry,
                            todayStart = todayStart,
                            isDragged = draggedTodoId == entry.todo.id,
                            isHoverTarget = hoverTodoId == entry.todo.id,
                            onToggleCompleted = { viewModel.toggleComplete(entry.todo) },
                            onClick = { viewModel.startEditingTodo(entry.todo) },
                            onDelete = { viewModel.deleteTodo(entry.todo) },
                            onStart = { viewModel.startTaskFromTodo(entry.todo) },
                            onBoundsChanged = { range -> itemBoundsY[entry.todo.id] = range },
                            onDragStart = {
                                draggedTodoId = entry.todo.id
                                dragY = itemBoundsY[entry.todo.id]?.let { (it.start + it.endInclusive) / 2f } ?: 0f
                            },
                            onDragDelta = { deltaY -> dragY += deltaY },
                            onDragEnd = {
                                hoverTodoId?.let { target -> viewModel.setParent(entry.todo, target) }
                                draggedTodoId = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (isAddingNew) {
        TodoEditDialog(
            initialTitle = "",
            initialKind = TaskKind.GRAPHITE,
            initialDeadline = null,
            initialParentId = null,
            parentChoices = allTodos,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, deadline, parentId -> viewModel.addTodo(title, kind, deadline, parentId) }
        )
    }

    pendingEditTodo?.let { todo ->
        val invalidParentIds = remember(todo.id, allTodos) { viewModel.invalidParentIds(todo.id, allTodos) }
        TodoEditDialog(
            initialTitle = todo.title,
            initialKind = todo.kind,
            initialDeadline = todo.deadline,
            initialParentId = todo.parentTodoId,
            parentChoices = allTodos.filter { it.id !in invalidParentIds },
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, deadline, parentId -> viewModel.saveEditedTodo(todo, title, kind, deadline, parentId) }
        )
    }
}

/** Day section header: which day it was, its date, and (when this day actually had todos due
 * on it, as opposed to only hosting carried-over overdue rows) an "X% Done" progress bar --
 * same readiness-bar pattern used for Collections. */
@Composable
private fun TodoDayHeader(dayStart: Long, totalDue: Int, completedDue: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(getDayLabel(dayStart), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatSimpleDate(dayStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (totalDue > 0) {
                val pct = (completedDue * 100) / totalDue
                Text(
                    text = "$pct% Done",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (totalDue > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { completedDue.toFloat() / totalDue.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun TodoRow(
    entry: TodoTreeEntry,
    todayStart: Long,
    isDragged: Boolean,
    isHoverTarget: Boolean,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onBoundsChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val todo = entry.todo
    val isOverdue = todo.state != TodoState.COMPLETE && todo.deadline != null && todo.deadline!! < todayStart
    val daysOverdue = if (isOverdue) ((todayStart - todo.deadline!!) / 86_400_000L).toInt() else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 20).dp)
            .alpha(if (isDragged) 0.5f else 1f)
            .then(
                if (isHoverTarget) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                else Modifier
            )
            .onGloballyPositioned { coords ->
                val top = coords.positionInRoot().y
                onBoundsChanged(top..(top + coords.size.height))
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (entry.parentName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, start = 8.dp)) {
                    Icon(Icons.Default.SubdirectoryArrowRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Sub-todo of ${entry.parentName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Drag to make this a sub-todo of another",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .pointerInput(todo.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount -> change.consume(); onDragDelta(dragAmount.y) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                )
                TriStateCheckbox(
                    state = when (entry.effectiveState) {
                        TodoState.COMPLETE -> ToggleableState.On
                        TodoState.IN_PROGRESS -> ToggleableState.Indeterminate
                        TodoState.INCOMPLETE -> ToggleableState.Off
                    },
                    onClick = onToggleCompleted
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (entry.effectiveState == TodoState.COMPLETE) TextDecoration.LineThrough else null,
                        color = if (entry.effectiveState == TodoState.INCOMPLETE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOverdue) {
                        Text(
                            text = "Overdue by $daysOverdue day${if (daysOverdue == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    entry.childProgress?.let { (completed, total) ->
                        Text(
                            text = "$completed/$total sub-todos complete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (todo.state != TodoState.COMPLETE) {
                    if (todo.activeSessionGroupId == null) {
                        IconButton(onClick = onStart) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Tracking", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "In Progress",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TodoEditDialog(
    initialTitle: String,
    initialKind: TaskKind,
    initialDeadline: Long?,
    initialParentId: String?,
    parentChoices: List<Todo>,
    onDismiss: () -> Unit,
    onSave: (String, TaskKind, Long?, String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var kind by remember { mutableStateOf(initialKind) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var parentId by remember { mutableStateOf(initialParentId) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isEmpty()) "New Todo" else "Edit Todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                TaskKindDropdownMenu(selectedKind = kind, onKindSelected = { kind = it })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(context, deadline ?: System.currentTimeMillis()) { picked ->
                                deadline = picked
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(deadline?.let { formatSimpleDate(it) } ?: "No deadline")
                    }
                    if (deadline != null) {
                        IconButton(onClick = { deadline = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear deadline", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                ParentTodoPicker(
                    parentChoices = parentChoices,
                    selectedParentId = parentId,
                    onParentSelected = { parentId = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, kind, deadline, parentId) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ParentTodoPicker(
    parentChoices: List<Todo>,
    selectedParentId: String?,
    onParentSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = parentChoices.find { it.id == selectedParentId }?.title ?: "No parent"

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(selectedTitle)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No parent") }, onClick = { onParentSelected(null); expanded = false })
            parentChoices.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.title) },
                    onClick = { onParentSelected(candidate.id); expanded = false }
                )
            }
        }
    }
}
