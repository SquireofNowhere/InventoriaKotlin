package com.inventoria.app.widget.todo

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.inventoria.app.R
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.ui.theme.Success
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import com.inventoria.app.widget.WidgetActionReceiver
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Supplies the rows of both todo widgets: Today's Todos, and Upcoming Todos when the adapter
 * intent carries [EXTRA_UPCOMING]. A Service, so @AndroidEntryPoint injects the repository, which
 * is then handed to the factory -- a RemoteViewsFactory is a plain object with no Hilt story of
 * its own.
 */
@AndroidEntryPoint
class TodoWidgetService : RemoteViewsService() {

    @Inject
    lateinit var todoRepository: TodoRepository

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoWidgetFactory(applicationContext, todoRepository, upcoming = intent.getBooleanExtra(EXTRA_UPCOMING, false))

    companion object {
        const val EXTRA_UPCOMING = "upcoming"
    }
}

/**
 * Today: the Today section exactly as the Today tab computes it ([TodoSections.today]), minus
 * completed rows -- the tab shows those ticked, a widget has no room for finished work. Upcoming:
 * the following days, each day's root rows labelled with the day. Nesting depth becomes
 * indentation; folds are ignored (there is no way to toggle one here).
 */
private class TodoWidgetFactory(
    private val context: Context,
    private val todoRepository: TodoRepository,
    private val upcoming: Boolean
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<TodoWidgetRow> = emptyList()
    private var todayStart: Long = 0L

    override fun onCreate() = Unit

    /** Runs on the host's binder thread, never the main thread, so blocking on Room here is the
     * documented way to load a widget list. */
    override fun onDataSetChanged() {
        val now = System.currentTimeMillis()
        todayStart = getStartOfDay(now)
        val all = runBlocking { todoRepository.getVisibleTodos().first() }
        rows = if (upcoming) TodoWidgetRows.upcoming(all, now) else TodoWidgetRows.today(all, now)
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        val entry = row.entry
        val todo = entry.todo
        val views = RemoteViews(context.packageName, R.layout.widget_todo_row)

        views.setTextViewText(R.id.widget_todo_title, todo.title.ifBlank { "Todo" })
        views.setInt(R.id.widget_todo_dot, "setColorFilter", todo.kind.colorValue.toInt())

        val density = context.resources.displayMetrics.density
        val startPadding = ((8 + entry.depth * 16) * density).toInt()
        views.setViewPadding(R.id.widget_todo_row, startPadding, (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())

        val parts = mutableListOf<String>()
        // Upcoming spans days, so each day's root rows say which one; nested rows sit under theirs.
        if (entry.depth == 0) row.dayStart?.let { parts += getDayLabel(it) }
        todo.deadlineMinuteOfDay?.let { parts += formatMinuteOfDay(it) }
        val deadline = todo.deadline
        if (deadline != null && deadline < todayStart && todo.state != TodoState.COMPLETE) {
            parts += context.getString(R.string.widget_overdue)
        }
        todo.priority?.let { parts += it.name }
        entry.childProgress?.let { (done, total) -> parts += context.getString(R.string.widget_sub_todos, done, total) }
        // Only at depth 0: nested rows already sit under their parent.
        if (entry.depth == 0) entry.parentName?.let { parts += "in $it" }
        val subtitle = parts.joinToString(" · ")
        views.setTextViewText(R.id.widget_todo_subtitle, subtitle)
        views.setViewVisibility(R.id.widget_todo_subtitle, if (subtitle.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)

        // The same tri-state the Todos tab's checkbox shows: a dash once some sub-todos are done,
        // green once they all are and the parent only needs its own tap. Completed rows are
        // filtered out above, so the filled tick only appears for a parent whose children
        // finished it.
        val allChildrenComplete = entry.childProgress?.let { (done, total) -> total > 0 && done == total } ?: false
        val readyToComplete = entry.effectiveState == TodoState.IN_PROGRESS && allChildrenComplete
        views.setImageViewResource(
            R.id.widget_todo_check,
            when (entry.effectiveState) {
                TodoState.COMPLETE -> R.drawable.ic_widget_check_filled
                TodoState.IN_PROGRESS -> R.drawable.ic_widget_check_partial
                TodoState.INCOMPLETE -> R.drawable.ic_widget_check_empty
            }
        )
        views.setInt(
            R.id.widget_todo_check,
            "setColorFilter",
            if (readyToComplete) Success.toArgb() else context.getColor(R.color.widget_on_surface_variant)
        )

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

    override fun getItemId(position: Int): Long = rows[position].entry.todo.id.hashCode().toLong()

    override fun hasStableIds(): Boolean = true
}
