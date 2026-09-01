package com.inventoria.app.ui.screens.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
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
