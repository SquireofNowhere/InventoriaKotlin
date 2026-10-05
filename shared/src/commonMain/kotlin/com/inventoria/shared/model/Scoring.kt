package com.inventoria.shared.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The phone's task scoring (TaskRepository.computeFrozenScore and friends), as pure functions: a
 * segment's score is its Kind's productivity value times its minutes times a momentum multiplier
 * that grows with the run of same-Kind sessions just before it. Kept identical to the app's rule so
 * a segment written from here freezes the same score the phone would have given it.
 */
object Scoring {
    private const val MOMENTUM_CAP = 2.5

    /** Sessions looked at when counting a streak, newest first -- the app's own lookback. */
    private const val STREAK_LOOKBACK = 20

    /** Rows read before collapsing to sessions, as the app's `getRecentCompletedTasks(limit = 100)`. */
    private const val RECENT_ROWS = 100

    fun momentumMultiplier(streak: Int, kind: TaskKind): Double {
        val rate = if (kind.productivityValue < 0) 0.15 else 0.10
        return minOf(MOMENTUM_CAP, (1 + rate).pow(streak.toDouble()))
    }

    fun frozenScore(kind: TaskKind, durationMs: Long, streak: Int): Int =
        (kind.productivityValue * (durationMs / 60000.0) * momentumMultiplier(streak, kind)).roundToInt()

    /**
     * How many fully-stopped sessions in a row, most recent first, were of [kind]. Sessions still
     * active do not count, and neither do interruptions unless they opted in (countsForStreak).
     * [endedBy] limits the lookback to sessions that ended at or before that instant, for scoring a
     * segment that is being written after the fact.
     */
    fun streakCount(tasks: List<Task>, kind: TaskKind, endedBy: Long = Long.MAX_VALUE): Int {
        val kinds = tasks
            .filter { !it.isDeleted && !it.isSessionActive && (endedBy == Long.MAX_VALUE || (it.endTime ?: Long.MAX_VALUE) <= endedBy) }
            .sortedByDescending { it.endTime ?: Long.MIN_VALUE }
            .take(RECENT_ROWS)
            .filter { it.interruptedGroupId == null || it.countsForStreak }
            .distinctBy { it.groupId }
            .take(STREAK_LOOKBACK)
            .map { it.kind }
        var streak = 0
        for (k in kinds) {
            if (k == kind) streak++ else break
        }
        return streak
    }
}
