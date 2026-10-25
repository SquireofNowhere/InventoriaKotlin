package com.inventoria.app.data.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.TaskTimePlan
import com.inventoria.app.data.model.TodoAlarmStyle
import com.inventoria.app.data.model.sessionElapsedMillis
import com.inventoria.app.data.model.sessionTimePlan
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.splash.SplashActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where a session's time plan goes off: a ring ("you've been at this for 25 minutes") or an
 * automatic stop. See [TaskPlanScheduler] for what arms it.
 *
 * Like the todo alarm, ACTION_FIRE re-reads the session before doing anything and works out what
 * is due from the table, not from what it was armed for -- a session paused, stopped or edited since
 * then is answered by silence, and a plan changed since then by the new plan. Whether to stop is
 * decided by [TaskTimePlan.stopInstant]; when it is due, the session is ended at that *planned*
 * instant rather than at the moment the alarm happened to be delivered, so a late alarm does not
 * add its own delay to the task's length.
 */
@AndroidEntryPoint
class TaskPlanReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var syncRepository: FirebaseSyncRepository

    @Inject
    lateinit var scheduler: TaskPlanScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_FIRE -> try {
                        fire(context, groupId)
                    } finally {
                        // A plan can hold more than the event that just went off; only the next is
                        // ever armed, so line up the one after -- even when this one stayed silent.
                        scheduler.rearm()
                    }
                    ACTION_STOP -> {
                        stopNow(context, groupId)
                        NotificationManagerCompat.from(context).cancel(notificationId(groupId))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handling $action for $groupId failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(context: Context, groupId: String) {
        val segments = taskRepository.getSessionSegments(groupId)
        val running = segments.firstOrNull { it.isRunning }
        if (running == null) {
            Log.d(TAG, "Plan alarm for $groupId fired but the session is not running; staying quiet")
            return
        }
        val plan = segments.sessionTimePlan()
        if (plan.isEmpty) return

        val now = System.currentTimeMillis()
        val elapsed = sessionElapsedMillis(segments, now)
        val stopAt = plan.stopInstant(elapsed, now)
        if (stopAt != null && stopAt <= now + TOLERANCE_MILLIS) {
            val endAt = stopAt.coerceIn(running.startTime, now)
            taskRepository.stopSession(groupId, endAt)
            syncRepository.pushPendingChanges()
            val atGoal = plan.stopsAtGoal && plan.goalMillis?.let { now + (it - elapsed) } == stopAt
            val ran = elapsed - (now - endAt)
            val reason = if (atGoal) "planned length reached" else "stop time reached"
            notifyInfo(context, groupId, "Stopped ${running.name}", "Ran ${TaskTimePlan.elapsedLabel(ran)} -- $reason")
            return
        }

        // Rounded to the nearest minute: the alarm lands on the mark, give or take the clock.
        val minutes = ((elapsed + 30_000L) / TaskTimePlan.MILLIS_PER_MINUTE).toInt()
        val goal = plan.goalMinutes
        val text = when {
            goal != null && minutes >= goal -> "${TaskTimePlan.minutesLabel(minutes)} -- planned ${TaskTimePlan.minutesLabel(goal)} reached"
            goal != null -> "${TaskTimePlan.minutesLabel(minutes)} of ${TaskTimePlan.minutesLabel(goal)}"
            else -> "Going for ${TaskTimePlan.minutesLabel(minutes)}"
        }
        notifyRing(context, groupId, running.name, text, withStop = true)
    }

    /** The notification's Stop button: ends the session right now, whatever the plan says. */
    private suspend fun stopNow(context: Context, groupId: String) {
        val segments = taskRepository.getSessionSegments(groupId)
        if (segments.none { it.isSessionActive }) return
        taskRepository.stopSession(groupId, System.currentTimeMillis())
        syncRepository.pushPendingChanges()
    }

    private suspend fun notifyRing(context: Context, groupId: String, title: String, text: String, withStop: Boolean) {
        if (!canNotify(context)) return
        val style = TodoAlarmStyle.fromName(settingsRepository.getTodoAlarmStyle().first())
        TodoAlarmReceiver.ensureChannels(context)
        val channelId = if (style == TodoAlarmStyle.ALARM) TodoAlarmReceiver.CHANNEL_ALARM else TodoAlarmReceiver.CHANNEL_NOTIFICATION
        val builder = baseBuilder(context, channelId, title, text)
            .setPriority(if (style == TodoAlarmStyle.ALARM) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (style == TodoAlarmStyle.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
        if (withStop) builder.addAction(0, "Stop", stopPendingIntent(context, groupId))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (style == TodoAlarmStyle.ALARM) {
                builder.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioManager.STREAM_ALARM
                ).setVibrate(longArrayOf(0, 600, 300, 600))
            } else {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
        }
        post(context, groupId, builder.build())
    }

    /** A quiet heads-up that something happened by itself; never the loud channel. */
    private suspend fun notifyInfo(context: Context, groupId: String, title: String, text: String) {
        if (!canNotify(context)) return
        TodoAlarmReceiver.ensureChannels(context)
        post(context, groupId, baseBuilder(context, TodoAlarmReceiver.CHANNEL_NOTIFICATION, title, text).build())
    }

    private suspend fun canNotify(context: Context): Boolean {
        if (!settingsRepository.getNotificationsEnabled().first()) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun baseBuilder(context: Context, channelId: String, title: String, text: String): NotificationCompat.Builder {
        // SplashActivity is the app's one exported entry point; it forwards to MainActivity.
        val openIntent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            context, title.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun post(context: Context, groupId: String, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId(groupId), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was withdrawn between the check and the post. Nothing to do.
            Log.w(TAG, "Notification refused", e)
        }
    }

    private fun stopPendingIntent(context: Context, groupId: String): PendingIntent {
        val intent = Intent(context, TaskPlanReceiver::class.java).apply {
            action = ACTION_STOP
            data = android.net.Uri.parse("inventoria://task-plan/$groupId/stop")
            putExtra(EXTRA_GROUP_ID, groupId)
        }
        return PendingIntent.getBroadcast(
            context, "task-plan-stop:$groupId".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "TaskPlanReceiver"

        const val ACTION_FIRE = "com.inventoria.app.action.TASK_PLAN_FIRE"
        const val ACTION_STOP = "com.inventoria.app.action.TASK_PLAN_STOP"
        const val EXTRA_GROUP_ID = "group_id"

        /** An alarm is delivered at or just after its time; within this of a stop counts as due. */
        private const val TOLERANCE_MILLIS = 1_500L

        /** One notification per session: a ring replaces the last, a stop replaces the ring. */
        fun notificationId(groupId: String): Int = "task-plan:$groupId".hashCode()
    }
}
