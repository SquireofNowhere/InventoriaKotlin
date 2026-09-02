package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/**
 * Diagram building blocks for the Today guides. The Planned Now card reuses [scheduleBlockRow]
 * from TodosDiagrams.kt rather than redrawing an equivalent, since the real screen draws the same
 * block there too. [nowLiveRow] is its own shape rather than reusing TaskDiagrams.kt's
 * [runningCard]: unlike a tracker card, the Now card's live row has no pause/stop controls of its
 * own -- tapping the whole card is what opens the tracker for those.
 */

internal fun todayTopBar(highlight: Boolean = false, callout: Int? = null) = DiagramElement.TopBar(
    title = "Today",
    actions = listOf(DiagramIcon(Icons.Default.Refresh, highlight = highlight, callout = callout))
)

/** The Now card's Running/Paused row: a live session, tapping through to the tracker rather than
 * offering its own pause/stop controls. */
internal fun nowLiveRow(
    name: String = "Writing",
    elapsed: String = "00:18:42",
    kind: TaskKind = TaskKind.PEACOCK,
    highlight: Boolean = false
) = DiagramElement.Row(
    title = name,
    meta = elapsed,
    leadingBar = DiagramAccent.Kind(kind),
    chips = listOf(DiagramChip(kind.displayName.split(" • ").last(), DiagramAccent.Kind(kind), leadingDot = true)),
    highlight = highlight
)

/** The two-button row under the Now card, however it's labelled for the current state. */
internal fun nowCardActions(primary: String, secondary: String) = DiagramElement.ChipRow(
    chips = listOf(
        DiagramChip(primary, DiagramAccent.Primary, highlight = true),
        DiagramChip(secondary, DiagramAccent.Neutral)
    )
)

/** One row of the Up Next card: a clock time plus countdown as the meta text, an accent bar, and
 * an optional alarm icon. */
internal fun upNextRow(
    time: String = "14:00",
    countdown: String = "in 45 min",
    title: String = "Call the vet",
    caption: String = "Todo due",
    accent: DiagramAccent = DiagramAccent.Warning,
    hasAlarm: Boolean = false,
    highlight: Boolean = false
) = DiagramElement.Row(
    title = title,
    subtitle = caption,
    meta = "$time · $countdown",
    leadingBar = accent,
    trailing = if (hasAlarm) listOf(DiagramIcon(Icons.Default.Alarm)) else emptyList(),
    highlight = highlight
)

/** The red nudge banner across the top of Today. */
internal fun nudgeBanner(counts: String = "1 overdue · 2 due within the hour", detail: String? = "Call the vet · 14:00, in 45 min") =
    DiagramElement.Row(
        title = counts,
        subtitle = detail,
        leadingIcon = Icons.Default.NotificationImportant,
        trailing = listOf(DiagramIcon(Icons.AutoMirrored.Filled.ArrowForward)),
        highlight = true
    )

/** The Quick Capture field: one row standing in for the text field, its leading Add icon and its
 * two trailing actions -- Add as a todo, or start tracking now. */
internal fun quickCaptureRow(highlightTrailing: Int? = null) = DiagramElement.Row(
    title = "Add a todo for today, or start a task…",
    leadingIcon = Icons.Default.Add,
    trailing = listOf(
        DiagramIcon(Icons.Default.Checklist, highlight = highlightTrailing == 0),
        DiagramIcon(Icons.Default.PlayArrow, highlight = highlightTrailing == 1)
    )
)

internal fun openTrackerChip() = DiagramChip("Open tracker", DiagramAccent.Primary, highlight = true)
internal fun planDayChip() = DiagramChip("Plan the day", DiagramAccent.Neutral)
internal fun eventNoteIcon(highlight: Boolean = false) = DiagramIcon(Icons.Default.EventNote, highlight = highlight)
