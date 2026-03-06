package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isRunning = 1")
    fun getRunningTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isRunning = 0 ORDER BY endTime DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE groupId = :groupId")
    suspend fun getTasksByGroupId(groupId: String): List<Task>

    @Upsert
    suspend fun insertTask(task: Task)

    @Upsert
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE groupId = :groupId")
    suspend fun deleteTasksByGroupId(groupId: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("UPDATE tasks SET name = :newName, updatedAt = :timestamp WHERE groupId = :groupId AND isNameCustom = 0")
    suspend fun updateSessionName(groupId: String, newName: String, timestamp: Long)

    @Query("UPDATE tasks SET kind = :newKind, updatedAt = :timestamp WHERE groupId = :groupId AND isKindCustom = 0")
    suspend fun updateSessionKind(groupId: String, newKind: TaskKind, timestamp: Long)
    
    @Query("UPDATE tasks SET isSessionActive = 0, isRunning = 0, isPaused = 0, updatedAt = :timestamp WHERE groupId = :groupId")
    suspend fun endSession(groupId: String, timestamp: Long)

    @Query("UPDATE tasks SET isRunning = 0, isPaused = 0, isSessionActive = 0, endTime = :endTime, duration = :duration, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun completeTask(taskId: String, endTime: Long, duration: Long, timestamp: Long)

    @Transaction
    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long, timestamp: Long) {
        completeTask(taskId, endTime, duration, timestamp)
        endSession(groupId, timestamp)
    }
}
