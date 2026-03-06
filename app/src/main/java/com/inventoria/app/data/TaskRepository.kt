package com.inventoria.app.data

import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    // Ensure monotonic timestamps for rapid operations
    private val lastTimestamp = AtomicLong(0L)

    private fun getNextTimestamp(): Long {
        val now = System.currentTimeMillis()
        var last = lastTimestamp.get()
        while (true) {
            val next = if (now > last) now else last + 1
            if (lastTimestamp.compareAndSet(last, next)) {
                return next
            }
            last = lastTimestamp.get()
        }
    }

    private fun hasMeaningfulChanges(old: Task, new: Task): Boolean {
        return old.name != new.name ||
                old.kind != new.kind ||
                old.startTime != new.startTime ||
                old.endTime != new.endTime ||
                old.duration != new.duration ||
                old.isRunning != new.isRunning ||
                old.isPaused != new.isPaused ||
                old.isSessionActive != new.isSessionActive ||
                old.savedToCalendar != new.savedToCalendar ||
                old.savedToCalendarAt != new.savedToCalendarAt ||
                old.isNameCustom != new.isNameCustom ||
                old.isKindCustom != new.isKindCustom
    }

    fun getRunningTasks(): Flow<List<Task>> = taskDao.getRunningTasks()
    
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.copy(updatedAt = getNextTimestamp()))
    }

    suspend fun insertTasks(tasks: List<Task>) {
        val baseTime = getNextTimestamp()
        taskDao.insertTasks(tasks.mapIndexed { index, task -> 
            task.copy(updatedAt = baseTime + index) 
        })
    }

    suspend fun updateTask(task: Task) {
        val current = taskDao.getTasksByGroupId(task.groupId).find { it.id == task.id }
        if (current == null || hasMeaningfulChanges(current, task)) {
            taskDao.updateTask(task.copy(updatedAt = getNextTimestamp()))
        }
    }

    suspend fun updateSessionName(groupId: String, newName: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { !it.isNameCustom && it.name != newName }
        if (needsUpdate) {
            taskDao.updateSessionName(groupId, newName, getNextTimestamp())
        }
    }

    suspend fun updateSessionKind(groupId: String, newKind: TaskKind) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { !it.isKindCustom && it.kind != newKind }
        if (needsUpdate) {
            taskDao.updateSessionKind(groupId, newKind, getNextTimestamp())
        }
    }

    suspend fun endSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { it.isSessionActive }
        if (needsUpdate) {
            taskDao.endSession(groupId, getNextTimestamp())
        }
    }

    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long) {
        // Always allow stop as it's a critical state change
        taskDao.stopTaskAndSession(taskId, groupId, endTime, duration, getNextTimestamp())
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteSession(groupId: String) {
        taskDao.deleteTasksByGroupId(groupId)
    }
}
