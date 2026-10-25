package com.inventoria.app.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.inventoria.app.data.ScheduleBlockRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.nextAutoStartAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the alarms that start a session by itself: one per todo with an auto-start plan
 * ([nextAutoStartAfter]) and one per schedule block set to start tracking
 * ([ScheduleBlock.nextStartAfter]). Built like [TodoAlarmScheduler] -- watch the tables, work out
 * what should exist, reconcile AlarmManager to it -- so a todo or block edited, ticked off, deleted
 * or synced from another device is picked up with no call site to forget. Only the next start of
 * each is armed; [AutoStartReceiver] calls [rearm] after one fires to line up the one after.
 */
@Singleton
class AutoStartScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository,
    private val blockRepository: ScheduleBlockRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Volatile
    private var started = false

    /** "todo:<id>" / "block:<id>" -> trigger time currently handed to AlarmManager by this process. */
    private val armed = mutableMapOf<String, Long>()

    @Volatile
    private var latest: Pair<List<Todo>, List<ScheduleBlock>> = emptyList<Todo>() to emptyList()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(todoRepository.getVisibleTodos(), blockRepository.getVisibleBlocks()) { todos, blocks -> todos to blocks }
                .collect { pair ->
                    latest = pair
                    reconcile(pair.first, pair.second)
                }
        }
    }

    fun rearm() = reconcile(latest.first, latest.second)

    @Synchronized
    private fun reconcile(todos: List<Todo>, blocks: List<ScheduleBlock>) {
        val now = System.currentTimeMillis()
        val desired = buildMap<String, Long> {
            todos.forEach { todo -> todo.nextAutoStartAfter(now)?.let { put(key(KIND_TODO, todo.id), it) } }
            blocks.forEach { block -> block.nextStartAfter(now)?.let { put(key(KIND_BLOCK, block.id), it) } }
        }

        (armed.keys - desired.keys).toList().forEach { key ->
            cancel(key)
            armed.remove(key)
        }
        desired.forEach { (key, at) ->
            if (armed[key] != at) {
                set(key, at)
                armed[key] = at
            }
        }
    }

    private fun set(key: String, triggerAt: Long) {
        TodoAlarmScheduler.setExactOrBestEffort(alarmManager, triggerAt, firePendingIntent(context, key))
        Log.d(TAG, "Armed auto-start $key at $triggerAt")
    }

    private fun cancel(key: String) {
        alarmManager.cancel(firePendingIntent(context, key))
        Log.d(TAG, "Cancelled auto-start $key")
    }

    companion object {
        private const val TAG = "AutoStartScheduler"

        const val KIND_TODO = "todo"
        const val KIND_BLOCK = "block"

        fun key(kind: String, id: String) = "$kind:$id"

        fun firePendingIntent(context: Context, key: String): PendingIntent {
            val kind = key.substringBefore(':')
            val id = key.substringAfter(':')
            val intent = Intent(context, AutoStartReceiver::class.java).apply {
                action = AutoStartReceiver.ACTION_FIRE
                data = android.net.Uri.parse("inventoria://auto-start/$kind/$id")
                putExtra(AutoStartReceiver.EXTRA_KIND, kind)
                putExtra(AutoStartReceiver.EXTRA_ID, id)
            }
            return PendingIntent.getBroadcast(
                context,
                "auto-start:$key".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
