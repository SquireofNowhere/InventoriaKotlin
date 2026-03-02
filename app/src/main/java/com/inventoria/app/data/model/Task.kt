package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.PropertyName

enum class TaskKind(val displayName: String, val colorValue: Long) {
    FREE_TIME("Free Time", 0xFFFFFFFF),
    BIG_WASTE("Big Waste", 0xFFDC143C),
    SMALL_WASTE("Small Waste", 0xFFFF0000),
    NEUTRAL_WAITING("Neutral/Waiting", 0xFF808080),
    SMALL_PRODUCTIVE("Small Productive", 0xFF006400),
    BIG_PRODUCTIVE("Big Productive", 0xFF00FF00)
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: String = "",
    
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("kind")
    @set:PropertyName("kind")
    var kind: TaskKind = TaskKind.NEUTRAL_WAITING,
    
    @get:PropertyName("startTime")
    @set:PropertyName("startTime")
    var startTime: Long = 0,
    
    @get:PropertyName("endTime")
    @set:PropertyName("endTime")
    var endTime: Long? = null,
    
    @get:PropertyName("duration")
    @set:PropertyName("duration")
    var duration: Long = 0,
    
    @get:PropertyName("isRunning")
    @set:PropertyName("isRunning")
    var isRunning: Boolean = false,
    
    @get:PropertyName("savedToCalendar")
    @set:PropertyName("savedToCalendar")
    var savedToCalendar: Boolean = false,
    
    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
)
