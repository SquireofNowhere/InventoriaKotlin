package com.inventoria.app.data

import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastTimestamp = AtomicLong(0L)

    init {
        repositoryScope.launch {
            val tasks = taskDao.getAllTasks().first()
            val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            lastTimestamp.set(maxT)
        }
    }

    private fun getNextTimestamp(minTimestamp: Long = 0L): Long {
        val now = System.currentTimeMillis()
        val base = maxOf(now, minTimestamp)
        while (true) {
            val last = lastTimestamp.get()
            val next = if (base > last) base else last + 1
            if (lastTimestamp.compareAndSet(last, next)) {
                return next
            }
        }
    }

    private fun hasMeaningfulChanges(old: Task, new: Task): Boolean {
        return old.name != new.name ||
                old.groupId != new.groupId ||
                old.kind != new.kind ||
                old.startTime != new.startTime ||
                old.endTime != new.endTime ||
                old.duration != new.duration ||
                old.isRunning != new.isRunning ||
                old.isPaused != new.isPaused ||
                old.isSessionActive != new.isSessionActive ||
                old.savedToCalendar != new.savedToCalendar ||
                old.savedToCalendarAt != new.savedToCalendarAt ||
                old.isNameCustom != new.isNameCustom ||
                old.isKindCustom != new.isKindCustom ||
                old.isDeleted != new.isDeleted ||
                old.countsForStreak != new.countsForStreak ||
                old.score != new.score
    }

    fun getVisibleTasks(): Flow<List<Task>> = taskDao.getVisibleTasks()
    fun getAllTasksForSync(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        val timestamp = getNextTimestamp(task.updatedAt)
        taskDao.insertTask(task.copy(updatedAt = timestamp, isDirty = true))
    }

    suspend fun insertTasks(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val maxInBatch = tasks.maxOf { it.updatedAt }
        val baseTime = getNextTimestamp(maxInBatch)
        val tasksWithTimestamps = tasks.mapIndexed { index, task ->
            task.copy(updatedAt = baseTime + index, isDirty = true)
        }
        taskDao.insertTasks(tasksWithTimestamps)
    }

    suspend fun updateTask(task: Task) {
        val existing = taskDao.getTaskById(task.id)
        if (existing == null || hasMeaningfulChanges(existing, task)) {
            val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
            taskDao.updateTask(task.copy(updatedAt = timestamp, isDirty = true))
        }
    }

    /** Manually editing a completed segment's time changes its duration, so its frozen score
     * (which is duration-dependent) would otherwise go stale -- recompute it the same way it
     * was originally frozen. Uses the current streak state, since there's no way to know exactly
     * what the streak looked like at the original completion time. */
    suspend fun updateSegmentTime(task: Task, start: Long, end: Long) {
        val duration = end - start
        val score = computeFrozenScore(task.kind, duration)
        updateTask(task.copy(startTime = start, endTime = end, duration = duration, score = score))
    }

    suspend fun updateSessionName(groupId: String, newName: String) {
        val existingGroupId = taskDao.getGroupIdByName(newName)
        if (existingGroupId != null && existingGroupId != groupId) {
            val timestamp = getNextTimestamp()
            taskDao.joinGroupAtomically(groupId, newName, existingGroupId, timestamp)
        } else {
            val tasks = taskDao.getTasksByGroupId(groupId)
            val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            val timestamp = getNextTimestamp(maxT)
            taskDao.updateSessionName(groupId, newName, timestamp)
        }
    }

    suspend fun updateSessionNameAndGroupId(oldGroupId: String, newName: String, newGroupId: String) {
        val timestamp = getNextTimestamp()
        taskDao.joinGroupAtomically(oldGroupId, newName, newGroupId, timestamp)
    }

    suspend fun updateSessionKind(groupId: String, newKind: TaskKind) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.updateSessionKindAndResetCustom(groupId, newKind, timestamp)
    }

    suspend fun endSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.endSession(groupId, timestamp)
    }

    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long, kind: TaskKind) {
        val score = computeFrozenScore(kind, duration)
        val timestamp = getNextTimestamp()
        taskDao.stopTaskAndSession(taskId, groupId, endTime, duration, score, timestamp)
    }

    /** Pausing produces a completed-but-not-final segment; it gets its own frozen score
     * immediately (so it counts toward metrics right away) rather than waiting for the whole
     * session to eventually stop. */
    suspend fun pauseSegment(task: Task, endTime: Long) {
        val duration = endTime - task.startTime
        val score = computeFrozenScore(task.kind, duration)
        updateTask(task.copy(isRunning = false, isPaused = true, endTime = endTime, duration = duration, score = score))
    }

    /** Same formula as computeFrozenScore, exposed publicly for a live/preview display -- a
     * running task's "so far" estimate, ticking as duration grows -- without implying anything is
     * actually being frozen/stored. Uses the CURRENT streak, so it can differ slightly from
     * whatever the streak happens to be at the moment the task is actually stopped. */
    suspend fun previewScore(kind: TaskKind, durationMs: Long): Int = computeFrozenScore(kind, durationMs)

    private suspend fun computeFrozenScore(kind: TaskKind, durationMs: Long): Int {
        val streak = getStreakCountForKind(kind)
        val multiplier = momentumMultiplier(streak, kind)
        val minutes = durationMs / 60000.0
        return (kind.productivityValue * minutes * multiplier).roundToInt()
    }

    /** Counts consecutive same-kind sessions immediately preceding "now" among fully-stopped
     * sessions, most recent first. Interruptions are excluded unless explicitly opted in via
     * Task.countsForStreak -- an involuntary break shouldn't cost an existing streak. */
    private suspend fun getStreakCountForKind(kind: TaskKind): Int {
        val recentSessionKinds = taskDao.getRecentCompletedTasks(limit = 100)
            .filter { it.interruptedGroupId == null || it.countsForStreak }
            .distinctBy { it.groupId }
            .take(20)
            .map { it.kind }
        var streak = 0
        for (k in recentSessionKinds) {
            if (k == kind) streak++ else break
        }
        return streak
    }

    private fun momentumMultiplier(streakCount: Int, kind: TaskKind): Double {
        val rate = if (kind.productivityValue < 0) 0.15 else 0.10
        val cap = 2.5
        return minOf(cap, (1 + rate).pow(streakCount.toDouble()))
    }

    suspend fun softDeleteTask(id: String) {
        val existing = taskDao.getTaskById(id)
        val timestamp = getNextTimestamp(existing?.updatedAt ?: 0L)
        taskDao.softDeleteTaskById(id, timestamp)
    }

    suspend fun softDeleteSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.softDeleteTasksByGroupId(groupId, timestamp)
    }

    suspend fun purgeOldDeletedTasks(threshold: Long) {
        taskDao.purgeOldDeletedTasks(threshold)
    }
}
