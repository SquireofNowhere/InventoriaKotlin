package com.inventoria.app.ui.screens.task

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.TaskTimePlan
import com.inventoria.app.ui.screens.todo.showTimePicker

private val GOAL_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)
private val MARK_PRESETS = listOf(5, 10, 15, 25, 30, 45, 60, 90)

/**
 * The quiet line in Task Details that opens [TaskTimePlanDialog]: "Time and alarms" until there is
 * a plan, then the plan in words, with how far along the session is when it has a planned length.
 * [elapsedMs] is the whole session's running time so far; [running] says whether it is still ticking.
 */
@Composable
internal fun TimePlanRow(
    plan: TaskTimePlan,
    elapsedMs: Long,
    running: Boolean,
    onPlanChange: (TaskTimePlan) -> Unit
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
        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (plan.isEmpty) "Time and alarms" else plan.describe(),
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            plan.goalMinutes?.let { goal ->
                val done = TaskTimePlan.elapsedLabel(elapsedMs)
                val label = if (elapsedMs >= goal * TaskTimePlan.MILLIS_PER_MINUTE) "$done -- planned length reached"
                else "$done of ${TaskTimePlan.minutesLabel(goal)}"
                Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
    if (open) {
        TaskTimePlanDialog(
            initial = plan,
            running = running,
            onDismiss = { open = false },
            onConfirm = { onPlanChange(it); open = false }
        )
    }
}

/**
 * Builds a [TaskTimePlan]: how long the session is meant to go for, when to be told how long it has
 * gone, and when it should stop by itself. Nothing is applied until Save, so backing out leaves the
 * plan as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskTimePlanDialog(
    initial: TaskTimePlan,
    running: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TaskTimePlan) -> Unit
) {
    val context = LocalContext.current
    var goal by remember { mutableStateOf(initial.goalMinutes) }
    val marks = remember { mutableStateListOf<Int>().apply { addAll(initial.ringAtMinutes) } }
    var everyOn by remember { mutableStateOf(initial.ringEveryMinutes != null) }
    var every by remember { mutableStateOf(initial.ringEveryMinutes ?: 30) }
    var stopAtGoal by remember { mutableStateOf(initial.stopAtGoal) }
    var stopAt by remember { mutableStateOf(initial.stopAtMillis) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val plan = TaskTimePlan.of(
        goalMinutes = goal,
        ringAtMinutes = marks,
        ringEveryMinutes = every.takeIf { everyOn },
        stopAtGoal = stopAtGoal,
        stopAtMillis = stopAt
    )
    val everyLifted = everyOn && plan.ringEveryMinutes != every

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time and alarms") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!running) {
                    Text(
                        "Rings and automatic stops only happen while the task is running.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }

                Text("Planned length", style = MaterialTheme.typography.titleSmall)
                Text("How long this is supposed to go for. It rings when it is reached.", style = MaterialTheme.typography.labelSmall, color = muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (GOAL_PRESETS + listOfNotNull(goal)).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            selected = goal == minutes,
                            onClick = { goal = if (goal == minutes) null else minutes },
                            label = { Text(TaskTimePlan.minutesLabel(minutes)) }
                        )
                    }
                }
                MinutesAdder(label = "Or set your own", defaultHours = false) { goal = it }

                HorizontalDivider()

                Text("Ring when it has gone for", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (MARK_PRESETS + marks).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            selected = minutes in marks,
                            onClick = { if (minutes in marks) marks.remove(minutes) else marks.add(minutes) },
                            label = { Text(TaskTimePlan.minutesLabel(minutes)) }
                        )
                    }
                }
                MinutesAdder(label = "Or add your own", defaultHours = false) { if (it !in marks) marks.add(it) }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep ringing", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (everyOn) "Every ${TaskTimePlan.minutesLabel(plan.ringEveryMinutes ?: every)} it is running"
                            else "Every so often while it runs",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                    Switch(checked = everyOn, onCheckedChange = { everyOn = it })
                }
                if (everyOn) {
                    MinutesAdder(label = "Every", defaultHours = false, buttonLabel = "Set") { every = it }
                    if (everyLifted) {
                        Text(
                            "The shortest repeat is ${TaskTimePlan.MIN_EVERY_MINUTES} minutes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                }

                HorizontalDivider()

                Text("Stop by itself", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("When the planned length is up")
                        if (goal == null) {
                            Text("Set a planned length first", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                    Switch(enabled = goal != null, checked = stopAtGoal && goal != null, onCheckedChange = { stopAtGoal = it })
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showTimePicker(context, null) { minute ->
                                stopAt = TaskTimePlan.nextClockInstant(minute, System.currentTimeMillis())
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stopAt?.let { "At ${TaskTimePlan.clockLabel(it)}" } ?: "At a time of day",
                        modifier = Modifier.weight(1f),
                        color = if (stopAt == null) muted else LocalContentColor.current
                    )
                    if (stopAt != null) {
                        IconButton(onClick = { stopAt = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear stop time", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (stopAt != null) {
                    Text(
                        "The next time the clock reads ${TaskTimePlan.clockLabel(stopAt!!)}, from when you picked it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }

                if (!plan.isEmpty) {
                    TextButton(
                        onClick = { goal = null; marks.clear(); everyOn = false; stopAtGoal = false; stopAt = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Clear all") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(plan) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * An amount and a unit (minutes or hours) with a button that hands back the total in minutes: the
 * "or type your own" under a row of chips. Out-of-range amounts are refused by the button greying
 * out, not by a surprise at Save.
 */
@Composable
internal fun MinutesAdder(
    label: String,
    defaultHours: Boolean,
    buttonLabel: String = "Add",
    onAdd: (Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(defaultHours) }
    val minutes = amount.toLongOrNull()
        ?.takeIf { it >= 1 }
        ?.let { it * if (hours) 60 else 1 }
        ?.takeIf { it <= TaskTimePlan.MAX_MINUTES }
        ?.toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp)
            )
            FilterChip(selected = !hours, onClick = { hours = false }, label = { Text("min") })
            FilterChip(selected = hours, onClick = { hours = true }, label = { Text("hr") })
            TextButton(
                enabled = minutes != null,
                onClick = { minutes?.let(onAdd); amount = "" }
            ) { Text(buttonLabel) }
        }
    }
}
