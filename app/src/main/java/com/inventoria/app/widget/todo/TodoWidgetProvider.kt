package com.inventoria.app.widget.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.inventoria.app.R
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.ui.screens.todo.TodoSections
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * The Today's Todos home-screen widget: what is due or overdue today, as the Today tab groups it
 * ([TodoSections]), with a tick per row.
 *
 * The rows come from [TodoWidgetService]; this provider draws the frame, points the ListView at
 * the service, installs the one mutable click template every row fills in, and tells the host
 * to reload the rows.
 *
 * "Today" moves at midnight, and the overdue carry-over with it. updatePeriodMillis is only
 * allowed to be half-hourly and only fires while the launcher feels like it, so an inexact alarm
 * is armed for just after midnight whenever a widget is placed ([armMidnightRefresh]).
 */
@AndroidEntryPoint
class TodoWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var todoRepository: TodoRepository

    override fun onReceive(context: Context, intent: Intent) {
        // super first: that is where Hilt injects, and where APPWIDGET_UPDATE reaches onUpdate.
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT) {
            requestUpdate(context)
            armMidnightRefresh(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dueCount = TodoSections.today(todoRepository.getVisibleTodos().first(), hideCompleted = true).size
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, build(context, id, dueCount))
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list)
                armMidnightRefresh(context)
            } catch (e: Exception) {
                Log.e(TAG, "Updating the todo widget failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        armMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightPendingIntent(context))
    }

    companion object {
        private const val TAG = "TodoWidgetProvider"
        private const val ACTION_MIDNIGHT = "com.inventoria.app.action.WIDGET_TODO_MIDNIGHT"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodoWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private fun build(context: Context, appWidgetId: Int, dueCount: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_todo)
            views.setOnClickPendingIntent(R.id.widget_todo_header, WidgetNav.openPendingIntent(context, WidgetNav.ROUTE_TODOS))
            views.setTextViewText(R.id.widget_todo_count, if (dueCount == 0) "" else dueCount.toString())

            // One adapter intent per widget instance: the host caches factories by intent
            // identity, and a shared one would make two instances fight over one factory.
            val adapterIntent = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("inventoria://widget-todo/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_todo_list, adapterIntent)
            views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
            views.setPendingIntentTemplate(R.id.widget_todo_list, WidgetActionReceiver.rowTemplate(context, "todo"))
            return views
        }

        private fun midnightPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TodoWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * An inexact, doze-tolerant alarm for shortly after the next midnight. Idempotent: setting
         * it again with the same PendingIntent replaces the previous one. Nothing is armed when no
         * widget is placed, so an app without the widget schedules nothing.
         */
        fun armMidnightRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = midnightPendingIntent(context)
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) {
                alarmManager.cancel(pending)
                return
            }
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, nextMidnight, pending)
        }
    }
}
