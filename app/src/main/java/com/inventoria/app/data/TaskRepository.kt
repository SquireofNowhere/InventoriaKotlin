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
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            val tasks = taskDao.getAllTasks().first()
            val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            lastTimestamp.set(maxT)
        }
    }

    private fun getNextTimestamp(minTimestamp: Long = 0L): Long {
        val now = System.currentTimeMillis()
        val base = maxOf(now, minTimestamp)
        while (true) {
            val last = lastTimestamp.get()
            val next = if (base > last) base else last + 1
            if (lastTimestamp.compareAndSet(last, next)) {
                return next
            }
        }
    }

    private fun hasMeaningfulChanges(old: Task, new: Task): Boolean {
        return old.name != new.name ||
                old.groupId != new.groupId ||
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

    fun getVisibleTasks(): Flow<List<Task>> = taskDao.getVisibleTasks()
    fun getAllTasksForSync(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        val timestamp = getNextTimestamp(task.updatedAt)
        taskDao.insertTask(task.copy(updatedAt = timestamp))
    }

    suspend fun insertTasks(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val maxInBatch = tasks.maxOf { it.updatedAt }
        val baseTime = getNextTimestamp(maxInBatch)
        val tasksWithTimestamps = tasks.mapIndexed { index, task ->
            task.copy(updatedAt = baseTime + index)
        }
        taskDao.insertTasks(tasksWithTimestamps)
    }

    suspend fun updateTask(task: Task) {
        val existing = taskDao.getTaskById(task.id)
        if (existing == null || hasMeaningfulChanges(existing, task)) {
            val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
            taskDao.updateTask(task.copy(updatedAt = timestamp))
        }
    }

    suspend fun updateSessionName(groupId: String, newName: String) {
        val existingGroupId = taskDao.getGroupIdByName(newName)
        if (existingGroupId != null && existingGroupId != groupId) {
            val timestamp = getNextTimestamp()
            taskDao.joinGroupAtomically(groupId, newName, existingGroupId, timestamp)
        } else {
            val tasks = taskDao.getTasksByGroupId(groupId)
            val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            val timestamp = getNextTimestamp(maxT)
            taskDao.updateSessionName(groupId, newName, timestamp)
        }
    }

    suspend fun updateSessionNameAndGroupId(oldGroupId: String, newName: String, newGroupId: String) {
        val timestamp = getNextTimestamp()
        taskDao.joinGroupAtomically(oldGroupId, newName, newGroupId, timestamp)
    }

    suspend fun updateSessionKind(groupId: String, newKind: TaskKind) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.updateSessionKindAndResetCustom(groupId, newKind, timestamp)
    }

    suspend fun endSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.endSession(groupId, timestamp)
    }

    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long) {
        val timestamp = getNextTimestamp()
        taskDao.stopTaskAndSession(taskId, groupId, endTime, duration, timestamp)
    }

    suspend fun softDeleteTask(id: String) {
        val existing = taskDao.getTaskById(id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        taskDao.softDeleteTaskById(id, timestamp)
    }

    suspend fun softDeleteSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.softDeleteTasksByGroupId(groupId, timestamp)
    }

    suspend fun purgeOldDeletedTasks(threshold: Long) {
        taskDao.purgeOldDeletedTasks(threshold)
    }
}
