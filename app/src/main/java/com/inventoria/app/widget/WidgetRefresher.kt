package com.inventoria.app.widget

import android.content.Context
import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.local.TodoDao
import com.inventoria.app.widget.collection.CollectionWidgetProvider
import com.inventoria.app.widget.task.TaskWidgetProvider
import com.inventoria.app.widget.todo.TodoWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widgets in step with the tables.
 *
 * Same design as TodoAlarmScheduler: watch the Room flows for the life of the process and ask
 * the relevant widget to redraw on every emission. A todo can change through the edit dialog,
 * the checkbox, a widget button, a notification's Done, or a Firebase pull from another device
 * -- every one of those lands in Room, so every one of them lands here without a call site to
 * forget.
 *
 * Each provider's [TaskWidgetProvider.requestUpdate]-style companion checks whether any instance
 * of that widget is actually placed before doing anything, so an app with no widgets pays only
 * the flow subscriptions. Emissions are debounced a little: a sync pull writes rows one at a
 * time, and redrawing a ListView widget per row would flicker.
 *
 * Started once from InventoriaApplication.onCreate. When the process is dead nothing here runs,
 * which is fine: a widget's own onUpdate builds from scratch, and any button press or periodic
 * update brings the process (and this) back up.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoDao: TodoDao,
    private val taskDao: TaskDao,
    private val collectionDao: CollectionDao,
    private val inventoryDao: InventoryDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            todoDao.getVisibleTodos().debounce(DEBOUNCE_MILLIS).collect { TodoWidgetProvider.requestUpdate(context) }
        }
        scope.launch {
            taskDao.getVisibleTasks().debounce(DEBOUNCE_MILLIS).collect { TaskWidgetProvider.requestUpdate(context) }
        }
        scope.launch {
            // Items matter too: a quantity edit changes whether a collection is "ready".
            combine(
                collectionDao.getAllCollections(),
                collectionDao.getAllCollectionItemsFlow(),
                inventoryDao.getAllItems()
            ) { _, _, _ -> Unit }
                .debounce(DEBOUNCE_MILLIS)
                .collect { CollectionWidgetProvider.requestUpdate(context) }
        }
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L
    }
}
