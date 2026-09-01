package com.inventoria.app.widget

import android.app.PendingIntent
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.ui.screens.task.TaskTimerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where every home-screen widget button lands.
 *
 * Same shape as TodoAlarmReceiver: an @AndroidEntryPoint receiver with injected repositories,
 * goAsync() so the Room write can suspend, and one distinct data URI per (action, id) so two
 * buttons never share a cached PendingIntent. The widgets themselves are refreshed by
 * [WidgetRefresher] watching the tables, not by anything here -- a button just changes data.
 *
 * Buttons inside a ListView (the Todo and Collection widgets) cannot each carry their own
 * PendingIntent; the list has one mutable template ([rowTemplate]) and each row fills in
 * [EXTRA_ROW_ACTION] plus its ids ([rowFillIn]). Everything outside a list uses a plain immutable
 * broadcast ([broadcast]).
 *
 * [ACTION_OPEN_ROUTE] starts an activity, which a receiver may only do while the launcher's
 * widget tap still lends it the privilege -- so it happens synchronously in onReceive, before any
 * coroutine, and never after goAsync().
 */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = if (intent.action == ACTION_ROW) intent.getStringExtra(EXTRA_ROW_ACTION) else intent.action
        if (action == null) return

        if (action == ACTION_OPEN_ROUTE) {
            val route = intent.getStringExtra(EXTRA_ROUTE) ?: return
            try {
                context.startActivity(WidgetNav.openIntent(context, route))
            } catch (e: Exception) {
                Log.w(TAG, "Could not open $route from a widget row", e)
            }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, action, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Widget action $action failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String, intent: Intent) {
        val now = System.currentTimeMillis()
        when (action) {
            ACTION_TODO_DONE -> {
                val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
                todoRepository.setStateWithCascade(todoId, complete = true)
            }
            ACTION_TASK_PAUSE -> {
                val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
                // Just the pause: the tracker screen's "track this interruption?" prompt needs a
                // screen to be asked on.
                taskRepository.pauseSession(groupId, now)
            }
            ACTION_TASK_RESUME -> {
                val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
                // Room first, service second: TaskTimerService stops itself the moment it
                // observes no running segment, so starting it before the insert lands would let
                // it see the old table and quit.
                if (taskRepository.resumeSession(groupId, now) != null) startTimerService(context)
            }
            ACTION_TASK_STOP -> {
                val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
                // The todo check-in the tracker screen offers after a stop is UI; a widget has
                // nowhere to ask it, so the todo simply stays as it is.
                taskRepository.stopSession(groupId, now)
                // Stopping an interruption resumes what it interrupted, which needs the timer up.
                if (taskRepository.getVisibleTasksList().any { it.isRunning }) startTimerService(context)
            }
            ACTION_TASK_START_NEW -> {
                val name = "Task ${taskRepository.activeSessionCount() + 1}"
                if (taskRepository.startNewSession(name, now) != null) startTimerService(context)
            }
        }
    }

    /**
     * A widget tap puts the app on the system's short foreground-service allowlist, which is
     * what makes this legal from a receiver on Android 12+. If the window is somehow missed the
     * segment is still running in the table; TaskTrackerViewModel binds the service the next
     * time the tracker is opened, so degrade to a log line rather than a crash.
     */
    private fun startTimerService(context: Context) {
        val intent = Intent(context, TaskTimerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Timer service refused from the widget; it will start with the app", e)
            } else {
                Log.w(TAG, "Could not start the timer service from the widget", e)
            }
        }
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"

        const val ACTION_ROW = "com.inventoria.app.action.WIDGET_ROW"
        const val ACTION_OPEN_ROUTE = "com.inventoria.app.action.WIDGET_OPEN_ROUTE"
        const val ACTION_TODO_DONE = "com.inventoria.app.action.WIDGET_TODO_DONE"
        const val ACTION_TASK_PAUSE = "com.inventoria.app.action.WIDGET_TASK_PAUSE"
        const val ACTION_TASK_RESUME = "com.inventoria.app.action.WIDGET_TASK_RESUME"
        const val ACTION_TASK_STOP = "com.inventoria.app.action.WIDGET_TASK_STOP"
        const val ACTION_TASK_START_NEW = "com.inventoria.app.action.WIDGET_TASK_START_NEW"

        const val EXTRA_ROW_ACTION = "row_action"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_ROUTE = "route"

        /** An immutable broadcast for one button. [id] keeps the PendingIntent distinct per row. */
        fun broadcast(context: Context, action: String, id: String = "", extras: Intent.() -> Unit = {}): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("inventoria://widget-action/$action/$id")
                extras()
            }
            return PendingIntent.getBroadcast(
                context,
                (action + id).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * The one PendingIntent a ListView's rows share. Mutable, because rows add extras to it;
         * with FLAG_IMMUTABLE the fill-ins are silently dropped on Android 12+. The explicit
         * component keeps it from being filled in with anything but our own extras.
         */
        fun rowTemplate(context: Context, widgetName: String): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = ACTION_ROW
                data = Uri.parse("inventoria://widget-row/$widgetName")
            }
            return PendingIntent.getBroadcast(
                context,
                ("row" + widgetName).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        /** What a row adds to [rowTemplate] when tapped. */
        fun rowFillIn(action: String, extras: Intent.() -> Unit = {}): Intent =
            Intent().apply {
                putExtra(EXTRA_ROW_ACTION, action)
                extras()
            }
    }
}
