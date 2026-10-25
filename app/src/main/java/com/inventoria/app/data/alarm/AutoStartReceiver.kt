package com.inventoria.app.data.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.inventoria.app.data.ScheduleBlockRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.TaskTimePlan
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.model.autoStartPlan
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.splash.SplashActivity
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where an auto-start alarm goes off and a session begins by itself. See [AutoStartScheduler].
 *
 * Like the other alarm receivers it re-reads the row first and trusts the table over whatever it
 * was armed for: a todo ticked off, a block switched off or deleted since is answered by silence.
 * What it starts is left to [SessionStarter]; what it says about it is a quiet notification, because
 * a session that began on its own is exactly the thing you would otherwise only find out about when
 * the clock was already running.
 */
@AndroidEntryPoint
class AutoStartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var blockRepository: ScheduleBlockRepository

    @Inject
    lateinit var starter: SessionStarter

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var scheduler: AutoStartScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    when (kind) {
                        AutoStartScheduler.KIND_TODO -> startTodo(context, id)
                        AutoStartScheduler.KIND_BLOCK -> startBlock(context, id)
                    }
                } finally {
                    // Only the next start is ever armed; line up the one after, even when this one
                    // was skipped or failed.
                    scheduler.rearm()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-start for $kind $id failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun startTodo(context: Context, id: String) {
        val todo = todoRepository.getTodoById(id)
        if (todo == null || todo.isDeleted || todo.state == TodoState.COMPLETE) {
            Log.d(TAG, "Auto-start for todo $id fired but it is gone or done; staying quiet")
            return
        }
        val plan = todo.autoStartPlan()
        if (plan.isEmpty) return
        when (starter.startFromTodo(todo, plan.sessionPlan())) {
            SessionStarter.Outcome.STARTED -> announceStart(
                context, todo.title, id, plan.runMinutes?.let { "Stops by itself after ${TaskTimePlan.minutesLabel(it)}" }
            )
            SessionStarter.Outcome.FULL -> announceSkipped(context, todo.title, id)
            SessionStarter.Outcome.ALREADY_RUNNING, SessionStarter.Outcome.TOO_LATE -> Unit
        }
    }

    private suspend fun startBlock(context: Context, id: String) {
        val block = blockRepository.getBlockById(id)
        val now = System.currentTimeMillis()
        if (block == null || block.isDeleted || !block.autoStart || !block.occursOn(getStartOfDay(now))) {
            Log.d(TAG, "Auto-start for block $id fired but it is gone or switched off; staying quiet")
            return
        }
        when (starter.startFromBlock(block, now)) {
            SessionStarter.Outcome.STARTED -> announceStart(
                context, block.title.ifBlank { "Task" }, id,
                if (block.autoStop) "Stops by itself when the block ends" else null
            )
            SessionStarter.Outcome.FULL -> announceSkipped(context, block.title.ifBlank { "Task" }, id)
            SessionStarter.Outcome.ALREADY_RUNNING, SessionStarter.Outcome.TOO_LATE -> Unit
        }
    }

    private suspend fun announceStart(context: Context, title: String, id: String, detail: String?) =
        notify(context, id, "Started tracking $title", detail ?: "Started automatically")

    private suspend fun announceSkipped(context: Context, title: String, id: String) =
        notify(context, id, "Couldn't start $title", "Five sessions are already running -- stop one to make room")

    private suspend fun notify(context: Context, id: String, title: String, text: String) {
        if (!settingsRepository.getNotificationsEnabled().first()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        TodoAlarmReceiver.ensureChannels(context)
        val openIntent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            context, id.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TodoAlarmReceiver.CHANNEL_NOTIFICATION)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(("auto-start:$id").hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification refused", e)
        }
    }

    companion object {
        private const val TAG = "AutoStartReceiver"

        const val ACTION_FIRE = "com.inventoria.app.action.AUTO_START_FIRE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
    }
}
