package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.ScheduleBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM ScheduleBlock WHERE isDeleted = 0 ORDER BY dayStart ASC, startMinuteOfDay ASC")
    fun getVisibleBlocks(): Flow<List<ScheduleBlock>>

    @Query("SELECT * FROM ScheduleBlock")
    suspend fun getAllBlocksForSyncList(): List<ScheduleBlock>

    @Query("SELECT * FROM ScheduleBlock WHERE isDirty = 1")
    fun getDirtyBlocksFlow(): Flow<List<ScheduleBlock>>

    @Query("SELECT * FROM ScheduleBlock WHERE isDirty = 1")
    suspend fun getDirtyBlocksList(): List<ScheduleBlock>

    @Query("UPDATE ScheduleBlock SET isDirty = 0 WHERE id IN (:blockIds)")
    suspend fun markBlocksClean(blockIds: List<String>)

    @Query("SELECT * FROM ScheduleBlock WHERE id = :id LIMIT 1")
    suspend fun getBlockById(id: String): ScheduleBlock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ScheduleBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ScheduleBlock>)

    @Update
    suspend fun updateBlock(block: ScheduleBlock)

    @Query("UPDATE ScheduleBlock SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteBlockById(id: String, timestamp: Long)

    /** Undo of the soft delete above -- see TodoDao.restoreTodoById for why the timestamp bumps. */
    @Query("UPDATE ScheduleBlock SET isDeleted = 0, updatedAt = :timestamp, isDirty = 1 WHERE id = :id")
    suspend fun restoreBlockById(id: String, timestamp: Long)

    @Query("DELETE FROM ScheduleBlock WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedBlocks(threshold: Long)
}
