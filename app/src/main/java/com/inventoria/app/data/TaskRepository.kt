package com.inventoria.app.data

import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Ensure monotonic timestamps for rapid operations
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            // Seed lastTimestamp with the highest value in the database to prevent clock-skew downgrades
            val tasks = taskDao.getAllTasks().first()
            val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            lastTimestamp.set(maxT)
        }
    }

    private fun getNextTimestamp(minTimestamp: Long = 0L): Long {
        val now = System.currentTimeMillis()
        val base = maxOf(now, minTimestamp)
        var last = lastTimestamp.get()
        while (true) {
            val next = if (base > last) base else last + 1
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
                old.isKindCustom != new.isKindCustom ||
                old.isDeleted != new.isDeleted
    }

    fun getRunningTasks(): Flow<List<Task>> = taskDao.getRunningTasks()
    
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    
    fun getVisibleTasks(): Flow<List<Task>> = taskDao.getVisibleTasks()

    // Used for sync to ensure tombstones are shared
    fun getAllTasksForSync(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.copy(updatedAt = getNextTimestamp(task.updatedAt)))
    }

    suspend fun insertTasks(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val maxInBatch = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val baseTime = getNextTimestamp(maxInBatch)
        taskDao.insertTasks(tasks.mapIndexed { index, task -> 
            task.copy(updatedAt = baseTime + index) 
        })
    }

    suspend fun updateTask(task: Task) {
        val current = taskDao.getTaskById(task.id)
        if (current == null || hasMeaningfulChanges(current, task)) {
            taskDao.updateTask(task.copy(updatedAt = getNextTimestamp(current?.updatedAt ?: 0L)))
        }
    }

    suspend fun updateSessionName(groupId: String, newName: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { !it.isNameCustom && it.name != newName }
        if (needsUpdate) {
            val maxCurrent = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            taskDao.updateSessionName(groupId, newName, getNextTimestamp(maxCurrent))
        }
    }

    suspend fun updateSessionKind(groupId: String, newKind: TaskKind) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { !it.isKindCustom && it.kind != newKind }
        if (needsUpdate) {
            val maxCurrent = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            taskDao.updateSessionKind(groupId, newKind, getNextTimestamp(maxCurrent))
        }
    }

    suspend fun endSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val needsUpdate = tasks.any { it.isSessionActive }
        if (needsUpdate) {
            val maxCurrent = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            taskDao.endSession(groupId, getNextTimestamp(maxCurrent))
        }
    }

    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxCurrent = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        taskDao.stopTaskAndSession(taskId, groupId, endTime, duration, getNextTimestamp(maxCurrent))
    }

    suspend fun softDeleteTask(id: String) {
        val current = taskDao.getTaskById(id)
        val timestamp = getNextTimestamp(current?.updatedAt ?: 0L)
        taskDao.softDeleteTaskById(id, timestamp)
    }

    suspend fun softDeleteSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxCurrent = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        taskDao.softDeleteTasksByGroupId(groupId, getNextTimestamp(maxCurrent))
    }

    suspend fun purgeOldDeletedTasks(threshold: Long) {
        taskDao.purgeOldDeletedTasks(threshold)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteSession(groupId: String) {
        taskDao.deleteTasksByGroupId(groupId)
    }
}
