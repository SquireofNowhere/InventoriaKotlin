package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.AutoStartPlan
import com.inventoria.app.data.model.ReminderSchedule
import com.inventoria.app.data.model.ScheduleKind
import com.inventoria.app.data.model.TaskTimePlan
import com.inventoria.app.ui.screens.task.MinutesAdder

/** Minutes before the due moment offered as one-tap chips; 0 is the due moment itself. */
private val BEFORE_PRESETS = listOf(0, 5, 10, 15, 30, 60)

/** How long the started session may run before stopping by itself. */
private val RUN_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)

/**
 * The todo dialog's one quiet line about starting itself: "Start automatically" until a plan
 * exists, then the plan in words. Tapping it opens [AutoStartDialog]. Always tappable, because a
 * schedule needs no deadline; the "before it's due" part switches itself off inside until there is one.
 */
@Composable
internal fun AutoStartRow(
    hasDeadline: Boolean,
    plan: AutoStartPlan,
    onPlanChange: (AutoStartPlan) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (plan.isEmpty) muted else LocalContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (plan.isEmpty) "Start automatically" else plan.describe(),
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (plan.needsDeadline && !hasDeadline) {
                Text(
                    "Starting before the due time won't happen until there is a deadline",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
            }
        }
    }
    if (open) {
        AutoStartDialog(
            initial = plan,
            hasDeadline = hasDeadline,
            onDismiss = { open = false },
            onConfirm = { onPlanChange(it); open = false }
        )
    }
}

/**
 * Builds an [AutoStartPlan]: when the todo starts tracking by itself -- some time before it is due,
 * and/or on a calendar schedule -- and how long the session may then run before stopping by itself.
 * Nothing is applied until Save, so backing out leaves the plan as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoStartDialog(
    initial: AutoStartPlan,
    hasDeadline: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AutoStartPlan) -> Unit
) {
    var before by remember { mutableStateOf(initial.beforeDueMinutes) }
    val schedules = remember { mutableStateListOf<ReminderSchedule>().apply { addAll(initial.schedules) } }
    var run by remember { mutableStateOf(initial.runMinutes) }
    // null = closed, -1 = adding a new schedule, otherwise the index being edited.
    var editing by remember { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val plan = AutoStartPlan.of(before, schedules, run)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start automatically") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Before it's due", style = MaterialTheme.typography.titleSmall)
                if (!hasDeadline) {
                    Text(
                        "Set a deadline to start before it. A schedule below needs none.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (BEFORE_PRESETS + listOfNotNull(before)).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            enabled = hasDeadline,
                            selected = before == minutes,
                            onClick = { before = if (before == minutes) null else minutes },
                            label = { Text(if (minutes == 0) "At due time" else "${TaskTimePlan.minutesLabel(minutes)} before") }
                        )
                    }
                }
                if (hasDeadline) {
                    MinutesAdder(label = "Or set your own", defaultHours = false) { before = it }
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("On a schedule", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Daily, weekly, monthly or on one date. No deadline needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                    TextButton(
                        enabled = schedules.size < AutoStartPlan.MAX_SCHEDULES,
                        onClick = { editing = -1 }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                schedules.forEachIndexed { index, schedule ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = index },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (schedule.kind == ScheduleKind.ONCE) Icons.Default.CalendarToday else Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(schedule.describe(), modifier = Modifier.weight(1f))
                        IconButton(onClick = { schedules.removeAt(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove schedule", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider()

                Text("Then stop after", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The session ends by itself once it has run this long. Leave it off to stop it yourself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (RUN_PRESETS + listOfNotNull(run)).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            selected = run == minutes,
                            onClick = { run = if (run == minutes) null else minutes },
                            label = { Text(TaskTimePlan.minutesLabel(minutes)) }
                        )
                    }
                }
                MinutesAdder(label = "Or set your own", defaultHours = false) { run = it }

                if (!plan.isEmpty || run != null) {
                    TextButton(
                        onClick = { before = null; schedules.clear(); run = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Clear all") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(plan) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    editing?.let { index ->
        ScheduleEditorDialog(
            initial = schedules.getOrNull(index),
            allowBurst = false,
            title = if (index in schedules.indices) "Edit start time" else "Add a start time",
            onDismiss = { editing = null },
            onConfirm = { schedule ->
                if (index in schedules.indices) schedules[index] = schedule else schedules.add(schedule)
                editing = null
            }
        )
    }
}
