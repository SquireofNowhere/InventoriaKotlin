package com.inventoria.app.data.repository

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "CalendarRepository"

    private val EVENT_PROJECTION = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION
    )

    suspend fun getInventoriaTasksFromCalendar(daysBack: Int = 30): List<Task> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<Task>()
        val contentResolver = context.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI

        // Scan events from the specified number of days back
        val startTime = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }.timeInMillis
        val selection = "${CalendarContract.Events.DTSTART} >= ?"
        val selectionArgs = arrayOf(startTime.toString())

        try {
            val cursor = contentResolver.query(uri, EVENT_PROJECTION, selection, selectionArgs, null)
            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)

                while (it.moveToNext()) {
                    val description = it.getString(descIdx) ?: ""
                    if (description.contains("Tracked via Inventoria Task Tracker")) {
                        val title = it.getString(titleIdx) ?: "Untitled"
                        val start = it.getLong(startIdx)
                        val end = it.getLong(endIdx)
                        
                        val kind = parseKindFromDescription(description)
                        val taskId = parseTaskIdFromDescription(description) ?: "cal_${it.getLong(idIdx)}"
                        val sessionId = parseSessionIdFromDescription(description) ?: "cal_group_${title}_${kind.name}"
                        
                        tasks.add(Task(
                            id = taskId,
                            groupId = sessionId,
                            name = title,
                            kind = kind,
                            startTime = start,
                            endTime = if (end > 0) end else null,
                            duration = if (end > start) end - start else 0L,
                            isRunning = false,
                            isPaused = false,
                            isSessionActive = false,
                            savedToCalendar = true,
                            updatedAt = start 
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calendar", e)
        }

        tasks
    }

    private fun parseKindFromDescription(description: String): TaskKind {
        val lines = description.lines()
        val typeLine = lines.find { it.startsWith("Type: ") }
        if (typeLine != null) {
            val kindNameWithExtra = typeLine.substringAfter("Type: ")
            // The display name format is "EMOJI Name • Status"
            return TaskKind.entries.find { kindNameWithExtra.startsWith(it.displayName) } ?: TaskKind.GRAPHITE
        }
        return TaskKind.GRAPHITE
    }

    private fun parseTaskIdFromDescription(description: String): String? {
        val lines = description.lines()
        val idLine = lines.find { it.startsWith("Task ID: ") }
        return idLine?.substringAfter("Task ID: ")?.trim()?.let { "cal_$it" }
    }

    private fun parseSessionIdFromDescription(description: String): String? {
        val lines = description.lines()
        val idLine = lines.find { it.startsWith("Session ID: ") }
        return idLine?.substringAfter("Session ID: ")?.trim()
    }
}
