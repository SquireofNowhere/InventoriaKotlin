package com.inventoria.app.data

import android.util.Log
import com.inventoria.app.data.local.TaskTypeDao
import com.inventoria.app.data.model.DEFAULT_TASK_TYPE_NAMES
import com.inventoria.app.data.model.TaskType
import com.inventoria.app.data.model.defaultTaskTypeId
import com.inventoria.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskTypeRepository @Inject constructor(
    private val taskTypeDao: TaskTypeDao,
    private val settingsRepository: SettingsRepository
) {
    private val lastTimestamp = AtomicLong(0L)

    /** Same monotonic-timestamp idiom as TaskRepository -- updatedAt drives last-write-wins
     * conflict resolution during sync, so two edits in the same millisecond must not tie. */
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

    fun getVisibleTaskTypes(): Flow<List<TaskType>> = taskTypeDao.getVisibleTaskTypes()

    suspend fun getTaskTypeById(id: String): TaskType? = taskTypeDao.getTaskTypeById(id)

    suspend fun addTaskType(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val timestamp = getNextTimestamp()
        taskTypeDao.insertTaskType(
            TaskType(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                updatedAt = timestamp,
                isDirty = true
            )
        )
    }

    suspend fun renameTaskType(taskType: TaskType, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == taskType.name) return
        val timestamp = getNextTimestamp(taskType.updatedAt)
        taskTypeDao.updateTaskType(
            taskType.copy(name = trimmed, updatedAt = timestamp, isDirty = true)
        )
    }

    /** Soft delete, consistent with every other entity -- the row survives so tasks still
     * referencing this id keep resolving, and the deletion itself syncs to other devices. */
    suspend fun deleteTaskType(id: String) {
        val existing = taskTypeDao.getTaskTypeById(id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        taskTypeDao.softDeleteTaskTypeById(id, timestamp)
    }

    /**
     * Seeds the defaults exactly once per install. Call *after* the initial Firebase pull so an
     * existing account's own types arrive first and the table is non-empty.
     *
     * Two independent guards, because each covers a case the other misses:
     *  - the seeded flag stops deleted defaults from reappearing on the next launch;
     *  - the row count stops a fresh install on an existing account from seeding over synced data.
     *
     * A race between two devices is harmless regardless: [defaultTaskTypeId] is deterministic, so
     * both produce identical ids and the insert collapses to a REPLACE rather than duplicating.
     */
    suspend fun seedDefaultsIfNeeded() {
        try {
            if (settingsRepository.hasSeededTaskTypes().first()) return
            if (taskTypeDao.countAllTaskTypes() > 0) {
                // Types already arrived from sync -- nothing to seed, but don't seed later either.
                settingsRepository.setTaskTypesSeeded()
                return
            }
            val now = getNextTimestamp()
            val defaults = DEFAULT_TASK_TYPE_NAMES.mapIndexed { index, name ->
                TaskType(
                    id = defaultTaskTypeId(name),
                    name = name,
                    updatedAt = now + index,
                    isDirty = true
                )
            }
            taskTypeDao.insertTaskTypes(defaults)
            settingsRepository.setTaskTypesSeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Seeding default task types failed", e)
        }
    }

    companion object {
        private const val TAG = "TaskTypeRepository"
    }
}
