package com.inventoria.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventoria.app.ui.screens.task.RunningTask
import com.inventoria.app.ui.screens.task.TrackedTask
import com.inventoria.app.ui.screens.task.TaskKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SerializableRunningTask(
    val id: UUID,
    val name: String,
    val kind: TaskKind?,
    val startTime: Long
)

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("task_tracker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRunningTasks(tasks: List<RunningTask>) {
        val serializableTasks = tasks.map { SerializableRunningTask(it.id, it.name, it.kind, it.startTime) }
        val json = gson.toJson(serializableTasks)
        prefs.edit().putString("running_tasks", json).apply()
    }

    fun loadRunningTasks(): List<SerializableRunningTask> {
        val json = prefs.getString("running_tasks", null) ?: return emptyList()
        val type = object : TypeToken<List<SerializableRunningTask>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCompletedTasks(tasks: List<TrackedTask>) {
        // Only save tasks that are NOT saved to calendar
        val tasksToPersist = tasks.filter { !it.savedToCalendar }
        val json = gson.toJson(tasksToPersist)
        prefs.edit().putString("completed_tasks", json).apply()
    }

    fun loadCompletedTasks(): List<TrackedTask> {
        val json = prefs.getString("completed_tasks", null) ?: return emptyList()
        val type = object : TypeToken<List<TrackedTask>>() {}.type
        return try {
            val tasks: List<TrackedTask> = gson.fromJson(json, type) ?: emptyList()
            // Sanitize tasks to ensure no null kinds (important for backward compatibility)
            tasks.map { task ->
                if (task.kind == null) {
                    task.copy(kind = TaskKind.NEUTRAL_WAITING)
                } else {
                    task
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
