package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM Task WHERE isDeleted = 0 ORDER BY startTime DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM Task WHERE isDirty = 1")
    fun getDirtyTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM Task WHERE isDirty = 1")
    suspend fun getDirtyTasksList(): List<Task>

    @Query("UPDATE Task SET isDirty = 0 WHERE id IN (:taskIds)")
    suspend fun markTasksClean(taskIds: List<String>)

    @Query("SELECT * FROM Task")
    suspend fun getAllTasksForSyncList(): List<Task>

    @Query("SELECT * FROM Task WHERE isDeleted = 0 ORDER BY startTime DESC")
    fun getVisibleTasks(): Flow<List<Task>>

    /** One-shot equivalent of [getVisibleTasks], for callers that need the history once at the
     * moment of an action rather than a standing subscription to the whole table. */
    @Query("SELECT * FROM Task WHERE isDeleted = 0 ORDER BY startTime DESC")
    suspend fun getVisibleTasksList(): List<Task>

    @Query("SELECT * FROM Task WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): Task?

    @Query("SELECT * FROM Task WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun getTasksByGroupId(groupId: String): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE Task SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteTaskById(id: String, timestamp: Long)

    @Query("UPDATE Task SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE groupId = :groupId")
    suspend fun softDeleteTasksByGroupId(groupId: String, timestamp: Long)

    @Query("UPDATE Task SET isRunning = 0, endTime = :endTime, duration = :duration, score = :score, updatedAt = :timestamp, isDirty = 1 WHERE id = :taskId")
    suspend fun completeTask(taskId: String, endTime: Long, duration: Long, score: Int, timestamp: Long)

    // Fully-stopped sessions only (isSessionActive = 0) -- used for streak lookback. Segments
    // still belonging to an in-progress (paused-but-not-stopped) session don't count toward
    // OTHER sessions' momentum yet, since they might still be resumed/interrupted further.
    @Query("SELECT * FROM Task WHERE isSessionActive = 0 AND isDeleted = 0 ORDER BY endTime DESC LIMIT :limit")
    suspend fun getRecentCompletedTasks(limit: Int): List<Task>

    @Query("UPDATE Task SET isSessionActive = 0, updatedAt = :timestamp, isDirty = 1 WHERE groupId = :groupId")
    suspend fun endSession(groupId: String, timestamp: Long)

    @Query("UPDATE Task SET name = :newName, updatedAt = :timestamp, isDirty = 1 WHERE groupId = :groupId")
    suspend fun updateSessionName(groupId: String, newName: String, timestamp: Long)

    @Transaction
    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long, score: Int, timestamp: Long) {
        completeTask(taskId, endTime, duration, score, timestamp)
        endSession(groupId, timestamp)
    }

    @Query("DELETE FROM Task WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedTasks(threshold: Long)
}
