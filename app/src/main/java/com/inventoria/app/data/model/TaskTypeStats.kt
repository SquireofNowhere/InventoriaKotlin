package com.inventoria.app.data.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Derived, display-only aggregate for one TaskType. Nothing here feeds scoring -- per-task scores
 * are still frozen individually by TaskRepository.computeFrozenScore.
 *
 * [averagePoints] is the plain mean of member tasks' `kind.productivityValue`, unweighted by
 * duration: three home-made meals (+1) and one takeout (-1) average to +0.5. Null when the type
 * has no tasks yet -- rendered as an em-dash, never as 0.0, so "no data" can't be misread as
 * "neutral".
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
            val rounded = (avg * 10).roundToInt() / 10.0
            val sign = when {
                rounded > 0 -> "+"
                rounded < 0 -> "-"
                else -> ""
            }
            // Locale.US explicitly: this is a compact numeric badge, and a locale-driven decimal
            // comma next to a +/- sign reads as a malformed number rather than a translation.
            return sign + String.format(Locale.US, "%.1f", abs(rounded))
        }
}

/**
 * The type a *name* has settled on: the most common taskTypeId across every task carrying that
 * name. Pass one name's whole history.
 *
 * Untyped tasks vote too, deliberately. A name with a long pre-Task-Types history therefore keeps
 * resolving to no type until enough newly-typed instances outnumber the untyped ones -- the type
 * has to be *earned* rather than flipped by a single tap. The flip side is that a brand new name
 * gets its type immediately: one task, one vote, done.
 *
 * Ties go to a real type over "untyped", then to whichever was used most recently, so the moment a
 * name draws level it starts suggesting something rather than sitting on null forever.
 *
 * Lives here rather than next to the autofill that first used it because it is domain logic, not a
 * UI concern -- the Todos screen reads it too, to seed a type for a task started from a todo.
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

/**
 * Builds stats for every type in a SINGLE pass over [tasks].
 *
 * Deliberately one shared computation rather than a stat-per-flow: TaskTrackerViewModel already
 * carries seven separate flows that each re-scan the full finished-task list (see the README's
 * Known Optimization Opportunities), and this would otherwise add several more.
 *
 * `kind.productivityValue` is a Kotlin enum property rather than a column, so this cannot be a SQL
 * AVG -- it has to happen here. Pass only non-deleted tasks.
 */
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
