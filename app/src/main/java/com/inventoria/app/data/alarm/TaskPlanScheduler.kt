package com.inventoria.app.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.sessionElapsedMillis
import com.inventoria.app.data.model.sessionTimePlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps AlarmManager in step with the running sessions' time plans, the same way [TodoAlarmScheduler]
 * does for todos: watch the table, work out the alarms that *should* exist, reconcile to that.
 *
 * One alarm per running session that has a plan, set for the plan's next event -- the next ring or
 * the stop, whichever comes first (TaskTimePlan.nextEventAt). Only that one is ever armed; when it
 * fires, [TaskPlanReceiver] calls [rearm] to line up the one after. A paused or stopped session has
 * no running segment, so it simply stops being desired and its alarm is cancelled -- and resuming it
 * is another emission of the table, which arms it again, counted from the time actually run.
 *
 * Because the trigger is worked out from when the running segment started (not from "now"), the same
 * session gives the same trigger on every emission, so the [armed] map can skip alarms that are
 * already right. It is an optimisation only: after a process restart it is empty and everything is
 * set again, which is also what picks up a stop that came due while the phone was off.
 */
@Singleton
class TaskPlanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Volatile
    private var started = false

    /** groupId -> trigger time currently handed to AlarmManager by this process. */
    private val armed = mutableMapOf<String, Long>()

    @Volatile
    private var latest: List<Task> = emptyList()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            taskRepository.getVisibleTasks().collect { tasks ->
                latest = tasks
                reconcile(tasks)
            }
        }
    }

    /** Arms the next event of every running session again, from the table as last seen. */
    fun rearm() = reconcile(latest)

    @Synchronized
    private fun reconcile(tasks: List<Task>) {
        val now = System.currentTimeMillis()
        // The table is the whole history; only sessions with a running segment can have an alarm, so
        // narrow to those before grouping.
        val runningGroups = tasks.filter { it.isRunning }.map { it.groupId }.toSet()
        val desired = if (runningGroups.isEmpty()) emptyMap() else {
            tasks.filter { it.groupId in runningGroups }
                .groupBy { it.groupId }
                .mapNotNull { (groupId, segments) -> nextTrigger(segments, now)?.let { groupId to it } }
                .toMap()
        }

        (armed.keys - desired.keys).toList().forEach { groupId ->
            cancel(groupId)
            armed.remove(groupId)
        }
        desired.forEach { (groupId, at) ->
            if (armed[groupId] != at) {
                set(groupId, at)
                armed[groupId] = at
            }
        }
    }

    /** When this session's plan next does something, or null when it is not running or has no plan. */
    private fun nextTrigger(segments: List<Task>, now: Long): Long? {
        if (segments.none { it.isRunning }) return null
        val plan = segments.sessionTimePlan()
        if (plan.isEmpty) return null
        val at = plan.nextEventAt(sessionElapsedMillis(segments, now), now) ?: return null
        // A stop that is already overdue still has to go through the receiver, a moment from now.
        return if (at <= now) now + OVERDUE_DELAY_MILLIS else at
    }

    private fun set(groupId: String, triggerAt: Long) {
        TodoAlarmScheduler.setExactOrBestEffort(alarmManager, triggerAt, firePendingIntent(context, groupId))
        Log.d(TAG, "Armed task plan for $groupId at $triggerAt")
    }

    private fun cancel(groupId: String) {
        alarmManager.cancel(firePendingIntent(context, groupId))
        Log.d(TAG, "Cancelled task plan for $groupId")
    }

    companion object {
        private const val TAG = "TaskPlanScheduler"

        private const val OVERDUE_DELAY_MILLIS = 1_000L

        fun firePendingIntent(context: Context, groupId: String): PendingIntent {
            val intent = Intent(context, TaskPlanReceiver::class.java).apply {
                action = TaskPlanReceiver.ACTION_FIRE
                // A distinct data URI per session, so two sessions never share a PendingIntent even
                // when their hash codes collide; the request code alone is not part of Intent identity.
                data = android.net.Uri.parse("inventoria://task-plan/$groupId")
                putExtra(TaskPlanReceiver.EXTRA_GROUP_ID, groupId)
            }
            return PendingIntent.getBroadcast(
                context,
                "task-plan:$groupId".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
