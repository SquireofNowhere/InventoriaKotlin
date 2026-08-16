package com.inventoria.app.ui.screens.clock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.getDayLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PRESET_MINUTES = listOf(5, 10, 15, 25, 45, 60)

private fun formatAlarmTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

/**
 * Timers and alarms, as far as Android allows.
 *
 * The platform has no read access to alarms or timers -- no provider, no list, no countdown to
 * observe -- so this is not a mirror of the clock app and doesn't pretend to be. It does the three
 * things that are actually possible: start a timer or alarm (labelled with what it's for), show
 * the single readable value the system exposes (the next alarm), and hand off to the clock app for
 * anything that means editing or cancelling something we can't see.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClockViewModel
) {
    val nextAlarm by viewModel.nextAlarm.collectAsState()
    val runningTask by viewModel.runningTask.collectAsState()
    val alarmableTodos by viewModel.alarmableTodos.collectAsState()
    val actionFailed by viewModel.lastActionFailed.collectAsState()
    var customMinutes by remember { mutableStateOf("") }

    // The clock app can add or clear an alarm while this screen sits in the background; there's no
    // change notification for it, so re-read on the way back in rather than showing a stale time.
    LaunchedEffect(Unit) { viewModel.refreshNextAlarm() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Timers & Alarms", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!viewModel.hasClockApp) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No clock app on this device answers timer or alarm requests, so there's " +
                        "nothing to drive from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (actionFailed) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "The clock app didn't accept that.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissFailure() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            item { NextAlarmCard(nextAlarmTime = nextAlarm?.triggerTime) }

            item {
                SectionCard(title = "Start a timer", icon = Icons.Default.Timer) {
                    val label = runningTask?.name?.takeIf { it.isNotBlank() } ?: "Inventoria"
                    Text(
                        text = runningTask?.let { "Labelled \"${it.name}\", after the task you're tracking." }
                            ?: "Nothing is being tracked, so the timer is labelled \"Inventoria\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    // Scrolls rather than wraps: FlowRow is still opt-in experimental, and a row
                    // of six short chips is fine to swipe on a phone.
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESET_MINUTES.forEach { minutes ->
                            AssistChip(
                                onClick = { viewModel.startTimer(minutes, label) },
                                label = { Text("$minutes min") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { input -> customMinutes = input.filter { it.isDigit() }.take(4) },
                            label = { Text("Custom (min)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        val minutes = customMinutes.toIntOrNull() ?: 0
                        Button(
                            onClick = {
                                viewModel.startTimer(minutes, label)
                                customMinutes = ""
                            },
                            enabled = minutes > 0
                        ) { Text("Start") }
                    }
                }
            }

            item {
                SectionCard(title = "Open the clock app", icon = Icons.Default.OpenInNew) {
                    Text(
                        "Editing, snoozing or cancelling happens there -- Android doesn't let " +
                            "another app see or change existing alarms and timers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.openAlarms() }) { Text("Alarms") }
                        OutlinedButton(onClick = { viewModel.openTimers() }) { Text("Timers") }
                    }
                }
            }

            if (alarmableTodos.isNotEmpty()) {
                item {
                    Text(
                        "Ring for a todo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(alarmableTodos, key = { it.todo.id }) { entry ->
                    AlarmableTodoRow(
                        entry = entry,
                        onSetAlarm = { viewModel.setAlarm(entry.minuteOfDay, entry.todo.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(nextAlarmTime: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (nextAlarmTime != null) Icons.Default.Alarm else Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Next alarm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (nextAlarmTime != null) {
                    Text(
                        text = "${getDayLabel(nextAlarmTime)} at ${formatAlarmTime(nextAlarmTime)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "None set",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Worth stating plainly: this is every app's alarms, and it's the only alarm the
                // system will name. The ones behind it are invisible from here.
                Text(
                    "The soonest alarm on the device, whichever app set it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlarmableTodoRow(entry: AlarmableTodo, onSetAlarm: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.todo.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = (if (entry.isToday) "Today" else "Tomorrow") + " at ${formatMinuteOfDay(entry.minuteOfDay)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSetAlarm) {
                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Set alarm")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
