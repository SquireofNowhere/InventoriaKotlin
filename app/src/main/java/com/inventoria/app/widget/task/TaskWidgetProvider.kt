package com.inventoria.app.widget.task

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.inventoria.app.R
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.task.formatTime
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Task Tracker home-screen widget: every active session with a live timer and its own
 * Pause/Resume and Stop, plus a way to start a new one.
 *
 * A running session's time is a Chronometer, which the launcher ticks by itself -- no updates
 * from this app, no battery spent. Its base is derived from the running segment's wall-clock
 * startTime on every build, so it is right after a reboot too, and it counts the current segment
 * only, which is exactly what the tracker screen's card shows. A paused session shows the total
 * of its finished segments instead.
 *
 * An AppWidgetProvider is a BroadcastReceiver, so @AndroidEntryPoint injects here just as it does
 * on TodoAlarmReceiver. onUpdate reads the table itself: it has to be able to draw from scratch
 * when the process was dead.
 */
@AndroidEntryPoint
class TaskWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var taskRepository: TaskRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = build(context, taskRepository.getVisibleTasksList())
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.e(TAG, "Updating the task widget failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** One active session as the widget shows it. */
    private data class WidgetSession(
        val groupId: String,
        val name: String,
        val kind: TaskKind,
        /** The live segment, or null while paused. */
        val running: Task?,
        /** Finished segments' time, shown while paused. */
        val finishedMillis: Long,
        val latestStart: Long
    )

    private class Slot(val root: Int, val dot: Int, val name: Int, val timer: Int, val paused: Int, val toggle: Int, val stop: Int)

    companion object {
        private const val TAG = "TaskWidgetProvider"

        private val SLOTS = listOf(
            Slot(R.id.widget_task_slot1, R.id.widget_task_slot1_dot, R.id.widget_task_slot1_name, R.id.widget_task_slot1_timer, R.id.widget_task_slot1_paused, R.id.widget_task_slot1_toggle, R.id.widget_task_slot1_stop),
            Slot(R.id.widget_task_slot2, R.id.widget_task_slot2_dot, R.id.widget_task_slot2_name, R.id.widget_task_slot2_timer, R.id.widget_task_slot2_paused, R.id.widget_task_slot2_toggle, R.id.widget_task_slot2_stop),
            Slot(R.id.widget_task_slot3, R.id.widget_task_slot3_dot, R.id.widget_task_slot3_name, R.id.widget_task_slot3_timer, R.id.widget_task_slot3_paused, R.id.widget_task_slot3_toggle, R.id.widget_task_slot3_stop),
            Slot(R.id.widget_task_slot4, R.id.widget_task_slot4_dot, R.id.widget_task_slot4_name, R.id.widget_task_slot4_timer, R.id.widget_task_slot4_paused, R.id.widget_task_slot4_toggle, R.id.widget_task_slot4_stop),
            Slot(R.id.widget_task_slot5, R.id.widget_task_slot5_dot, R.id.widget_task_slot5_name, R.id.widget_task_slot5_timer, R.id.widget_task_slot5_paused, R.id.widget_task_slot5_toggle, R.id.widget_task_slot5_stop)
        )

        /** Redraw every placed instance, if there are any. Goes through the normal update
         * broadcast so there is exactly one code path that builds the widget. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TaskWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private fun activeSessions(tasks: List<Task>): List<WidgetSession> =
            tasks.groupBy { it.groupId }
                .filter { (_, segments) -> segments.any { it.isSessionActive } }
                .map { (groupId, segments) ->
                    val latest = segments.maxBy { it.startTime }
                    WidgetSession(
                        groupId = groupId,
                        name = latest.name.ifBlank { "Task" },
                        kind = latest.kind,
                        running = segments.firstOrNull { it.isRunning },
                        finishedMillis = segments.filter { !it.isRunning }.sumOf { it.duration },
                        latestStart = latest.startTime
                    )
                }
                // Newest first, the tracker screen's own order.
                .sortedByDescending { it.latestStart }
                .take(TaskRepository.MAX_ACTIVE_SESSIONS)

        fun build(context: Context, tasks: List<Task>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_task)
            val sessions = activeSessions(tasks)
            val now = System.currentTimeMillis()

            views.setOnClickPendingIntent(R.id.widget_task_header, WidgetNav.openPendingIntent(context, WidgetNav.ROUTE_TASKS))
            val startNew = WidgetActionReceiver.broadcast(context, WidgetActionReceiver.ACTION_TASK_START_NEW)
            views.setOnClickPendingIntent(R.id.widget_task_add, startNew)
            views.setOnClickPendingIntent(R.id.widget_task_start, startNew)
            views.setViewVisibility(
                R.id.widget_task_add,
                if (sessions.size >= TaskRepository.MAX_ACTIVE_SESSIONS) View.GONE else View.VISIBLE
            )

            views.setViewVisibility(R.id.widget_task_empty, if (sessions.isEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_task_sessions, if (sessions.isEmpty()) View.GONE else View.VISIBLE)

            SLOTS.forEachIndexed { index, slot ->
                val session = sessions.getOrNull(index)
                if (session == null) {
                    views.setViewVisibility(slot.root, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(slot.root, View.VISIBLE)
                views.setTextViewText(slot.name, session.name)
                views.setInt(slot.dot, "setColorFilter", session.kind.colorValue.toInt())

                val running = session.running
                if (running != null) {
                    // Chronometer counts from an elapsedRealtime base; translate the wall-clock
                    // start into one. Current segment only, like the tracker's card.
                    val base = SystemClock.elapsedRealtime() - (now - running.startTime)
                    views.setChronometer(slot.timer, base, null, true)
                    views.setViewVisibility(slot.timer, View.VISIBLE)
                    views.setViewVisibility(slot.paused, View.GONE)
                    views.setImageViewResource(slot.toggle, R.drawable.ic_widget_pause)
                    views.setContentDescription(slot.toggle, context.getString(R.string.pause))
                    views.setOnClickPendingIntent(
                        slot.toggle,
                        WidgetActionReceiver.broadcast(context, WidgetActionReceiver.ACTION_TASK_PAUSE, session.groupId) {
                            putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, session.groupId)
                        }
                    )
                } else {
                    views.setChronometer(slot.timer, 0L, null, false)
                    views.setViewVisibility(slot.timer, View.GONE)
                    views.setTextViewText(
                        slot.paused,
                        "${context.getString(R.string.widget_paused)} · ${formatTime(session.finishedMillis)}"
                    )
                    views.setViewVisibility(slot.paused, View.VISIBLE)
                    views.setImageViewResource(slot.toggle, R.drawable.ic_widget_play)
                    views.setContentDescription(slot.toggle, context.getString(R.string.resume))
                    views.setOnClickPendingIntent(
                        slot.toggle,
                        WidgetActionReceiver.broadcast(context, WidgetActionReceiver.ACTION_TASK_RESUME, session.groupId) {
                            putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, session.groupId)
                        }
                    )
                }
                views.setOnClickPendingIntent(
                    slot.stop,
                    WidgetActionReceiver.broadcast(context, WidgetActionReceiver.ACTION_TASK_STOP, session.groupId) {
                        putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, session.groupId)
                    }
                )
            }
            return views
        }
    }
}
