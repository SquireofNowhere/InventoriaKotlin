package com.inventoria.app.data.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoAlarmStyle
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.splash.SplashActivity
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.getDayLabel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where a todo alarm actually goes off, and where its notification's buttons land.
 *
 * ACTION_FIRE re-reads the todo before doing anything: an alarm can arrive for a todo that was
 * completed or deleted after it was armed -- from another device, or by a previous process that
 * died before the scheduler saw the change -- and the right response to that is silence.
 *
 * Two channels, one per [TodoAlarmStyle]. Channels cannot be changed once created, so "loud" and
 * "normal" have to be two channels the setting picks between, not one channel that is adjusted.
 * The user can still fine-tune either one in the system's notification settings, and that is
 * respected: the app never re-creates a channel.
 */
@AndroidEntryPoint
class TodoAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_FIRE -> fire(context, todoId, intent.getStringExtra(EXTRA_TITLE))
                    ACTION_DONE -> {
                        todoRepository.setStateWithCascade(todoId, complete = true)
                        NotificationManagerCompat.from(context).cancel(notificationId(todoId))
                    }
                    ACTION_SNOOZE -> {
                        snooze(context, todoId, intent.getStringExtra(EXTRA_TITLE))
                        NotificationManagerCompat.from(context).cancel(notificationId(todoId))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handling $action for $todoId failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(context: Context, todoId: String, fallbackTitle: String?) {
        val todo = todoRepository.getTodoById(todoId)
        if (todo == null || todo.isDeleted || todo.state == TodoState.COMPLETE) {
            Log.d(TAG, "Alarm for $todoId fired but the todo is gone or done; staying quiet")
            return
        }
        if (!settingsRepository.getNotificationsEnabled().first()) {
            Log.d(TAG, "Notifications disabled in app settings; alarm for '${todo.title}' suppressed")
            return
        }
        val style = TodoAlarmStyle.fromName(settingsRepository.getTodoAlarmStyle().first())
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; alarm for '${todo.title}' cannot be shown")
            return
        }
        try {
            manager.notify(notificationId(todoId), buildNotification(context, todo, fallbackTitle, style))
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was withdrawn between the check above and the post. Nothing to do.
            Log.w(TAG, "Notification refused", e)
        }
    }

    private fun snooze(context: Context, todoId: String, title: String?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = TodoAlarmScheduler.firePendingIntent(
            context, todoId, title, TodoAlarmScheduler.SNOOZE_REQUEST_CODE_OFFSET
        )
        TodoAlarmScheduler.setExactOrBestEffort(alarmManager, System.currentTimeMillis() + SNOOZE_MILLIS, pendingIntent)
        Log.d(TAG, "Snoozed $todoId for ${SNOOZE_MILLIS / 60_000} minutes")
    }

    private fun buildNotification(
        context: Context,
        todo: Todo,
        fallbackTitle: String?,
        style: TodoAlarmStyle
    ): android.app.Notification {
        val id = notificationId(todo.id)
        val title = todo.title.ifBlank { fallbackTitle ?: "Todo" }
        val dueText = todo.deadline?.let { day ->
            val time = todo.deadlineMinuteOfDay?.let { " at ${formatMinuteOfDay(it)}" } ?: ""
            "Due ${getDayLabel(day)}$time"
        } ?: "Due"

        // SplashActivity is the app's one exported entry point; it forwards to MainActivity.
        val openIntent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            context, id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val donePending = actionPendingIntent(context, todo.id, title, ACTION_DONE, id + 2)
        val snoozePending = actionPendingIntent(context, todo.id, title, ACTION_SNOOZE, id + 3)

        val channelId = if (style == TodoAlarmStyle.ALARM) CHANNEL_ALARM else CHANNEL_NOTIFICATION
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(dueText)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(if (style == TodoAlarmStyle.ALARM) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (style == TodoAlarmStyle.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Done", donePending)
            .addAction(0, "Snooze 1 h", snoozePending)

        // Pre-Oreo has no channels, so the loudness has to be set on the notification itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (style == TodoAlarmStyle.ALARM) {
                builder.setSound(alarmSoundUri(), AudioManager.STREAM_ALARM)
                    .setVibrate(ALARM_VIBRATION)
            } else {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
        }
        return builder.build()
    }

    private fun actionPendingIntent(context: Context, todoId: String, title: String, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            this.action = action
            data = android.net.Uri.parse("inventoria://todo-alarm/$todoId/$action")
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "TodoAlarmReceiver"

        const val ACTION_FIRE = "com.inventoria.app.action.TODO_ALARM_FIRE"
        const val ACTION_DONE = "com.inventoria.app.action.TODO_ALARM_DONE"
        const val ACTION_SNOOZE = "com.inventoria.app.action.TODO_ALARM_SNOOZE"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_TITLE = "title"

        const val CHANNEL_ALARM = "todo_alarms"
        const val CHANNEL_NOTIFICATION = "todo_reminders"

        const val SNOOZE_MILLIS = 60 * 60 * 1000L

        private val ALARM_VIBRATION = longArrayOf(0, 600, 300, 600, 300, 600)

        fun notificationId(todoId: String): Int = todoId.hashCode()

        private fun alarmSoundUri() =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        /**
         * Creates both channels if they do not exist yet. Idempotent: createNotificationChannel is
         * a no-op for an existing id, which is exactly what keeps the user's own per-channel
         * adjustments in system settings intact.
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                "Todo Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rings like an alarm when a todo with an alarm set comes due"
                setSound(alarmSoundUri(), alarmAttributes)
                enableVibration(true)
                vibrationPattern = ALARM_VIBRATION
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val reminderChannel = NotificationChannel(
                CHANNEL_NOTIFICATION,
                "Todo Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A normal notification when a todo with an alarm set comes due"
            }
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(reminderChannel)
        }
    }
}
