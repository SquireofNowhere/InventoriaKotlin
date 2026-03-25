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

    @Query("SELECT * FROM ItemLink")
    fun getAllLinksFlow(): Flow<List<ItemLink>>

    @Query("SELECT * FROM ItemLink")
    suspend fun getAllLinksList(): List<ItemLink>

    @Query("SELECT * FROM ItemLink WHERE followerId = :itemId OR leaderId = :itemId")
    fun getLinksForItemFlow(itemId: Long): Flow<List<ItemLink>>

    @Query("SELECT * FROM ItemLink WHERE followerId = :followerId AND leaderId = :leaderId LIMIT 1")
    suspend fun getLink(followerId: Long, leaderId: Long): ItemLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ItemLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<ItemLink>)

    @Query("DELETE FROM ItemLink WHERE followerId = :followerId AND leaderId = :leaderId")
    suspend fun removeLink(followerId: Long, leaderId: Long)

    @Query("DELETE FROM ItemLink WHERE followerId = :itemId OR leaderId = :itemId")
    suspend fun removeLinksForItem(itemId: Long)

    @Query("DELETE FROM ItemLink")
    suspend fun deleteAllLinks()
}
