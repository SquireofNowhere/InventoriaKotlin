package com.inventoria.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.firebase.database.PropertyName

@Entity(
    tableName = "item_links",
    primaryKeys = ["follower_id", "leader_id"],
    foreignKeys = [
        ForeignKey(
            entity = InventoryItem::class,
            parentColumns = ["id"],
            childColumns = ["follower_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryItem::class,
            parentColumns = ["id"],
            childColumns = ["leader_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("leader_id")]
)
data class ItemLink(
    @ColumnInfo(name = "follower_id")
    @get:PropertyName("followerId")
    @set:PropertyName("followerId")
    var followerId: Long = 0,
    
    @ColumnInfo(name = "leader_id")
    @get:PropertyName("leaderId")
    @set:PropertyName("leaderId")
    var leaderId: Long = 0,
    
    @ColumnInfo(name = "created_at")
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
)
