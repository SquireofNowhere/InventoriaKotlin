package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.Visibility
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.screens.help.model.*

/**
 * Diagram building blocks for the Todos guides -- the todo list and the Schedule segment share one
 * tab, so they share one file of parameterised pieces the same way TaskDiagrams.kt does for Task
 * Tracking. [priorityAccent] is here rather than duplicated per caller since three different rows
 * (a todo row, a priority picker field, a scope-of-penalty callout) all need to turn a
 * [TodoPriority] into the same red/orange/green the app itself uses (todoPriorityTierColor).
 */

internal fun priorityAccent(priority: TodoPriority?): DiagramAccent = when (priority?.name?.firstOrNull()) {
    'A' -> DiagramAccent.Danger
    'B' -> DiagramAccent.Warning
    'C' -> DiagramAccent.Success
    else -> DiagramAccent.Neutral
}

/** The Todos tab's segmented control -- Todos | Schedule -- shown once at the top of any diagram
 * that needs to establish which half of the tab a step is happening on. */
internal fun todosSegmentedControl(selected: String = "Todos") = DiagramElement.ChipRow(
    chips = listOf(
        DiagramChip("Todos", if (selected == "Todos") DiagramAccent.Primary else DiagramAccent.Neutral, highlight = selected == "Todos"),
        DiagramChip("Schedule", if (selected == "Schedule") DiagramAccent.Primary else DiagramAccent.Neutral, highlight = selected == "Schedule")
    )
)

/** The Todos tab bar: collapse/expand-all and hide-completed, the two actions that only appear
 * while the Todos segment is showing. */
internal fun todosTopBar(highlight: Int? = null, callout: Int? = null) = DiagramElement.TopBar(
    title = "Todos",
    actions = listOf(
        DiagramIcon(Icons.Default.UnfoldLess, highlight = highlight == 0, callout = if (highlight == 0) callout else null),
        DiagramIcon(Icons.Default.Visibility, highlight = highlight == 1, callout = if (highlight == 1) callout else null)
    )
)

/** One todo list row: kind bar, title, optional due time, optional priority chip, and whichever
 * trailing icon matches its tracking state. Mirrors TodoRow.kt's own layout closely enough to
 * teach the real shape without trying to be a screenshot of it. */
internal fun todoRow(
    title: String = "Renew passport",
    due: String? = "Due 17:00",
    priority: TodoPriority? = TodoPriority.A1,
    kind: TaskKind = TaskKind.GRAPHITE,
    hasAlarm: Boolean = false,
    indent: Int = 0,
    trailing: List<DiagramIcon> = listOf(DiagramIcon(Icons.Default.PlayArrow)),
    highlight: Boolean = false,
    callout: Int? = null,
    gesture: DiagramGesture? = null
) = DiagramElement.Row(
    title = title,
    meta = due,
    leadingBar = DiagramAccent.Kind(kind),
    chips = listOfNotNull(
        priority?.let { DiagramChip(it.name, priorityAccent(it)) },
        if (hasAlarm) DiagramChip("Alarm", DiagramAccent.Neutral) else null
    ),
    trailing = trailing,
    indent = indent,
    highlight = highlight,
    callout = callout,
    gesture = gesture
)

internal fun addTodoFab(highlight: Boolean = true, callout: Int? = null) =
    DiagramElement.Fab(Icons.Default.Add, highlight = highlight, callout = callout)

internal fun startTrailing(highlight: Boolean = true) = listOf(DiagramIcon(Icons.Default.PlayArrow, highlight = highlight))
internal fun viewOnTasksTrailing(highlight: Boolean = true) =
    listOf(DiagramIcon(Icons.AutoMirrored.Filled.ArrowForward, highlight = highlight))
internal fun deleteTrailing(highlight: Boolean = false) = listOf(DiagramIcon(Icons.Default.Delete, highlight = highlight))
internal fun dragHandleTrailing() = listOf(DiagramIcon(Icons.Default.DragIndicator))

/** A day section header, as it appears above a run of todos due that day. */
internal fun todoDayHeader(label: String = "Today") = DiagramElement.SectionHeader(label)

/** The "started tracking" confirmation after tapping a todo's play button. */
internal fun trackingStartedPopup(title: String = "Renew passport") = DiagramElement.Popup(
    title = "Tracking started",
    body = "\"$title\" is now running on the Task Tracker. Stop it there when you're done and " +
        "you'll be asked whether the todo is complete.",
    confirmLabel = "Open Tracker",
    dismissLabel = "Stay here"
)

/** The completion check-in offered when a task started from a todo is stopped. */
internal fun completionCheckInPopup(title: String = "Renew passport") = DiagramElement.Popup(
    title = "Finished?",
    body = "Is \"$title\" complete, or still ongoing?",
    confirmLabel = "Complete",
    dismissLabel = "Still Ongoing"
)

// ---- Schedule ----------------------------------------------------------------------------------

/** The week strip: seven day cells with a marker dot per kind of thing that day has. */
internal fun weekStripNote() = DiagramElement.Note("Mon  Tue  Wed  Thu  Fri  Sat  Sun -- tap a day to select it")

/** A flat schedule block on the day timeline: a wash of its Kind colour, a title, and its span. */
internal fun scheduleBlockRow(
    title: String = "Deep Work",
    span: String = "09:00 – 11:00",
    kind: TaskKind = TaskKind.PEACOCK,
    type: String? = "Coding",
    repeats: Boolean = false,
    highlight: Boolean = false,
    callout: Int? = null
) = DiagramElement.Row(
    title = title,
    subtitle = type?.let { "◈ $it" },
    meta = span,
    leadingBar = DiagramAccent.Kind(kind),
    chips = if (repeats) listOf(DiagramChip("Repeats", DiagramAccent.Neutral)) else emptyList(),
    trailing = if (repeats) listOf(DiagramIcon(Icons.Default.Repeat)) else emptyList(),
    highlight = highlight,
    callout = callout
)

/** A tracked task segment as it sits on the schedule timeline, to the right of the divider. */
internal fun scheduleTaskRow(
    name: String = "Client email",
    span: String = "09:15 – 09:40",
    kind: TaskKind = TaskKind.PEACOCK,
    highlight: Boolean = false,
    callout: Int? = null,
    gesture: DiagramGesture? = DiagramGesture.Tap
) = DiagramElement.Row(
    title = name,
    meta = span,
    leadingBar = DiagramAccent.Kind(kind),
    indent = 2,
    highlight = highlight,
    callout = callout,
    gesture = gesture
)

/** The block dialog, shown by tapping an empty hour, the FAB, or an existing block. */
internal fun scheduleBlockPopup(
    isNew: Boolean = true,
    title: String = "Deep Work",
    highlightField: Int? = null
) = DiagramElement.Popup(
    title = if (isNew) "New Block" else "Edit Block",
    fields = listOf(
        DiagramField("What is this time for?", title, FieldKind.Text, highlight = highlightField == 0, callout = if (highlightField == 0) 1 else null),
        DiagramField("Kind", "Peacock", FieldKind.Dropdown, highlight = highlightField == 1, callout = if (highlightField == 1) 1 else null),
        DiagramField("Type", "Coding", FieldKind.Dropdown, highlight = highlightField == 2, callout = if (highlightField == 2) 1 else null),
        DiagramField("From / To", "09:00 – 11:00", FieldKind.Text, highlight = highlightField == 3, callout = if (highlightField == 3) 1 else null),
        DiagramField("Repeat weekly", "", FieldKind.Toggle, highlight = highlightField == 4, callout = if (highlightField == 4) 1 else null)
    ),
    confirmLabel = "Save",
    dismissLabel = if (isNew) "Cancel" else "Delete"
)

internal fun addBlockFab(highlight: Boolean = true, callout: Int? = null) =
    DiagramElement.Fab(Icons.Default.Add, highlight = highlight, callout = callout)

/** A todo due at a time of day, as the hairline it draws across the schedule timeline. */
internal fun todoDueMarker(title: String = "Call the vet", time: String = "14:00", done: Boolean = false, highlight: Boolean = false) =
    DiagramElement.Row(
        title = "$time  $title",
        leadingIcon = if (done) Icons.Default.Checklist else Icons.Default.CalendarToday,
        highlight = highlight,
        gesture = DiagramGesture.Tap
    )

internal fun todayIcon(highlight: Boolean = false) = DiagramIcon(Icons.Default.Today, highlight = highlight)
internal fun alarmIcon(highlight: Boolean = false) = DiagramIcon(Icons.Default.Alarm, highlight = highlight)
