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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.ALL_DAY_REMINDER_MINUTE_OF_DAY
import com.inventoria.app.data.model.ReminderPlan
import com.inventoria.app.data.model.ReminderSpan
import com.inventoria.app.data.model.ReminderUnit
import com.inventoria.app.util.formatMinuteOfDay

/** The lead times offered as one-tap chips; anything else the user adds shows up beside them. */
private val LEAD_PRESETS = listOf(0, 10, 30, 60, 2 * 60, 24 * 60, 2 * 24 * 60, 7 * 24 * 60)

/** Units a lead time can be given in. No months: a month is not a fixed number of minutes. */
private val LEAD_UNITS = listOf(ReminderUnit.MINUTE, ReminderUnit.HOUR, ReminderUnit.DAY, ReminderUnit.WEEK)

private fun unitLabel(unit: ReminderUnit): String = when (unit) {
    ReminderUnit.MINUTE -> "min"
    ReminderUnit.HOUR -> "hr"
    ReminderUnit.DAY -> "days"
    ReminderUnit.WEEK -> "weeks"
    ReminderUnit.MONTH -> "months"
}

/** The dialog's one-line summary of a todo's reminders; tapping it opens [ReminderPlanDialog].
 * Greyed out until a deadline exists, but still visible so the option is discoverable. */
@Composable
internal fun ReminderRow(
    enabled: Boolean,
    isAllDay: Boolean,
    plan: ReminderPlan,
    onPlanChange: (ReminderPlan) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { open = true } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (!plan.isEmpty) Icons.Default.Alarm else Icons.Default.AlarmOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (enabled) plan.describe() else "Reminders (set a deadline first)",
                color = tint
            )
            if (enabled && !plan.isEmpty && isAllDay) {
                Text(
                    "All-day deadline: counts from ${formatMinuteOfDay(ALL_DAY_REMINDER_MINUTE_OF_DAY)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (open) {
        ReminderPlanDialog(
            initial = plan,
            onDismiss = { open = false },
            onConfirm = { onPlanChange(it); open = false }
        )
    }
}

/**
 * Builds a [ReminderPlan]: any number of lead times ("4, 5 and 6 hours before") plus an optional
 * "every so often until the deadline". Nothing is applied until Save, so backing out leaves the
 * todo's reminders as they were.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPlanDialog(
    initial: ReminderPlan,
    onDismiss: () -> Unit,
    onConfirm: (ReminderPlan) -> Unit
) {
    val leads = remember { mutableStateListOf<Int>().apply { addAll(initial.leadMinutes) } }
    var customAmount by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf(ReminderUnit.HOUR) }
    var repeatOn by remember { mutableStateOf(initial.every != null) }
    var everyAmount by remember { mutableStateOf((initial.every?.amount ?: 1).toString()) }
    var everyUnit by remember { mutableStateOf(initial.every?.unit ?: ReminderUnit.DAY) }

    val customMinutes = customAmount.toLongOrNull()
        ?.takeIf { it >= 1 }
        ?.let { it * customUnit.minutes }
        ?.takeIf { it <= ReminderPlan.MAX_LEAD_MINUTES }
        ?.toInt()
    val everyCount = everyAmount.toIntOrNull()?.takeIf { it in 1..ReminderPlan.MAX_EVERY_AMOUNT }
    val everySpan = if (repeatOn && everyCount != null) ReminderSpan(everyCount, everyUnit) else null
    val plan = ReminderPlan.of(leads, everySpan)
    // The repeat is clamped to a minimum gap, so say so rather than silently saving something else.
    val clamped = everySpan != null && plan.every != everySpan

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Remind me before the deadline", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (LEAD_PRESETS + leads).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            selected = minutes in leads,
                            onClick = { if (minutes in leads) leads.remove(minutes) else leads.add(minutes) },
                            label = { Text(ReminderPlan.leadLabel(minutes)) }
                        )
                    }
                }
                Text("Or add your own", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                    TextButton(
                        enabled = customMinutes != null,
                        onClick = {
                            customMinutes?.let { if (it !in leads) leads.add(it) }
                            customAmount = ""
                        }
                    ) { Text("Add") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEAD_UNITS.forEach { unit ->
                        FilterChip(
                            selected = customUnit == unit,
                            onClick = { customUnit = unit },
                            label = { Text(unitLabel(unit)) }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep reminding me", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (repeatOn) "Counted back from the due time, ending with it"
                            else "Every so often until the deadline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = repeatOn, onCheckedChange = { repeatOn = it })
                }
                if (repeatOn) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every")
                        OutlinedTextField(
                            value = everyAmount,
                            onValueChange = { everyAmount = it.filter(Char::isDigit).take(3) },
                            singleLine = true,
                            isError = everyCount == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = everyUnit == unit,
                                onClick = { everyUnit = unit },
                                label = { Text(unitLabel(unit)) }
                            )
                        }
                    }
                    Text(
                        if (clamped) "The shortest repeat is ${ReminderPlan.MIN_EVERY_MINUTES} minutes."
                        else "…until the deadline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (repeatOn || leads.isNotEmpty()) {
                    TextButton(
                        onClick = { leads.clear(); repeatOn = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Clear all") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(plan) },
                enabled = !repeatOn || everyCount != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
