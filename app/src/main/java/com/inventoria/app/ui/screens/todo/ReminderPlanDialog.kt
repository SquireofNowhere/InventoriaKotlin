package com.inventoria.app.ui.screens.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
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
import com.inventoria.app.data.model.ALL_DAY_REMINDER_MINUTE_OF_DAY
import com.inventoria.app.data.model.ReminderPlan
import com.inventoria.app.data.model.ReminderSchedule
import com.inventoria.app.data.model.ReminderSpan
import com.inventoria.app.data.model.ReminderUnit
import com.inventoria.app.data.model.ScheduleKind
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.formatSimpleDate
import java.util.Calendar

/** The lead times offered as one-tap chips; anything else the user adds shows up beside them. */
private val LEAD_PRESETS = listOf(0, 10, 30, 60, 2 * 60, 24 * 60, 2 * 24 * 60, 7 * 24 * 60)

/** Units a lead time can be given in. No months: a month is not a fixed number of minutes. */
private val LEAD_UNITS = listOf(ReminderUnit.MINUTE, ReminderUnit.HOUR, ReminderUnit.DAY, ReminderUnit.WEEK)

private val SCHEDULE_KIND_LABELS = listOf(
    ScheduleKind.DAILY to "Daily",
    ScheduleKind.WEEKLY to "Weekly",
    ScheduleKind.MONTHLY to "Monthly",
    ScheduleKind.ONCE to "Once"
)

private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun unitLabel(unit: ReminderUnit): String = when (unit) {
    ReminderUnit.MINUTE -> "min"
    ReminderUnit.HOUR -> "hr"
    ReminderUnit.DAY -> "days"
    ReminderUnit.WEEK -> "weeks"
    ReminderUnit.MONTH -> "months"
}

/** A yyyyMMdd date as a start-of-day timestamp, for the date picker and the date label. */
private fun dateMillis(date: Int): Long = Calendar.getInstance().apply {
    set(date / 10_000, date / 100 % 100 - 1, date % 100, 0, 0, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dateOf(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return ReminderSchedule.dateOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/**
 * The todo dialog's one quiet line about reminders; tapping it opens [ReminderPlanDialog]. Always
 * tappable: schedules need no deadline, so the row no longer waits for one. It says "Add reminders"
 * until there is something to say, and otherwise sums the plan up on at most two lines.
 */
@Composable
internal fun ReminderRow(
    hasDeadline: Boolean,
    isAllDay: Boolean,
    plan: ReminderPlan,
    onPlanChange: (ReminderPlan) -> Unit
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
        Icon(
            if (!plan.isEmpty) Icons.Default.Alarm else Icons.Default.AlarmOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (plan.isEmpty) "Add reminders" else plan.summary(),
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val note = when {
                plan.needsDeadline && !hasDeadline -> "Deadline reminders won't ring until there is a deadline"
                plan.needsDeadline && isAllDay ->
                    "All-day deadline: counts from ${formatMinuteOfDay(ALL_DAY_REMINDER_MINUTE_OF_DAY)}"
                else -> null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
    if (open) {
        ReminderPlanDialog(
            initial = plan,
            hasDeadline = hasDeadline,
            onDismiss = { open = false },
            onConfirm = { onPlanChange(it); open = false }
        )
    }
}

/**
 * Builds a [ReminderPlan] in three independent sections: lead times before the deadline ("4, 5 and
 * 6 hours before"), "keep reminding me" until the deadline, and calendar schedules that need no
 * deadline ("every day at 08:00", "the 15th of every month, every 5 minutes from 09:00"). The first
 * two are switched off until the todo has a deadline. Nothing is applied until Save, so backing out
 * leaves the todo's reminders as they were.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPlanDialog(
    initial: ReminderPlan,
    hasDeadline: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ReminderPlan) -> Unit
) {
    val leads = remember { mutableStateListOf<Int>().apply { addAll(initial.leadMinutes) } }
    val schedules = remember { mutableStateListOf<ReminderSchedule>().apply { addAll(initial.schedules) } }
    var customAmount by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf(ReminderUnit.HOUR) }
    var repeatOn by remember { mutableStateOf(initial.every != null) }
    var everyAmount by remember { mutableStateOf((initial.every?.amount ?: 1).toString()) }
    var everyUnit by remember { mutableStateOf(initial.every?.unit ?: ReminderUnit.DAY) }
    // null = closed, -1 = adding a new schedule, otherwise the index being edited.
    var editing by remember { mutableStateOf<Int?>(null) }

    val customMinutes = customAmount.toLongOrNull()
        ?.takeIf { it >= 1 }
        ?.let { it * customUnit.minutes }
        ?.takeIf { it <= ReminderPlan.MAX_LEAD_MINUTES }
        ?.toInt()
    val everyCount = everyAmount.toIntOrNull()?.takeIf { it in 1..ReminderPlan.MAX_EVERY_AMOUNT }
    val everySpan = if (repeatOn && everyCount != null) ReminderSpan(everyCount, everyUnit) else null
    val plan = ReminderPlan.of(leads, everySpan, schedules)
    // The repeat is clamped to a minimum gap, so say so rather than silently saving something else.
    val clamped = everySpan != null && plan.every != everySpan
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Before the deadline", style = MaterialTheme.typography.titleSmall)
                if (!hasDeadline) {
                    Text(
                        "Set a deadline to be reminded before it. A schedule below needs none.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (LEAD_PRESETS + leads).distinct().sorted().forEach { minutes ->
                        FilterChip(
                            enabled = hasDeadline,
                            selected = minutes in leads,
                            onClick = { if (minutes in leads) leads.remove(minutes) else leads.add(minutes) },
                            label = { Text(ReminderPlan.leadLabel(minutes)) }
                        )
                    }
                }
                Text("Or add your own", style = MaterialTheme.typography.labelMedium, color = muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        enabled = hasDeadline,
                        value = customAmount,
                        onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                    TextButton(
                        enabled = hasDeadline && customMinutes != null,
                        onClick = {
                            customMinutes?.let { if (it !in leads) leads.add(it) }
                            customAmount = ""
                        }
                    ) { Text("Add") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEAD_UNITS.forEach { unit ->
                        FilterChip(
                            enabled = hasDeadline,
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
                            color = muted
                        )
                    }
                    Switch(enabled = hasDeadline, checked = repeatOn, onCheckedChange = { repeatOn = it })
                }
                if (repeatOn) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every")
                        OutlinedTextField(
                            enabled = hasDeadline,
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
                                enabled = hasDeadline,
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
                        color = muted
                    )
                }

                HorizontalDivider()

                // Schedules: the "more than one kind of alarm" half. Listed, not expanded -- a
                // schedule that rings 200 times a day is still one line.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("On a schedule", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Daily, weekly, monthly or on one date. No deadline needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                    TextButton(
                        enabled = schedules.size < ReminderPlan.MAX_SCHEDULES,
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
                        Column(Modifier.weight(1f)) {
                            Text(schedule.describe(), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (schedule.ringsPerDay > 1) {
                                Text(
                                    "${schedule.ringsPerDay} rings that day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted
                                )
                            }
                        }
                        IconButton(onClick = { schedules.removeAt(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove schedule", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (repeatOn || leads.isNotEmpty() || schedules.isNotEmpty()) {
                    TextButton(
                        onClick = { leads.clear(); schedules.clear(); repeatOn = false },
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

    editing?.let { index ->
        ScheduleEditorDialog(
            initial = schedules.getOrNull(index),
            onDismiss = { editing = null },
            onConfirm = { schedule ->
                if (index in schedules.indices) schedules[index] = schedule else schedules.add(schedule)
                editing = null
            }
        )
    }
}

/**
 * One schedule: how often (daily, weekly on chosen days, monthly on a day, or once on a date), at
 * what time, and optionally "ring again and again" every so many minutes until a later time that
 * day. The line under the fields reads the result back in words and counts the rings, so the
 * answer to "what did I just set?" never needs a second dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleEditorDialog(
    initial: ReminderSchedule?,
    onDismiss: () -> Unit,
    onConfirm: (ReminderSchedule) -> Unit,
    /** False hides "Ring again and again" -- right for a schedule that starts something, where a
     * second start the same day means nothing. */
    allowBurst: Boolean = true,
    title: String = if (initial == null) "Add a schedule" else "Edit schedule"
) {
    val context = LocalContext.current
    val tomorrow = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) } }
    var kind by remember { mutableStateOf(initial?.kind ?: ScheduleKind.DAILY) }
    var minute by remember { mutableStateOf(initial?.minuteOfDay ?: 9 * 60) }
    val weekdays = remember { mutableStateListOf<Int>().apply { addAll(initial?.weekdays ?: setOf(1, 2, 3, 4, 5)) } }
    var dayText by remember {
        mutableStateOf((initial?.takeIf { it.kind == ScheduleKind.MONTHLY }?.dayOfMonth ?: tomorrow.get(Calendar.DAY_OF_MONTH)).toString())
    }
    var date by remember {
        mutableStateOf(
            initial?.takeIf { it.kind == ScheduleKind.ONCE }?.date
                ?: ReminderSchedule.dateOf(
                    tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH) + 1, tomorrow.get(Calendar.DAY_OF_MONTH)
                )
        )
    }
    val wasRepeating = allowBurst && (initial?.burstEveryMinutes ?: 0) > 0
    var burstOn by remember { mutableStateOf(wasRepeating) }
    var everyText by remember { mutableStateOf((initial?.burstEveryMinutes?.takeIf { it > 0 } ?: 30).toString()) }
    var untilMinute by remember {
        mutableStateOf(
            if (wasRepeating) initial!!.burstUntilMinute
            else minOf((initial?.minuteOfDay ?: 9 * 60) + 8 * 60, ReminderSchedule.MINUTES_PER_DAY - 1)
        )
    }

    val everyMinutes = everyText.toIntOrNull() ?: 0
    val candidate = ReminderSchedule(
        kind = kind,
        minuteOfDay = minute,
        date = date,
        weekdays = weekdays.toSet(),
        dayOfMonth = dayText.toIntOrNull() ?: 0,
        burstEveryMinutes = if (burstOn) everyMinutes else 0,
        burstUntilMinute = untilMinute
    ).normalised()
    val burstProblem = when {
        !burstOn -> null
        everyMinutes < ReminderSchedule.MIN_BURST_EVERY_MINUTES -> "The shortest gap is ${ReminderSchedule.MIN_BURST_EVERY_MINUTES} minutes."
        untilMinute <= minute -> "Pick an end time after ${formatMinuteOfDay(minute)}."
        else -> null
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCHEDULE_KIND_LABELS.forEach { (option, label) ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(label) })
                    }
                }
                when (kind) {
                    ScheduleKind.WEEKLY -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WEEKDAY_LABELS.forEachIndexed { i, label ->
                            val day = i + 1
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { if (day in weekdays) weekdays.remove(day) else weekdays.add(day) },
                                label = { Text(label) }
                            )
                        }
                    }
                    ScheduleKind.MONTHLY -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("On day")
                            OutlinedTextField(
                                value = dayText,
                                onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                                singleLine = true,
                                isError = dayText.toIntOrNull() !in 1..31,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp)
                            )
                            Text("of every month")
                        }
                        Text(
                            "A month without that day rings on its last day.",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                    ScheduleKind.ONCE -> Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker(context, dateMillis(date)) { date = dateOf(it) } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(formatSimpleDate(dateMillis(date)))
                    }
                    ScheduleKind.DAILY -> Unit
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker(context, minute) { minute = it } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("At ${formatMinuteOfDay(minute)}")
                }

                if (allowBurst) Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ring again and again", style = MaterialTheme.typography.titleSmall)
                        Text("Several times on the day, not just once", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                    Switch(checked = burstOn, onCheckedChange = { burstOn = it })
                }
                if (allowBurst && burstOn) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every")
                        OutlinedTextField(
                            value = everyText,
                            onValueChange = { everyText = it.filter(Char::isDigit).take(4) },
                            singleLine = true,
                            isError = everyMinutes < ReminderSchedule.MIN_BURST_EVERY_MINUTES,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(88.dp)
                        )
                        Text("min")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker(context, untilMinute) { untilMinute = it } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Until ${formatMinuteOfDay(untilMinute)}")
                    }
                }

                HorizontalDivider()
                val summary = when {
                    burstProblem != null -> burstProblem
                    candidate == null -> "Choose at least one day."
                    candidate.ringsPerDay > 1 -> "${candidate.describe()} (${candidate.ringsPerDay} rings)"
                    else -> candidate.describe()
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (burstProblem != null || candidate == null) MaterialTheme.colorScheme.error else muted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = candidate != null && burstProblem == null,
                onClick = { candidate?.let(onConfirm) }
            ) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
