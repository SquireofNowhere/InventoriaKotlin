package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM Task WHERE isDeleted = 0 ORDER BY startTime DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM Task WHERE isDeleted = 0")
    suspend fun getAllTasksList(): List<Task>

    @Query("SELECT * FROM Task WHERE isDeleted = 0 ORDER BY startTime DESC")
    fun getVisibleTasks(): Flow<List<Task>>

    @Query("SELECT * FROM Task WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): Task?

    @Query("SELECT * FROM Task WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun getTasksByGroupId(groupId: String): List<Task>

    @Query("SELECT groupId FROM Task WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getGroupIdByName(name: String): String?

    @Query("SELECT kind FROM Task WHERE groupId = :groupId AND isDeleted = 0 LIMIT 1")
    suspend fun getKindByGroupId(groupId: String): TaskKind?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE Task SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTaskById(id: String, timestamp: Long)

    @Query("UPDATE Task SET isDeleted = 1, updatedAt = :timestamp WHERE groupId = :groupId")
    suspend fun softDeleteTasksByGroupId(groupId: String, timestamp: Long)

    @Query("UPDATE Task SET isRunning = 0, endTime = :endTime, duration = :duration, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun completeTask(taskId: String, endTime: Long, duration: Long, timestamp: Long)

    @Query("UPDATE Task SET isSessionActive = 0, updatedAt = :timestamp WHERE groupId = :groupId")
    suspend fun endSession(groupId: String, timestamp: Long)

    @Query("UPDATE Task SET name = :newName, updatedAt = :timestamp WHERE groupId = :groupId")
    suspend fun updateSessionName(groupId: String, newName: String, timestamp: Long)

    @Query("UPDATE Task SET name = :newName, groupId = :newGroupId, updatedAt = :timestamp WHERE groupId = :oldGroupId")
    suspend fun updateSessionNameAndGroupId(oldGroupId: String, newName: String, newGroupId: String, timestamp: Long)

    @Query("UPDATE Task SET kind = :newKind, isKindCustom = 0, updatedAt = :timestamp WHERE groupId = :groupId")
    suspend fun updateSessionKindAndResetCustom(groupId: String, newKind: TaskKind, timestamp: Long)

    @Transaction
    suspend fun joinGroupAtomically(oldGroupId: String, newName: String, newGroupId: String, timestamp: Long) {
        val targetKind = getKindByGroupId(newGroupId)
        updateSessionNameAndGroupId(oldGroupId, newName, newGroupId, timestamp)
        if (targetKind != null) {
            updateSessionKindAndResetCustom(newGroupId, targetKind, timestamp + 1)
        }
    }

    @Transaction
    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long, timestamp: Long) {
        completeTask(taskId, endTime, duration, timestamp)
        endSession(groupId, timestamp)
    }

    @Query("DELETE FROM Task WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedTasks(threshold: Long)
}
