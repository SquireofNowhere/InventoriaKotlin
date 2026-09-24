package com.inventoria.shared.model

import kotlinx.serialization.Serializable

/** users/$uid/tasks, keyed by [id]. See the Android Task entity for what each field means. */
@Serializable
data class Task(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val taskTypeId: String? = null,
    val kind: TaskKind = TaskKind.GRAPHITE,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val duration: Long = 0L,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isSessionActive: Boolean = true,
    val savedToCalendar: Boolean = false,
    val savedToCalendarAt: Long? = null,
    val isNameCustom: Boolean = false,
    val isKindCustom: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L,
    val interruptedGroupId: String? = null,
    val countsForStreak: Boolean = false,
    val originTodoId: String? = null,
    val score: Int = 0
) {
    /** Time spent so far: live from [startTime] while running, the stored [duration] once stopped
     * -- the same rule the Android task screen displays with. */
    fun elapsedMillis(now: Long = nowMillis()): Long =
        if (isRunning) (now - startTime).coerceAtLeast(0L) else duration
}

@Serializable
enum class TaskCategory {
    NEUTRAL, PERSONAL, SOCIAL
}

@Serializable
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
