package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.task.TaskKindChip
import com.inventoria.app.ui.screens.task.TaskTypeLabel
import com.inventoria.app.ui.screens.task.TodoPriorityChip
import com.inventoria.app.ui.screens.task.taskTypeColor
import com.inventoria.app.ui.screens.task.todoPriorityTierColor
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import com.inventoria.app.util.getDayLabel

/**
 * The todo list row and its day header, shared by the Plan screen (TodoScreen, the full editable
 * list) and the Today screen (a read-mostly view of what's due today).
 *
 * These live here rather than inside TodoScreen so the two screens can't drift on the display
 * rules that matter -- overdue days, the "late today" clock cue, tri-state completion from
 * effectiveState, and the play/arrow swap on an active session. Today opts out of the parts that
 * only make sense on a screen that owns the list, via showDragHandle/showDelete.
 */

/** Day section header: which day it was, its date, and (when this day actually had todos due
 * on it, as opposed to only hosting carried-over overdue rows) an "X% Done" progress bar --
 * same readiness-bar pattern used for Collections. */
@Composable
internal fun TodoDayHeader(dayStart: Long, totalDue: Int, completedDue: Int) {
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

/**
 * One todo row.
 *
 * [showDragHandle], [showDelete] and [showCollapseToggle] default to the full Plan-screen
 * behaviour. Today passes false for all three: it has no drop targets to drag onto and no delete
 * affordance, so it also skips reporting its bounds (the drag machinery is the only consumer of
 * those), and folding is Todos-screen view state that Today deliberately does not share.
 */
@Composable
internal fun TodoRow(
    entry: TodoTreeEntry,
    todayStart: Long,
    nowMinuteOfDay: Int,
    taskTypeNames: Map<String, String>,
    hasActiveSession: Boolean,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onViewTask: () -> Unit,
    showDragHandle: Boolean = true,
    showDelete: Boolean = true,
    showCollapseToggle: Boolean = true,
    isDragged: Boolean = false,
    isHoverTarget: Boolean = false,
    isSelected: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBoundsChanged: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onDragStart: (iconRootTopLeft: Offset, localOffset: Offset) -> Unit = { _, _ -> },
    onDragDelta: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
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
            .then(
                // Only the drag machinery consumes these, so a row without a handle has nothing
                // to report to.
                if (showDragHandle) Modifier.onGloballyPositioned { coords ->
                    val top = coords.positionInRoot().y
                    onBoundsChanged(top..(top + coords.size.height))
                } else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        // Prioritized rows carry a wash of their tier color (A=red, B=orange, C=green -- the
        // same mapping the chip uses) so the list reads by urgency without opening a single row.
        // Composited over surface rather than left translucent: the hover/selected overlays above
        // stack on whatever the container paints, and a flat pre-mixed color keeps those legible.
        // Unprioritized rows keep the stock Card container -- gray-washing them would just make
        // the whole list look disabled -- and completed rows drop back to it too, so the tint
        // tracks what still demands attention rather than what it once was.
        colors = if (todo.priority != null && entry.effectiveState != TodoState.COMPLETE) {
            CardDefaults.cardColors(
                containerColor = todoPriorityTierColor(todo.priority)
                    .copy(alpha = 0.10f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            )
        } else CardDefaults.cardColors()
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
                if (showDragHandle) {
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
                }
                // Sits immediately left of the checkbox, and only on rows that actually nest
                // something here -- reserving the space on every row would indent the whole list
                // for the sake of a control most rows have no use for.
                if (showCollapseToggle && entry.hasVisibleChildren) {
                    IconButton(onClick = onToggleCollapsed, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (entry.isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = if (entry.isCollapsed) "Show sub-todos" else "Hide sub-todos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    // Colored by id, not name: renames keep their hue, and the seeded ids make
                    // the same type the same color on every device. See taskTypeColor.
                    todo.taskTypeId?.let { typeId ->
                        taskTypeNames[typeId]?.let { typeName ->
                            TaskTypeLabel(typeName, color = taskTypeColor(typeId))
                        }
                    }
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (entry.effectiveState == TodoState.COMPLETE) TextDecoration.LineThrough else null,
                        color = if (entry.effectiveState == TodoState.INCOMPLETE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Due time and, when one is set, the alarm -- the icon is the only place the
                    // list says "this one will ring", so it sits right next to the time it rings
                    // for. An all-day todo with an alarm shows the icon alone.
                    val hasAlarm = todo.reminderOffsetMinutes != null && todo.state != TodoState.COMPLETE
                    if (todo.deadlineMinuteOfDay != null || hasAlarm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            todo.deadlineMinuteOfDay?.let { minuteOfDay ->
                                Text(
                                    text = "Due ${formatMinuteOfDay(minuteOfDay)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isLateToday) FontWeight.Bold else null,
                                    color = if (isLateToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasAlarm) Spacer(Modifier.width(4.dp))
                            }
                            if (hasAlarm) {
                                Icon(
                                    Icons.Default.Alarm,
                                    contentDescription = "Alarm set",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
