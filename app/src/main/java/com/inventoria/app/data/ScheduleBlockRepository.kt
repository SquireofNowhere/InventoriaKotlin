package com.inventoria.app.data

import com.inventoria.app.data.local.ScheduleBlockDao
import com.inventoria.app.data.model.ScheduleBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Schedule blocks -- see [ScheduleBlock]. Same shape as TodoRepository, including the monotonic
 * updatedAt idiom that keeps two same-millisecond edits from tying during last-write-wins sync. */
@Singleton
class ScheduleBlockRepository @Inject constructor(
    private val scheduleBlockDao: ScheduleBlockDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            val blocks = scheduleBlockDao.getVisibleBlocks().first()
            lastTimestamp.set(blocks.maxOfOrNull { it.updatedAt } ?: 0L)
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

    fun getVisibleBlocks(): Flow<List<ScheduleBlock>> = scheduleBlockDao.getVisibleBlocks()

    suspend fun getBlockById(id: String): ScheduleBlock? = scheduleBlockDao.getBlockById(id)

    suspend fun insertBlock(block: ScheduleBlock) {
        val timestamp = getNextTimestamp(block.updatedAt)
        scheduleBlockDao.insertBlock(block.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun updateBlock(block: ScheduleBlock) {
        val existing = scheduleBlockDao.getBlockById(block.id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        scheduleBlockDao.updateBlock(block.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun softDeleteBlock(id: String) {
        val existing = scheduleBlockDao.getBlockById(id)
        scheduleBlockDao.softDeleteBlockById(id, getNextTimestamp(existing?.updatedAt ?: 0L))
    }

    /** Puts a soft-deleted block back; the timestamp must beat the tombstone's own. */
    suspend fun restoreBlock(id: String) {
        val existing = scheduleBlockDao.getBlockById(id)
        scheduleBlockDao.restoreBlockById(id, getNextTimestamp(existing?.updatedAt ?: 0L))
    }

    suspend fun purgeOldDeletedBlocks(threshold: Long) {
        scheduleBlockDao.purgeOldDeletedBlocks(threshold)
    }
}
