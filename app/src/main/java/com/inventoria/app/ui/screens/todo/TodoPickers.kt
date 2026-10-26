package com.inventoria.app.ui.screens.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.Weekdays
import java.util.Calendar

/**
 * The date and time-of-day pickers the Todo dialog and the Schedule block dialog share. Both keep
 * a date (start-of-day millis) and a time (minutes since midnight) as separate fields, so these
 * hand back exactly those two shapes rather than a combined timestamp.
 */

/** Hands back the picked day as a start-of-day timestamp in the device's zone. */
internal fun showDatePicker(context: Context, initialTime: Long, onDateSelected: (Long) -> Unit) {
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

/** Time-of-day picker, handing back minutes since midnight rather than a timestamp -- the date
 * lives in its own field and must stay a start-of-day value. 24-hour, matching showDateTimePicker
 * on the Task tracker. */
internal fun showTimePicker(context: Context, initialMinuteOfDay: Int?, onTimeSelected: (Int) -> Unit) {
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

private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Seven round day buttons, Monday to Sunday, for choosing which days of the week something repeats
 * on -- the same idea as the alarm clock's day picker. [selected] holds ISO days (1 = Monday) and
 * [onToggle] is told which one was tapped. A line underneath says it in words ("Mon, Wed, Fri",
 * "Weekdays"), because seven bare letters are easy to misread.
 */
@Composable
internal fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { day ->
                val on = day in selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggle(day) }
                        .semantics { contentDescription = Weekdays.name(day) + if (on) ", on" else ", off" }
                ) {
                    Text(
                        WEEKDAY_LETTERS[day - 1],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            Weekdays.describe(Weekdays.maskOf(selected)).ifEmpty { "Pick at least one day" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
