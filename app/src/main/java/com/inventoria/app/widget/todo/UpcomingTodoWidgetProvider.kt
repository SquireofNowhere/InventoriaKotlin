package com.inventoria.app.widget.todo

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
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Upcoming Todos home-screen widget: what is due over the next [TodoWidgetRows.UPCOMING_DAYS]
 * days, soonest first, each day's rows labelled with the day and a tick per row -- the Today's
 * Todos widget's look ahead.
 *
 * Same frame, row layout and row service as [TodoWidgetProvider]; the service is told which list to
 * load by an extra on the adapter intent. Edits reach it through WidgetRefresher like the Today
 * widget. Its days move at midnight, which the Today widget's alarm covers when that widget is
 * placed; on its own the half-hourly updatePeriodMillis catches the rollover.
 */
@AndroidEntryPoint
class UpcomingTodoWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var todoRepository: TodoRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = TodoWidgetRows.upcoming(todoRepository.getVisibleTodos().first()).size
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, build(context, id, count))
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list)
            } catch (e: Exception) {
                Log.e(TAG, "Updating the upcoming todo widget failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpcomingTodoWidget"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UpcomingTodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, UpcomingTodoWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private fun build(context: Context, appWidgetId: Int, count: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_todo)
            views.setOnClickPendingIntent(R.id.widget_todo_header, WidgetNav.openPendingIntent(context, WidgetNav.ROUTE_TODOS))
            views.setTextViewText(R.id.widget_todo_header_title, context.getString(R.string.widget_todo_upcoming_title))
            views.setTextViewText(R.id.widget_todo_count, if (count == 0) "" else count.toString())
            views.setTextViewText(R.id.widget_todo_empty, context.getString(R.string.widget_todo_upcoming_empty))

            // A data URI of its own, so the host never hands this instance the Today widget's
            // cached factory (it keys factories on the intent, data included).
            val adapterIntent = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TodoWidgetService.EXTRA_UPCOMING, true)
                data = Uri.parse("inventoria://widget-todo-upcoming/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_todo_list, adapterIntent)
            views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
            views.setPendingIntentTemplate(R.id.widget_todo_list, WidgetActionReceiver.rowTemplate(context, "todo_upcoming"))
            return views
        }
    }
}
