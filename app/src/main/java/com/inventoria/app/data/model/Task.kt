package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

@Entity
data class Task(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("groupId") @set:PropertyName("groupId") var groupId: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    @get:PropertyName("startTime") @set:PropertyName("startTime") var startTime: Long = 0L,
    @get:PropertyName("endTime") @set:PropertyName("endTime") var endTime: Long? = null,
    @get:PropertyName("duration") @set:PropertyName("duration") var duration: Long = 0L,
    @get:PropertyName("isRunning") @set:PropertyName("isRunning") var isRunning: Boolean = false,
    @get:PropertyName("isPaused") @set:PropertyName("isPaused") var isPaused: Boolean = false,
    @get:PropertyName("isSessionActive") @set:PropertyName("isSessionActive") var isSessionActive: Boolean = true,
    @get:PropertyName("savedToCalendar") @set:PropertyName("savedToCalendar") var savedToCalendar: Boolean = false,
    @get:PropertyName("savedToCalendarAt") @set:PropertyName("savedToCalendarAt") var savedToCalendarAt: Long? = null,
    @get:PropertyName("isNameCustom") @set:PropertyName("isNameCustom") var isNameCustom: Boolean = false,
    @get:PropertyName("isKindCustom") @set:PropertyName("isKindCustom") var isKindCustom: Boolean = false,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
) {
    val score: Int
        get() = kind.productivityValue
}

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
    GRAPHITE("⚫ Graphite • Waiting", 4286611584L, 0, TaskCategory.NEUTRAL, "Idle time, unavoidable waiting, or transitions."),
    GRAPE("🍇 Grape • Earned Leisure", 4288360703L, 0, TaskCategory.NEUTRAL, "Active relaxation and earned free time."),
    TOMATO("🍅 Tomato • Major Drain", 4294927175L, -2, TaskCategory.PERSONAL, "Major personal time waste or unhealthy habits."),
    TANGERINE("🍊 Tangerine • Minor Slip", 4294944000L, -1, TaskCategory.PERSONAL, "Slight distractions or minor procrastination."),
    BLUEBERRY("🫐 Blueberry • Self-Care", 4282477025L, 1, TaskCategory.PERSONAL, "Basic maintenance, health, and personal upkeep."),
    LAVENDER("💜 Lavender • Growth", 4288052150L, 2, TaskCategory.PERSONAL, "Skill building, learning, and meaningful hobbies."),
    PEACOCK("🦚 Peacock • Peak Performance", 4278243025L, 3, TaskCategory.PERSONAL, "Flow state, high-impact work, and major goals."),
    BANANA("🍌 Banana • Social Drain", 4294959413L, -2, TaskCategory.SOCIAL, "Draining social interactions or conflict."),
    FLAMINGO("🦩 Flamingo • Social Friction", 4294741676L, -1, TaskCategory.SOCIAL, "Minor social awkwardness or light friction."),
    BASIL("🌿 Basil • Contribution", 4286611456L, 1, TaskCategory.SOCIAL, "Small helpful acts and household contributions."),
    SAGE("🌱 Sage • Support", 4290558090L, 2, TaskCategory.SOCIAL, "Deep support for family, friends, and community.");
}
