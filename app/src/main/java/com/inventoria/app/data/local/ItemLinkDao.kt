package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.ItemLink
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemLinkDao {
    @Query("SELECT * FROM ItemLink WHERE isDirty = 1")
    fun getDirtyLinksFlow(): Flow<List<ItemLink>>

    @Query("SELECT * FROM ItemLink WHERE isDirty = 1")
    suspend fun getDirtyLinksList(): List<ItemLink>

    @Query("UPDATE ItemLink SET isDirty = 0 WHERE followerId = :followerId AND leaderId = :leaderId")
    suspend fun markLinkClean(followerId: Long, leaderId: Long)

    @Transaction
    suspend fun markLinksClean(links: List<ItemLink>) {
        links.forEach { markLinkClean(it.followerId, it.leaderId) }
    }

    @Query("SELECT * FROM ItemLink WHERE isDeleted = 0")
    fun getAllLinksFlow(): Flow<List<ItemLink>>

    @Query("SELECT * FROM ItemLink WHERE isDeleted = 0")
    suspend fun getAllLinksList(): List<ItemLink>

    // Unfiltered -- sync must push tombstones (isDeleted=1 rows) too, or a removed link never
    // reaches other devices/Firebase at all. See ErrorLog.md #33.
    @Query("SELECT * FROM ItemLink")
    suspend fun getAllLinksForSyncList(): List<ItemLink>

    @Query("SELECT * FROM ItemLink WHERE (followerId = :itemId OR leaderId = :itemId) AND isDeleted = 0")
    fun getLinksForItemFlow(itemId: Long): Flow<List<ItemLink>>

    // Unfiltered by design: used for sync merge-decision (comparing updatedAt against the
    // incoming cloud row), which needs the raw row -- tombstoned or not.
    @Query("SELECT * FROM ItemLink WHERE followerId = :followerId AND leaderId = :leaderId LIMIT 1")
    suspend fun getLink(followerId: Long, leaderId: Long): ItemLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ItemLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<ItemLink>)

    @Query("UPDATE ItemLink SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE followerId = :followerId AND leaderId = :leaderId")
    suspend fun removeLink(followerId: Long, leaderId: Long, timestamp: Long)

    @Query("UPDATE ItemLink SET isDeleted = 1, updatedAt = :timestamp, isDirty = 1 WHERE (followerId = :itemId OR leaderId = :itemId) AND isDeleted = 0")
    suspend fun removeLinksForItem(itemId: Long, timestamp: Long)

    @Query("DELETE FROM ItemLink")
    suspend fun deleteAllLinks()

    @Query("DELETE FROM ItemLink WHERE isDeleted = 1 AND updatedAt < :threshold")
    suspend fun purgeOldDeletedLinks(threshold: Long)
}
