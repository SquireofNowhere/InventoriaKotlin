package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.ALL_DAY_REMINDER_MINUTE_OF_DAY
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.screens.task.TaskKindDropdownMenu
import com.inventoria.app.ui.screens.task.TaskTypeDropdownMenu
import com.inventoria.app.ui.screens.task.TodoPriorityDropdownMenu
import com.inventoria.app.util.currentMinuteOfDay
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The Todos segment of the Todos tab -- the full, editable list. Draws no app bar of its own: the
 * hub (TodoHubScreen) owns the one bar and hosts this screen's collapse-all / hide-completed
 * actions in it, the same arrangement the Inventory hub uses for its segments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onNavigateToTasks: () -> Unit,
    viewModel: TodoViewModel
) {
    val allTodos by viewModel.todos.collectAsState()
    // plannerSections, not todoSections: this screen's hide/collapse toggles apply here and
    // deliberately not on Today. See TodoViewModel.todoSections.
    val todoSections by viewModel.plannerSections.collectAsState()
    val undatedTodoEntries by viewModel.undatedTodoEntries.collectAsState()
    val isAddingNew by viewModel.isAddingNew.collectAsState()
    val pendingEditTodo by viewModel.pendingEditTodo.collectAsState()
    val selectedTodoId by viewModel.selectedTodoId.collectAsState()
    val taskTypes by viewModel.taskTypes.collectAsState()
    val taskTypeNames by viewModel.taskTypeNamesById.collectAsState()
    val todoIdsWithActiveSession by viewModel.todoIdsWithActiveSession.collectAsState()
    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }
    // Wall-clock minute, re-read once a minute, so a due time on a today todo flips to its
    // "past due" styling on its own instead of waiting for some unrelated recomposition.
    val nowMinuteOfDay by produceState(currentMinuteOfDay()) {
        while (true) {
            delay(60_000)
            value = currentMinuteOfDay()
        }
    }

    // Drag-and-drop parenting: each row reports its own on-screen Y range here as it's laid out
    // (below), all in ROOT coordinates. Hover target detection is just "which range contains the
    // current drag Y" -- and since only todo rows (never day headers/labels) report bounds here,
    // "no range contains it" already means "not over a todo" for free.
    val itemBoundsY = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }
    // Rows only ever *add* their bounds, so a row that stops being drawn -- folded under a
    // collapsed parent, or hidden with the rest of the completed work -- would otherwise leave its
    // last known Y range in here forever, and a drag over that empty space would silently resolve
    // to a todo that is not on screen.
    val renderedTodoIds = remember(todoSections, undatedTodoEntries) {
        (todoSections.flatMap { it.visibleTodos } + undatedTodoEntries).mapTo(mutableSetOf()) { it.todo.id }
    }
    LaunchedEffect(renderedTodoIds) { itemBoundsY.keys.retainAll(renderedTodoIds) }
    var contentBoxTopLeft by remember { mutableStateOf(Offset.Zero) }
    var draggedTodoId by remember { mutableStateOf<String?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) } // small, local-to-handle touch point
    var dragOffset by remember { mutableStateOf(Offset.Zero) } // current absolute root position of the finger
    val invalidDropTargets = remember(draggedTodoId, allTodos) {
        draggedTodoId?.let { viewModel.invalidParentIds(it, allTodos) } ?: emptySet()
    }
    val hoverTodoId = if (draggedTodoId != null) {
        itemBoundsY.entries.firstOrNull { (id, range) -> id !in invalidDropTargets && dragOffset.y in range }?.key
    } else null
    // True only when hovering somewhere that isn't ANY tracked todo (valid or invalid) -- a day
    // header, the "No Deadline" label, blank space, or off the list entirely. Hovering an invalid
    // target (self/descendant) is neither this nor a valid hover, so it correctly falls through
    // to a no-op on drop.
    val isRemoveZone = draggedTodoId != null && hoverTodoId == null &&
        itemBoundsY.values.none { dragOffset.y in it }

    fun endDrag() {
        val dragged = allTodos.find { it.id == draggedTodoId }
        if (dragged != null) {
            if (hoverTodoId != null) viewModel.setParent(dragged, hoverTodoId)
            else if (isRemoveZone) viewModel.clearParent(dragged)
        }
        draggedTodoId = null
    }

    // Every delete here is a tombstone kept for DELETED_ROW_RETENTION_MILLIS, so this offers back
    // the one mistake people actually notice -- the one they just made.
    val undoSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.undoPrompts.collect { label ->
            val result = undoSnackbarHostState.showSnackbar(
                message = "Deleted \"$label\"",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(undoSnackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startAddingTodo() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { contentBoxTopLeft = it.positionInRoot() }
                // Only fires for taps that land on background space -- any row's own clickable
                // (body, checkbox, Start, Delete) consumes the tap before it reaches here.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.clearSelection() })
                }
        ) {
            if (todoSections.isEmpty() && undatedTodoEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No todos yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                nowMinuteOfDay = nowMinuteOfDay,
                                taskTypeNames = taskTypeNames,
                                hasActiveSession = entry.todo.id in todoIdsWithActiveSession,
                                isDragged = draggedTodoId == entry.todo.id,
                                isHoverTarget = hoverTodoId == entry.todo.id,
                                isSelected = selectedTodoId == entry.todo.id,
                                onToggleCompleted = { viewModel.toggleComplete(entry.todo) },
                                onToggleCollapsed = { viewModel.toggleCollapsed(entry.todo.id) },
                                onClick = {
                                    if (selectedTodoId == entry.todo.id) viewModel.startEditingTodo(entry.todo)
                                    else viewModel.selectTodo(entry.todo.id)
                                },
                                onDelete = { viewModel.deleteTodo(entry.todo) },
                                onStart = { viewModel.startTaskFromTodo(entry.todo) },
                                onViewTask = onNavigateToTasks,
                                onBoundsChanged = { range -> itemBoundsY[entry.todo.id] = range },
                                onDragStart = { iconRootTopLeft, localOffset ->
                                    draggedTodoId = entry.todo.id
                                    grabOffset = localOffset
                                    dragOffset = iconRootTopLeft + localOffset
                                },
                                onDragDelta = { delta -> dragOffset += delta },
                                onDragEnd = { endDrag() }
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
                                nowMinuteOfDay = nowMinuteOfDay,
                                taskTypeNames = taskTypeNames,
                                hasActiveSession = entry.todo.id in todoIdsWithActiveSession,
                                isDragged = draggedTodoId == entry.todo.id,
                                isHoverTarget = hoverTodoId == entry.todo.id,
                                isSelected = selectedTodoId == entry.todo.id,
                                onToggleCompleted = { viewModel.toggleComplete(entry.todo) },
                                onToggleCollapsed = { viewModel.toggleCollapsed(entry.todo.id) },
                                onClick = {
                                    if (selectedTodoId == entry.todo.id) viewModel.startEditingTodo(entry.todo)
                                    else viewModel.selectTodo(entry.todo.id)
                                },
                                onDelete = { viewModel.deleteTodo(entry.todo) },
                                onStart = { viewModel.startTaskFromTodo(entry.todo) },
                                onViewTask = onNavigateToTasks,
                                onBoundsChanged = { range -> itemBoundsY[entry.todo.id] = range },
                                onDragStart = { iconRootTopLeft, localOffset ->
                                    draggedTodoId = entry.todo.id
                                    grabOffset = localOffset
                                    dragOffset = iconRootTopLeft + localOffset
                                },
                                onDragDelta = { delta -> dragOffset += delta },
                                onDragEnd = { endDrag() }
                            )
                        }
                    }
                }
            }

            if (draggedTodoId != null) {
                val draggedTitle = allTodos.find { it.id == draggedTodoId }?.title ?: ""
                val ghostTopLeft = dragOffset - grabOffset - contentBoxTopLeft
                Box(
                    modifier = Modifier
                        .offset { IntOffset(ghostTopLeft.x.roundToInt(), ghostTopLeft.y.roundToInt()) }
                        .fillMaxWidth(0.85f)
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isRemoveZone -> MaterialTheme.colorScheme.errorContainer
                                hoverTodoId != null -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .alpha(0.9f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isRemoveZone) Icons.Default.LinkOff else Icons.Default.DragIndicator,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isRemoveZone) "Remove Parent" else draggedTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            initialTaskTypeId = null,
            taskTypes = taskTypes,
            initialDeadline = null,
            initialDeadlineMinuteOfDay = null,
            // New todos ring at their due moment unless told otherwise -- the whole reason alarms
            // exist is the deadline nobody looked at. Only takes effect once a deadline is set.
            initialReminderOffsetMinutes = 0,
            initialParentId = selectedTodoId,
            initialPriority = null,
            parentChoices = allTodos,
            onCreateSubTodo = null,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, taskTypeId, deadline, deadlineMinuteOfDay, reminder, parentId, priority ->
                viewModel.addTodo(title, kind, taskTypeId, deadline, deadlineMinuteOfDay, reminder, parentId, priority)
            }
        )
    }

    pendingEditTodo?.let { todo ->
        val invalidParentIds = remember(todo.id, allTodos) { viewModel.invalidParentIds(todo.id, allTodos) }
        TodoEditDialog(
            initialTitle = todo.title,
            initialKind = todo.kind,
            initialTaskTypeId = todo.taskTypeId,
            taskTypes = taskTypes,
            initialDeadline = todo.deadline,
            initialDeadlineMinuteOfDay = todo.deadlineMinuteOfDay,
            initialReminderOffsetMinutes = todo.reminderOffsetMinutes,
            initialParentId = todo.parentTodoId,
            initialPriority = todo.priority,
            parentChoices = allTodos.filter { it.id !in invalidParentIds },
            onCreateSubTodo = { viewModel.startAddingSubTodoOf(todo) },
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, taskTypeId, deadline, deadlineMinuteOfDay, reminder, parentId, priority ->
                viewModel.saveEditedTodo(todo, title, kind, taskTypeId, deadline, deadlineMinuteOfDay, reminder, parentId, priority)
            }
        )
    }
}

/** The alarm lead times a todo can pick from. Values are minutes before the due moment; null is
 * "no alarm". Kept as a list of pairs rather than an enum so the stored Int stays the source of
 * truth and a value this list doesn't name (synced from a newer build, say) still displays. */
internal val REMINDER_CHOICES: List<Pair<Int?, String>> = listOf(
    null to "No alarm",
    0 to "At due time",
    10 to "10 minutes before",
    60 to "1 hour before",
    24 * 60 to "1 day before"
)

internal fun reminderLabel(offsetMinutes: Int?): String =
    REMINDER_CHOICES.firstOrNull { it.first == offsetMinutes }?.second
        ?: "$offsetMinutes minutes before"

@Composable
private fun TodoEditDialog(
    initialTitle: String,
    initialKind: TaskKind,
    initialTaskTypeId: String?,
    taskTypes: List<TaskType>,
    initialDeadline: Long?,
    initialDeadlineMinuteOfDay: Int?,
    initialReminderOffsetMinutes: Int?,
    initialParentId: String?,
    initialPriority: TodoPriority?,
    parentChoices: List<Todo>,
    onCreateSubTodo: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (String, TaskKind, String?, Long?, Int?, Int?, String?, TodoPriority?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var kind by remember { mutableStateOf(initialKind) }
    var taskTypeId by remember { mutableStateOf(initialTaskTypeId) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var deadlineMinuteOfDay by remember { mutableStateOf(initialDeadlineMinuteOfDay) }
    var reminderOffsetMinutes by remember { mutableStateOf(initialReminderOffsetMinutes) }
    var parentId by remember { mutableStateOf(initialParentId) }
    var priority by remember { mutableStateOf(initialPriority) }
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
                // Carried onto the task when this todo is started. Left unset, the task falls back
                // to whatever type this title has already settled on over in the tracker.
                TaskTypeDropdownMenu(
                    selectedTypeId = taskTypeId,
                    taskTypes = taskTypes,
                    onTypeSelected = { taskTypeId = it }
                )
                TodoPriorityDropdownMenu(selectedPriority = priority, onPrioritySelected = { priority = it })
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
                        IconButton(onClick = { deadline = null; deadlineMinuteOfDay = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear deadline", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                // Always on offer, date or no date: picking a time first is a perfectly natural
                // way to say "today at 17:00", so a time chosen with no date fills the date in as
                // today rather than making you go back for it. Clearing the date above still takes
                // the time with it -- a time with no day is not a deadline.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showTimePicker(context, deadlineMinuteOfDay) { picked ->
                                if (deadline == null) deadline = getStartOfDay(System.currentTimeMillis())
                                deadlineMinuteOfDay = picked
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            deadlineMinuteOfDay?.let { formatMinuteOfDay(it) }
                                ?: if (deadline != null) "No time (all day)" else "No time (picks today)"
                        )
                    }
                    if (deadlineMinuteOfDay != null) {
                        IconButton(onClick = { deadlineMinuteOfDay = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear time", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                // Alarm lead time. Only meaningful with a deadline, so it sits greyed out until
                // one exists -- but stays visible so it is obvious the option is there. All-day
                // deadlines ring at 09:00 (see ALL_DAY_REMINDER_MINUTE_OF_DAY).
                ReminderPicker(
                    enabled = deadline != null,
                    isAllDay = deadline != null && deadlineMinuteOfDay == null,
                    selected = reminderOffsetMinutes,
                    onSelected = { reminderOffsetMinutes = it }
                )
                ParentTodoPicker(
                    parentChoices = parentChoices,
                    selectedParentId = parentId,
                    onParentSelected = { parentId = it }
                )
                if (onCreateSubTodo != null) {
                    TextButton(onClick = onCreateSubTodo, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Sub-Todo")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, kind, taskTypeId, deadline, deadlineMinuteOfDay, reminderOffsetMinutes, parentId, priority) },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReminderPicker(
    enabled: Boolean,
    isAllDay: Boolean,
    selected: Int?,
    onSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable { expanded = true } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected != null) Icons.Default.Alarm else Icons.Default.AlarmOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tint
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = when {
                        !enabled -> "Alarm (set a deadline first)"
                        else -> reminderLabel(selected)
                    },
                    color = tint
                )
                if (enabled && selected != null && isAllDay) {
                    Text(
                        "All-day deadline: rings at ${formatMinuteOfDay(ALL_DAY_REMINDER_MINUTE_OF_DAY)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            REMINDER_CHOICES.forEach { (offset, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontWeight = if (offset == selected) FontWeight.Bold else null
                        )
                    },
                    onClick = { onSelected(offset); expanded = false }
                )
            }
        }
    }
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
