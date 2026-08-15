package com.inventoria.app.ui.screens.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.task.TaskKindChip
import com.inventoria.app.ui.screens.task.TaskKindDropdownMenu
import com.inventoria.app.ui.screens.task.TaskTypeDropdownMenu
import com.inventoria.app.ui.screens.task.TaskTypeLabel
import com.inventoria.app.ui.screens.task.TodoPriorityChip
import com.inventoria.app.ui.screens.task.TodoPriorityDropdownMenu
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

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

private fun currentMinuteOfDay(): Int = Calendar.getInstance().let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

/** Deadline time-of-day picker, handing back minutes since midnight rather than a timestamp --
 * the todo's date lives in its own field and must stay a start-of-day value. 24-hour, matching
 * showDateTimePicker on the Task tracker. */
private fun showTimePicker(context: Context, initialMinuteOfDay: Int?, onTimeSelected: (Int) -> Unit) {
    val now = Calendar.getInstance()
    val hour = initialMinuteOfDay?.let { it / 60 } ?: now.get(Calendar.HOUR_OF_DAY)
    val minute = initialMinuteOfDay?.let { it % 60 } ?: 0
    TimePickerDialog(
        context,
        { _, pickedHour, pickedMinute -> onTimeSelected(pickedHour * 60 + pickedMinute) },
        hour,
        minute,
        true
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTasks: () -> Unit,
    viewModel: TodoViewModel
) {
    val allTodos by viewModel.todos.collectAsState()
    val todoSections by viewModel.todoSections.collectAsState()
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Todos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection(); onNavigateBack() }) {
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
            initialParentId = selectedTodoId,
            initialPriority = null,
            parentChoices = allTodos,
            onCreateSubTodo = null,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, taskTypeId, deadline, deadlineMinuteOfDay, parentId, priority ->
                viewModel.addTodo(title, kind, taskTypeId, deadline, deadlineMinuteOfDay, parentId, priority)
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
            initialParentId = todo.parentTodoId,
            initialPriority = todo.priority,
            parentChoices = allTodos.filter { it.id !in invalidParentIds },
            onCreateSubTodo = { viewModel.startAddingSubTodoOf(todo) },
            onDismiss = { viewModel.dismissDialog() },
            onSave = { title, kind, taskTypeId, deadline, deadlineMinuteOfDay, parentId, priority ->
                viewModel.saveEditedTodo(todo, title, kind, taskTypeId, deadline, deadlineMinuteOfDay, parentId, priority)
            }
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
    nowMinuteOfDay: Int,
    taskTypeNames: Map<String, String>,
    hasActiveSession: Boolean,
    isDragged: Boolean,
    isHoverTarget: Boolean,
    isSelected: Boolean,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onViewTask: () -> Unit,
    onBoundsChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onDragStart: (iconRootTopLeft: Offset, localOffset: Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val todo = entry.todo
    val isOverdue = todo.state != TodoState.COMPLETE && todo.deadline != null && todo.deadline!! < todayStart
    val daysOverdue = if (isOverdue) ((todayStart - todo.deadline!!) / 86_400_000L).toInt() else 0
    // A time that's already passed only reads as late on the day it's actually due: earlier days
    // are covered by the whole-days "Overdue by N days" line, and on later days the clock says
    // nothing. Purely a display cue -- the procrastination penalty stays whole-days-only.
    val isLateToday = todo.state != TodoState.COMPLETE && todo.deadline == todayStart &&
        todo.deadlineMinuteOfDay != null && todo.deadlineMinuteOfDay!! < nowMinuteOfDay
    var iconRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    // pointerInput(todo.id) below only re-executes its block when todo.id itself changes -- since
    // it doesn't change mid-drag, the block (and whatever it captures) stays frozen at whichever
    // recomposition first set it up. onDragStart/onDragDelta happen to still work despite that
    // (their bodies only write to stable remember-backed state), but onDragEnd reads plain local
    // vals (hoverTodoId/isRemoveZone, recomputed fresh each recomposition, not remember-backed) in
    // the caller, and a frozen reference to that lambda saw them as they were before any drag
    // started -- silently resolving every drop as "nothing hovered." rememberUpdatedState keeps
    // the callback actually invoked always pointing at the latest lambda, matching the exact
    // pattern InventoryListScreen's own (working) drag-and-drop already relies on.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 20).dp)
            .alpha(if (isDragged) 0.3f else 1f)
            .then(
                if (isHoverTarget) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                else Modifier
            )
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
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
                // Kind color, at a glance, before anything else in the row -- same left-bar
                // convention SingleTaskItemCard already uses for tracked tasks.
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(todo.kind.colorValue))
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Drag to make this a sub-todo of another",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .onGloballyPositioned { iconRootTopLeft = it.positionInRoot() }
                        .pointerInput(todo.id) {
                            // Plain detectDragGestures (no long-press wait): this handle has no
                            // competing gesture to disambiguate against, so there's no reason to
                            // gate a drag behind a hold-still timer -- movement past touch slop
                            // starts the drag immediately.
                            detectDragGestures(
                                onDragStart = { localOffset -> currentOnDragStart(iconRootTopLeft, localOffset) },
                                onDrag = { change, dragAmount -> change.consume(); currentOnDragDelta(dragAmount) },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
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
                    val typeName = todo.taskTypeId?.let { taskTypeNames[it] }
                    if (typeName != null) TaskTypeLabel(typeName)
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (entry.effectiveState == TodoState.COMPLETE) TextDecoration.LineThrough else null,
                        color = if (entry.effectiveState == TodoState.INCOMPLETE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    todo.deadlineMinuteOfDay?.let { minuteOfDay ->
                        Text(
                            text = "Due ${formatMinuteOfDay(minuteOfDay)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isLateToday) FontWeight.Bold else null,
                            color = if (isLateToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                if (todo.priority != null) {
                    TodoPriorityChip(priority = todo.priority, modifier = Modifier.scale(0.85f))
                }
                TaskKindChip(kind = todo.kind, modifier = Modifier.scale(0.85f))
                if (todo.state != TodoState.COMPLETE) {
                    if (!hasActiveSession) {
                        IconButton(onClick = onStart) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Tracking", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = onViewTask) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "In Progress -- View on Tasks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
    initialTaskTypeId: String?,
    taskTypes: List<TaskType>,
    initialDeadline: Long?,
    initialDeadlineMinuteOfDay: Int?,
    initialParentId: String?,
    initialPriority: TodoPriority?,
    parentChoices: List<Todo>,
    onCreateSubTodo: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (String, TaskKind, String?, Long?, Int?, String?, TodoPriority?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var kind by remember { mutableStateOf(initialKind) }
    var taskTypeId by remember { mutableStateOf(initialTaskTypeId) }
    var deadline by remember { mutableStateOf(initialDeadline) }
    var deadlineMinuteOfDay by remember { mutableStateOf(initialDeadlineMinuteOfDay) }
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
                // A due time only means anything alongside a date, so this row only exists once
                // one is set -- and clearing the date above takes the time with it.
                if (deadline != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showTimePicker(context, deadlineMinuteOfDay) { picked ->
                                    deadlineMinuteOfDay = picked
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(deadlineMinuteOfDay?.let { formatMinuteOfDay(it) } ?: "No time (all day)")
                        }
                        if (deadlineMinuteOfDay != null) {
                            IconButton(onClick = { deadlineMinuteOfDay = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear time", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
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
            TextButton(onClick = { onSave(title, kind, taskTypeId, deadline, deadlineMinuteOfDay, parentId, priority) }, enabled = title.isNotBlank()) {
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
