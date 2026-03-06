package com.inventoria.app.data.local

import androidx.room.*
import com.inventoria.app.data.model.ItemLink
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ItemLink)

    @Delete
    suspend fun deleteLink(link: ItemLink)

    @Query("DELETE FROM item_links WHERE follower_id = :followerId AND leader_id = :leaderId")
    suspend fun removeLink(followerId: Long, leaderId: Long)

    @Query("SELECT * FROM item_links WHERE follower_id = :itemId")
    suspend fun getLeadersForItem(itemId: Long): List<ItemLink>

    @Query("SELECT * FROM item_links WHERE leader_id = :itemId")
    suspend fun getFollowersForItem(itemId: Long): List<ItemLink>

    @Query("SELECT * FROM item_links WHERE follower_id = :itemId OR leader_id = :itemId")
    fun getLinksForItemFlow(itemId: Long): Flow<List<ItemLink>>
    
    @Query("SELECT * FROM item_links")
    suspend fun getAllLinks(): List<ItemLink>

    @Query("SELECT * FROM item_links")
    fun getAllLinksFlow(): Flow<List<ItemLink>>
}
