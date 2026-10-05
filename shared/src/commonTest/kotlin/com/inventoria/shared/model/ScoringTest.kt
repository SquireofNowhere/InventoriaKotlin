package com.inventoria.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringTest {
    private fun finished(group: String, kind: TaskKind, endedAt: Long, interrupts: String? = null, counts: Boolean = false) =
        Task(
            id = "t-$group", groupId = group, kind = kind, endTime = endedAt, startTime = endedAt - 60_000,
            isSessionActive = false, interruptedGroupId = interrupts, countsForStreak = counts
        )

    @Test
    fun scoreIsValueTimesMinutesTimesMomentum() {
        // One hour of a +3 Kind with no streak is 180 minute-points.
        assertEquals(180, Scoring.frozenScore(TaskKind.PEACOCK, 3_600_000, 0))
        // A -2 Kind is penalised, and its multiplier compounds at 15% rather than 10%.
        assertEquals(-60, Scoring.frozenScore(TaskKind.TOMATO, 1_800_000, 0))
        assertEquals(-69, Scoring.frozenScore(TaskKind.TOMATO, 1_800_000, 1))
        // Neutral Kinds never score.
        assertEquals(0, Scoring.frozenScore(TaskKind.GRAPHITE, 3_600_000, 5))
    }

    @Test
    fun momentumIsCapped() {
        assertEquals(2.5, Scoring.momentumMultiplier(50, TaskKind.PEACOCK))
        assertEquals(1.0, Scoring.momentumMultiplier(0, TaskKind.PEACOCK))
    }

    @Test
    fun streakCountsConsecutiveSessionsOfTheSameKind() {
        val tasks = listOf(
            finished("a", TaskKind.PEACOCK, 1_000),
            finished("b", TaskKind.PEACOCK, 2_000),
            finished("c", TaskKind.LAVENDER, 3_000),
            finished("d", TaskKind.PEACOCK, 4_000)
        )
        assertEquals(1, Scoring.streakCount(tasks, TaskKind.PEACOCK))
        assertEquals(0, Scoring.streakCount(tasks, TaskKind.LAVENDER))
        // Looking back from before the last two sessions, the two Peacocks are the newest run.
        assertEquals(2, Scoring.streakCount(tasks, TaskKind.PEACOCK, endedBy = 2_500))
    }

    @Test
    fun segmentsOfOneSessionCountOnce() {
        val tasks = listOf(
            finished("a", TaskKind.PEACOCK, 1_000).copy(id = "a1"),
            finished("a", TaskKind.PEACOCK, 2_000).copy(id = "a2")
        )
        assertEquals(1, Scoring.streakCount(tasks, TaskKind.PEACOCK))
    }

    @Test
    fun interruptionsAreSkippedUnlessTheyOptedIn() {
        val tasks = listOf(
            finished("a", TaskKind.PEACOCK, 1_000),
            finished("water", TaskKind.GRAPHITE, 2_000, interrupts = "a"),
            finished("b", TaskKind.PEACOCK, 3_000)
        )
        assertEquals(2, Scoring.streakCount(tasks, TaskKind.PEACOCK))
        val optedIn = tasks.map { if (it.groupId == "water") it.copy(countsForStreak = true) else it }
        assertEquals(1, Scoring.streakCount(optedIn, TaskKind.PEACOCK))
    }

    @Test
    fun activeAndDeletedSessionsDoNotCount() {
        val tasks = listOf(
            finished("a", TaskKind.PEACOCK, 1_000),
            finished("b", TaskKind.PEACOCK, 2_000).copy(isSessionActive = true),
            finished("c", TaskKind.PEACOCK, 3_000).copy(isDeleted = true)
        )
        assertEquals(1, Scoring.streakCount(tasks, TaskKind.PEACOCK))
    }
}
