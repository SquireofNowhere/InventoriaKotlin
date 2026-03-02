package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.PropertyName

enum class TaskCategory {
    NEUTRAL, PERSONAL, SOCIAL
}

enum class TaskKind(
    val displayName: String,
    val colorValue: Long,
    val productivityValue: Int,
    val category: TaskCategory,
    val description: String
) {
    GRAPHITE("⚫ Graphite • Waiting", 0xFF808080, 0, TaskCategory.NEUTRAL, "Idle time, unavoidable waiting, or transitions."),
    GRAPE("🍇 Grape • Earned Leisure", 0xFF9B30FF, 0, TaskCategory.NEUTRAL, "Active relaxation and earned free time."),
    TOMATO("🍅 Tomato • Major Drain", 0xFFFF6347, -2, TaskCategory.PERSONAL, "Major personal time waste or unhealthy habits."),
    TANGERINE("🍊 Tangerine • Minor Slip", 0xFFFFA500, -1, TaskCategory.PERSONAL, "Slight distractions or minor procrastination."),
    BLUEBERRY("🫐 Blueberry • Self-Care", 0xFF4169E1, 1, TaskCategory.PERSONAL, "Basic maintenance, health, and personal upkeep."),
    LAVENDER("💜 Lavender • Growth", 0xFF967BB6, 2, TaskCategory.PERSONAL, "Skill building, learning, and meaningful hobbies."),
    PEACOCK("🦚 Peacock • Peak Performance", 0xFF00CED1, 3, TaskCategory.PERSONAL, "Flow state, high-impact work, and major goals."),
    BANANA("🍌 Banana • Social Drain", 0xFFFFE135, -2, TaskCategory.SOCIAL, "Draining social interactions or conflict."),
    FLAMINGO("🦩 Flamingo • Social Friction", 0xFFFC8EAC, -1, TaskCategory.SOCIAL, "Minor social awkwardness or light friction."),
    BASIL("🌿 Basil • Contribution", 0xFF808000, 1, TaskCategory.SOCIAL, "Small helpful acts and household contributions."),
    SAGE("🌱 Sage • Support", 0xFFBCB88A, 2, TaskCategory.SOCIAL, "Deep support for family, friends, and community.")
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: String = "",
    
    @get:PropertyName("groupId")
    @set:PropertyName("groupId")
    var groupId: String = "",
    
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("kind")
    @set:PropertyName("kind")
    var kind: TaskKind = TaskKind.GRAPHITE,
    
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

    @get:PropertyName("isPaused")
    @set:PropertyName("isPaused")
    var isPaused: Boolean = false,
    
    @get:PropertyName("isSessionActive")
    @set:PropertyName("isSessionActive")
    var isSessionActive: Boolean = true,
    
    @get:PropertyName("savedToCalendar")
    @set:PropertyName("savedToCalendar")
    var savedToCalendar: Boolean = false,

    @get:PropertyName("savedToCalendarAt")
    @set:PropertyName("savedToCalendarAt")
    var savedToCalendarAt: Long? = null,

    @get:PropertyName("isNameCustom")
    @set:PropertyName("isNameCustom")
    var isNameCustom: Boolean = false,

    @get:PropertyName("isKindCustom")
    @set:PropertyName("isKindCustom")
    var isKindCustom: Boolean = false,
    
    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
) {
    val score: Int get() = kind.productivityValue
}
