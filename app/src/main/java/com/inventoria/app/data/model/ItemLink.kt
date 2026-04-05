package com.inventoria.app.data.model

import androidx.room.Entity
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

@Entity(primaryKeys = ["followerId", "leaderId"])
data class ItemLink(
    @get:PropertyName("followerId") @set:PropertyName("followerId") var followerId: Long = 0L,
    @get:PropertyName("leaderId") @set:PropertyName("leaderId") var leaderId: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
)
