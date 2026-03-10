package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.ItemLink
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemLinkDao {
    @Query("SELECT * FROM ItemLink")
    fun getAllLinksFlow(): Flow<List<ItemLink>>

    @Query("SELECT * FROM ItemLink")
    suspend fun getAllLinksList(): List<ItemLink>

    @Query("SELECT * FROM ItemLink WHERE followerId = :itemId OR leaderId = :itemId")
    fun getLinksForItemFlow(itemId: Long): Flow<List<ItemLink>>

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
