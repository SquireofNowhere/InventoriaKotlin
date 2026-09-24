package com.inventoria.shared.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * users/$uid/task_types: the activity tier between a free-text Task.name and a TaskKind. Tasks
 * reference types by id, so a rename reaches all history.
 */
@Serializable
data class TaskType(
    val id: String = "",
    val name: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
)

/** Seeded once per account with deterministic ids, so two devices seeding at once converge. */
val DEFAULT_TASK_TYPE_NAMES = listOf(
    "Eating", "Sleep", "Work", "Study", "Exercise", "Commute", "Chores", "Errands", "Social",
    "Family", "Screen Time", "Self-Care", "Admin", "Hobby", "Health", "Interruption", "Other"
)

/** "Screen Time" -> "type_screen_time". Stable across devices and app versions. */
fun defaultTaskTypeId(name: String): String =
    "type_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/**
 * Display-only aggregate for one TaskType. [averagePoints] is the unweighted mean of member tasks'
 * `kind.productivityValue`, null when the type has no tasks yet.
 */
data class TaskTypeStats(
    val typeId: String,
    val name: String,
    val taskCount: Int,
    val averagePoints: Double?,
    val mostUsedKind: TaskKind?,
    val totalDurationMs: Long
) {
    /** "+0.5" / "-1.3" / "0.0", or "--" when there is nothing to average. */
    val averageLabel: String
        get() {
            val avg = averagePoints ?: return "--"
            val tenths = (avg * 10).roundToInt()
            val sign = when {
                tenths > 0 -> "+"
                tenths < 0 -> "-"
                else -> ""
            }
            return "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
        }
}

/**
 * The type a name has settled on: the most common taskTypeId across that name's history. Untyped
 * tasks vote too, so a type has to be earned; ties go to a real type, then to the most recent.
 */
fun modalTypeIdFor(tasksWithSameName: List<Task>): String? =
    tasksWithSameName
        .groupBy { it.taskTypeId }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String?, List<Task>>> { it.value.size }
                .thenByDescending { it.key != null }
                .thenByDescending { entry -> entry.value.maxOf { it.startTime } }
        )
        .firstOrNull()
        ?.key

/** The Kind a name usually carries: the mode, ties going to the most recently used. */
fun modalKindFor(tasksWithSameName: List<Task>): TaskKind? =
    tasksWithSameName
        .groupBy { it.kind }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<TaskKind, List<Task>>> { it.value.size }
                .thenByDescending { entry -> entry.value.maxOf { it.startTime } }
        )
        .firstOrNull()
        ?.key

/** Stats for every type in a single pass over [tasks]. Pass only non-deleted tasks. */
fun computeTaskTypeStats(
    types: List<TaskType>,
    tasks: List<Task>
): Map<String, TaskTypeStats> {
    val byType = tasks.groupBy { it.taskTypeId }
    return types.associate { type ->
        val members = byType[type.id].orEmpty()
        val mostUsedKind = members
            .groupingBy { it.kind }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        type.id to TaskTypeStats(
            typeId = type.id,
            name = type.name,
            taskCount = members.size,
            averagePoints = members
                .takeIf { it.isNotEmpty() }
                ?.map { it.kind.productivityValue }
                ?.average(),
            mostUsedKind = mostUsedKind,
            totalDurationMs = members.sumOf { it.duration }
        )
    }
}
