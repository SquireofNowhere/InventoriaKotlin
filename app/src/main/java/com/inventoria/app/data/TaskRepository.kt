package com.inventoria.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventoria.app.ui.screens.task.RunningTask
import com.inventoria.app.ui.screens.task.TrackedTask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SerializableRunningTask(
    val id: UUID,
    val name: String,
    val startTime: Long
)

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("task_tracker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRunningTasks(tasks: List<RunningTask>) {
        val serializableTasks = tasks.map { SerializableRunningTask(it.id, it.name, it.startTime) }
        val json = gson.toJson(serializableTasks)
        prefs.edit().putString("running_tasks", json).apply()
    }

    fun loadRunningTasks(): List<SerializableRunningTask> {
        val json = prefs.getString("running_tasks", null) ?: return emptyList()
        val type = object : TypeToken<List<SerializableRunningTask>>() {}.type
        return gson.fromJson(json, type)
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
        return gson.fromJson(json, type)
    }
}
