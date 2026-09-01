package com.inventoria.app.widget.todo

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.inventoria.app.R
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.screens.todo.TodoSections
import com.inventoria.app.ui.screens.todo.TodoTreeEntry
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.getStartOfDay
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Supplies the Today's Todos widget's rows. A Service, so @AndroidEntryPoint injects the
 * repository, which is then handed to the factory -- a RemoteViewsFactory is a plain object with
 * no Hilt story of its own.
 */
@AndroidEntryPoint
class TodoWidgetService : RemoteViewsService() {

    @Inject
    lateinit var todoRepository: TodoRepository

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoWidgetFactory(applicationContext, todoRepository)
}

/**
 * The Today section exactly as the Today tab computes it ([TodoSections.today]), minus completed
 * rows: the tab shows those ticked, a widget has no room for finished work. Nesting depth becomes
 * indentation; folds are ignored (there is no way to toggle one here).
 */
private class TodoWidgetFactory(
    private val context: Context,
    private val todoRepository: TodoRepository
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<TodoTreeEntry> = emptyList()
    private var todayStart: Long = 0L

    override fun onCreate() = Unit

    /** Runs on the host's binder thread, never the main thread, so blocking on Room here is the
     * documented way to load a widget list. */
    override fun onDataSetChanged() {
        val now = System.currentTimeMillis()
        todayStart = getStartOfDay(now)
        rows = runBlocking {
            TodoSections.today(todoRepository.getVisibleTodos().first(), hideCompleted = true, nowMillis = now)
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val entry = rows[position]
        val todo = entry.todo
        val views = RemoteViews(context.packageName, R.layout.widget_todo_row)

        views.setTextViewText(R.id.widget_todo_title, todo.title.ifBlank { "Todo" })
        views.setInt(R.id.widget_todo_dot, "setColorFilter", todo.kind.colorValue.toInt())

        val density = context.resources.displayMetrics.density
        val startPadding = ((8 + entry.depth * 16) * density).toInt()
        views.setViewPadding(R.id.widget_todo_row, startPadding, (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())

        val parts = mutableListOf<String>()
        todo.deadlineMinuteOfDay?.let { parts += formatMinuteOfDay(it) }
        val deadline = todo.deadline
        if (deadline != null && deadline < todayStart && todo.state != TodoState.COMPLETE) {
            parts += context.getString(R.string.widget_overdue)
        }
        todo.priority?.let { parts += it.name }
        // Only at depth 0: nested rows already sit under their parent.
        if (entry.depth == 0) entry.parentName?.let { parts += "in $it" }
        val subtitle = parts.joinToString(" · ")
        views.setTextViewText(R.id.widget_todo_subtitle, subtitle)
        views.setViewVisibility(R.id.widget_todo_subtitle, if (subtitle.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)

        views.setOnClickFillInIntent(
            R.id.widget_todo_check,
            WidgetActionReceiver.rowFillIn(WidgetActionReceiver.ACTION_TODO_DONE) {
                putExtra(WidgetActionReceiver.EXTRA_TODO_ID, todo.id)
            }
        )
        views.setOnClickFillInIntent(
            R.id.widget_todo_row,
            WidgetActionReceiver.rowFillIn(WidgetActionReceiver.ACTION_OPEN_ROUTE) {
                putExtra(WidgetActionReceiver.EXTRA_ROUTE, WidgetNav.ROUTE_TODOS)
            }
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = rows[position].todo.id.hashCode().toLong()

    override fun hasStableIds(): Boolean = true
}
