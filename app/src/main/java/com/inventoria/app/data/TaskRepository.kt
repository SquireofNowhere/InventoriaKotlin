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
import java.util.UUID
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
                old.taskTypeId != new.taskTypeId ||
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

    suspend fun getVisibleTasksList(): List<Task> = taskDao.getVisibleTasksList()

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

    /** Renames this session and nothing else.
     *
     * Until v2.12 a rename that collided with an existing name silently merged this session INTO
     * that name's group, so groupId meant "session" and "everything ever called this" by turns.
     * That collapsed every sitting of a repeated name into one card, let a rename retroactively
     * rewrite the kind and type of months of history, and made the streak counter (which counts
     * distinct groupIds) see a name you return to daily as a single session. A groupId is now one
     * session, permanently; the useful half of the old merge -- inheriting a known name's kind and
     * type -- lives on in the autofill path instead, where it applies to THIS session only. */
    suspend fun updateSessionName(groupId: String, newName: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.updateSessionName(groupId, newName, timestamp)
    }

    /** Retypes a whole session. Unlike updateSessionKind below, no score recomputation is needed:
     * the type is a grouping label only and never enters computeFrozenScore. */
    suspend fun updateSessionTaskType(groupId: String, newTaskTypeId: String?) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        var timestamp = getNextTimestamp(maxT)
        tasks.forEach { task ->
            taskDao.updateTask(task.copy(taskTypeId = newTaskTypeId, updatedAt = timestamp, isDirty = true))
            timestamp += 1
        }
    }

    /** Retypes exactly one segment, mirroring updateSegmentKind's scope -- TaskDetailDialog edits a
     * single finished segment, and retyping it shouldn't drag the rest of its session along. As
     * with updateSessionTaskType, no score recomputation: the type never enters the scoring math. */
    suspend fun updateSegmentTaskType(taskId: String, newTaskTypeId: String?) {
        val task = taskDao.getTaskById(taskId) ?: return
        val timestamp = getNextTimestamp(task.updatedAt)
        taskDao.updateTask(task.copy(taskTypeId = newTaskTypeId, updatedAt = timestamp, isDirty = true))
    }

    /** Deliberate whole-session recategorization (the "Session Category" picker in
     * SessionDetailDialog, used for both active and fully historical sessions). Recomputes
     * score per-segment rather than a single bulk SQL UPDATE, since each already-completed
     * segment's frozen score is duration-dependent on the OLD Kind -- leaving it untouched
     * would silently mismatch the new Kind's productivityValue, the same staleness
     * updateSegmentTime already guards against for time edits. A still-running segment has no
     * frozen score yet, so it's left alone (computed properly whenever it eventually stops). */
    suspend fun updateSessionKind(groupId: String, newKind: TaskKind) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        var timestamp = getNextTimestamp(maxT)
        tasks.forEach { task ->
            val newScore = if (task.isRunning) task.score else computeFrozenScore(newKind, task.duration)
            taskDao.updateTask(task.copy(kind = newKind, isKindCustom = false, score = newScore, updatedAt = timestamp, isDirty = true))
            timestamp += 1
        }
    }

    /** Retags exactly ONE segment's Kind -- unlike [updateSessionKind]'s deliberate whole-session
     * recolor, this is for e.g. ActiveSessionCard's inline dropdown, which should only affect
     * whichever segment is actually shown (the running one, or the most recent paused one if
     * nothing's currently running) rather than silently recoloring the rest of the session's
     * history too. Recomputes score the same way updateSessionKind does when the segment is
     * already frozen. */
    suspend fun updateSegmentKind(taskId: String, newKind: TaskKind) {
        val existing = taskDao.getTaskById(taskId) ?: return
        val newScore = if (existing.isRunning) existing.score else computeFrozenScore(newKind, existing.duration)
        updateTask(existing.copy(kind = newKind, isKindCustom = true, score = newScore))
    }

    suspend fun endSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupId(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        val timestamp = getNextTimestamp(maxT)
        taskDao.endSession(groupId, timestamp)
    }

    /** The Kind is read from the stored row rather than taken from the caller: callers hold a
     * RunningTaskUI snapshot, and a Kind edited from the card between the last flow emission and
     * the tap on Stop would otherwise freeze a score computed against the Kind it *used* to have,
     * leaving the row's own kind and score disagreeing forever. [fallbackKind] only covers a row
     * that has since vanished. */
    suspend fun stopTaskAndSession(taskId: String, groupId: String, endTime: Long, duration: Long, fallbackKind: TaskKind) {
        val kind = taskDao.getTaskById(taskId)?.kind ?: fallbackKind
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

    /** Undo of [softDeleteTask]. The timestamp must beat the tombstone's own, or the delete still
     * wins the last-write-wins merge on the next pull. */
    suspend fun restoreTask(id: String) {
        val existing = taskDao.getTaskById(id)
        taskDao.restoreTaskById(id, getNextTimestamp(existing?.updatedAt ?: 0L))
    }

    /** Undo of [softDeleteSession]. */
    suspend fun restoreSession(groupId: String) {
        val tasks = taskDao.getTasksByGroupIdIncludingDeleted(groupId)
        val maxT = tasks.maxOfOrNull { it.updatedAt } ?: 0L
        taskDao.restoreTasksByGroupId(groupId, getNextTimestamp(maxT))
    }

    suspend fun purgeOldDeletedTasks(threshold: Long) {
        taskDao.purgeOldDeletedTasks(threshold)
    }

    // ---- Session operations ------------------------------------------------------------------
    //
    // A "session" is every Task row sharing a groupId. These read the table rather than any
    // in-memory snapshot so they work the same from the tracker screen and from a home-screen
    // widget button pressed while the app is not running.

    /** Sessions still active (paused or running), keyed by groupId. */
    private suspend fun activeSessions(): Map<String, List<Task>> =
        taskDao.getVisibleTasksList()
            .groupBy { it.groupId }
            .filter { (_, segments) -> segments.any { it.isSessionActive } }

    /** Hard cap on concurrent sessions -- the tracker screen refuses a sixth, and so does this. */
    suspend fun activeSessionCount(): Int = activeSessions().size

    /**
     * Stops whatever is interrupting [groupId] -- and, recursively, whatever is interrupting THAT
     * -- deepest first, regardless of how many levels are currently paused partway down the chain.
     * Used whenever a session is being stopped or resumed outright (rather than simply un-pausing
     * straight back into an active interruption), so a chain of interruptions doesn't get left
     * dangling with nothing left to eventually return to.
     */
    suspend fun stopInterruptionChain(groupId: String, now: Long) {
        val interrupting = activeSessions().entries
            .firstOrNull { (_, segments) -> segments.any { it.interruptedGroupId == groupId } }
            ?: return
        stopInterruptionChain(interrupting.key, now)
        val running = interrupting.value.firstOrNull { it.isRunning }
        if (running != null) {
            stopTaskAndSession(running.id, interrupting.key, now, now - running.startTime, running.kind)
        } else {
            endSession(interrupting.key)
        }
    }

    /**
     * Un-pauses [groupId] by opening a fresh running segment on it, first collapsing any
     * interruption chain stacked on top (resuming "back to this" means none of those have
     * anything left to return to). No-op if the session is already running or is gone.
     *
     * interruptedGroupId/countsForStreak/originTodoId/taskTypeId carry over from the session's
     * most recent segment, or a resume would silently sever the parent-child link, streak opt-in,
     * todo origin or type -- and since this always reads the MOST RECENT segment, one un-propagated
     * resume would sever them for every resume after it too.
     */
    suspend fun resumeSession(groupId: String, now: Long): Task? {
        val segments = taskDao.getTasksByGroupId(groupId)
        if (segments.none { it.isSessionActive } || segments.any { it.isRunning }) return null
        val latest = segments.maxByOrNull { it.startTime } ?: return null
        stopInterruptionChain(groupId, now)
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = latest.name,
            kind = latest.kind,
            taskTypeId = latest.taskTypeId,
            isRunning = true,
            startTime = now,
            interruptedGroupId = latest.interruptedGroupId,
            countsForStreak = latest.countsForStreak,
            originTodoId = latest.originTodoId
        )
        insertTask(newTask)
        return newTask
    }

    /** Pauses [groupId]'s running segment, if it has one. Returns the segment that was paused. */
    suspend fun pauseSession(groupId: String, now: Long): Task? {
        val running = taskDao.getTasksByGroupId(groupId).firstOrNull { it.isRunning } ?: return null
        pauseSegment(running, now)
        return running
    }

    /**
     * Ends [groupId] for good: collapses its interruption chain, freezes its running segment (or
     * just closes the session if it was paused), and -- when this session was itself an
     * interruption -- resumes the session it interrupted, because stopping an interruption means
     * "back to what I was doing". Returns the todo this session was started from, if any, so a
     * caller with a UI can offer the completion check-in.
     */
    suspend fun stopSession(groupId: String, now: Long): String? {
        val segments = taskDao.getTasksByGroupId(groupId)
        if (segments.none { it.isSessionActive }) return null
        val running = segments.firstOrNull { it.isRunning }
        // Read the links off whichever segment exists: a session can be paused right now because
        // it has an interruption on top, in which case there is no running segment to ask.
        val reference = running ?: segments.maxByOrNull { it.startTime }
        val interruptedGroupId = reference?.interruptedGroupId
        val originTodoId = reference?.originTodoId

        stopInterruptionChain(groupId, now)
        if (running != null) {
            stopTaskAndSession(running.id, groupId, now, now - running.startTime, running.kind)
        } else {
            endSession(groupId)
        }

        if (interruptedGroupId != null) resumeSession(interruptedGroupId, now)
        return originTodoId
    }

    /**
     * Opens a brand-new running session called [name], or returns null when the five-session cap
     * is already reached. Callers still own starting TaskTimerService afterwards.
     */
    suspend fun startNewSession(name: String, now: Long = System.currentTimeMillis()): Task? {
        if (activeSessionCount() >= MAX_ACTIVE_SESSIONS) return null
        val task = Task(
            id = UUID.randomUUID().toString(),
            groupId = UUID.randomUUID().toString(),
            name = name,
            isRunning = true,
            startTime = now
        )
        insertTask(task)
        return task
    }

    companion object {
        /** The tracker screen refuses a sixth concurrent session; the widget honours the same cap. */
        const val MAX_ACTIVE_SESSIONS = 5
    }
}
