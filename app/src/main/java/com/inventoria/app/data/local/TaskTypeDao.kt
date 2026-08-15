package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTypeDao {
    @Query("SELECT * FROM TaskType WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getVisibleTaskTypes(): Flow<List<TaskType>>

    @Query("SELECT * FROM TaskType WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllTaskTypesList(): List<TaskType>

    @Query("SELECT * FROM TaskType")
    suspend fun getAllTaskTypesForSyncList(): List<TaskType>

    /** Counts every row, soft-deleted included -- a user who deleted all their types has a
     * non-empty table, so seeding must not treat that as "never seeded". */
    @Query("SELECT COUNT(*) FROM TaskType")
    suspend fun countAllTaskTypes(): Int

    @Query("SELECT * FROM TaskType WHERE isDirty = 1")
    fun getDirtyTaskTypesFlow(): Flow<List<TaskType>>

    @Query("SELECT * FROM TaskType WHERE isDirty = 1")
    suspend fun getDirtyTaskTypesList(): List<TaskType>

    @Query("UPDATE TaskType SET isDirty = 0 WHERE id IN (:taskTypeIds)")
    suspend fun markTaskTypesClean(taskTypeIds: List<String>)

    @Query("SELECT * FROM TaskType WHERE id = :id LIMIT 1")
    suspend fun getTaskTypeById(id: String): TaskType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskType(taskType: TaskType)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskTypes(taskTypes: List<TaskType>)

    @Update
    suspend fun updateTaskType(taskType: TaskType)

    @Query("UPDATE TaskType SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteTaskTypeById(id: String, timestamp: Long)

    @Query("DELETE FROM TaskType WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedTaskTypes(threshold: Long)
}
