package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/**
 * Diagram building blocks for the Task Tracking guides.
 *
 * The Tasks top bar alone appears in a dozen articles; defining these once as parameterised
 * functions is what keeps each article file readable prose rather than a wall of nested data. It
 * is also the thing that makes the diagrams consistent -- change the bar here and every guide that
 * shows it follows.
 */

/** The Tasks screen top bar. [highlight] names which action to ring, by index into the four. */
internal fun tasksTopBar(highlight: Int? = null, callout: Int? = null) = DiagramElement.TopBar(
    title = "Tasks",
    actions = listOf(
        DiagramIcon(Icons.Default.Alarm, highlight = highlight == 0, callout = if (highlight == 0) callout else null),
        DiagramIcon(Icons.Default.Sync, highlight = highlight == 1, callout = if (highlight == 1) callout else null),
        DiagramIcon(Icons.Default.BarChart, highlight = highlight == 2, callout = if (highlight == 2) callout else null),
        DiagramIcon(Icons.Default.History, highlight = highlight == 3, callout = if (highlight == 3) callout else null)
    )
)

/** A running session card: name, elapsed time, Kind chip, and the pause/stop controls. */
internal fun runningCard(
    name: String = "Writing",
    elapsed: String = "00:18:42",
    kind: TaskKind = TaskKind.PEACOCK,
    type: String? = "Work",
    highlight: Boolean = false,
    callout: Int? = null,
    trailing: List<DiagramIcon> = listOf(
        DiagramIcon(Icons.Default.Pause),
        DiagramIcon(Icons.Default.Stop)
    ),
    indent: Int = 0
) = DiagramElement.Row(
    title = name,
    subtitle = type?.let { "◈ $it" },
    meta = elapsed,
    leadingBar = DiagramAccent.Kind(kind),
    chips = listOf(DiagramChip(kind.displayName.split(" • ").last(), DiagramAccent.Kind(kind), leadingDot = true)),
    trailing = trailing,
    indent = indent,
    highlight = highlight,
    callout = callout
)

/** A finished session card in the Recent Sessions or History list. */
internal fun completedCard(
    name: String = "Writing",
    meta: String = "1h 12m • 5.0% of Today",
    kind: TaskKind = TaskKind.PEACOCK,
    sittings: Int = 1,
    highlight: Boolean = false,
    callout: Int? = null
) = DiagramElement.Row(
    title = name,
    subtitle = if (sittings > 1) "$sittings sittings • 4 segments" else null,
    meta = meta,
    leadingBar = DiagramAccent.Kind(kind),
    chips = listOf(DiagramChip(kind.displayName.split(" • ").last(), DiagramAccent.Kind(kind), leadingDot = true)),
    trailing = listOf(DiagramIcon(Icons.Default.Delete)),
    highlight = highlight,
    callout = callout
)

internal fun addTaskFab(highlight: Boolean = true, callout: Int? = null) =
    DiagramElement.Fab(Icons.Default.Add, highlight = highlight, callout = callout)

internal fun playFab(highlight: Boolean = true, callout: Int? = null) =
    DiagramElement.Fab(Icons.Default.PlayArrow, highlight = highlight, callout = callout)

internal fun moreVert(highlight: Boolean = false, callout: Int? = null) =
    DiagramIcon(Icons.Default.MoreVert, highlight = highlight, callout = callout)

/** The three-way scope prompt, which several guides need to show. */
internal fun scopePrompt(
    title: String = "Change Kind to Peak Performance",
    body: String = "\"Writing\" covers 3 sittings. Apply this to all of them, or only to the one you opened?"
) = DiagramElement.Popup(
    title = title,
    body = body,
    confirmLabel = "Change all 3",
    dismissLabel = "Just this one"
)
