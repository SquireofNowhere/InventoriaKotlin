package com.inventoria.app.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.inventoria.app.data.local.TodoDao
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.reminderTriggerAt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps AlarmManager in step with the Todo table.
 *
 * Watches the visible-todos flow for the life of the process and, on every emission, works out
 * the set of alarms that *should* exist (one per incomplete todo with a deadline and an alarm
 * setting whose trigger is still in the future -- see [reminderTriggerAt]) and reconciles
 * AlarmManager to it: new or moved triggers are (re)set, everything else is cancelled.
 *
 * Deriving from the table rather than hooking each write path is the whole design. A todo can
 * change through the edit dialog, the checkbox, a drag, a delete-and-undo, a Firebase pull from
 * another device, or a notification's Done button -- and every one of those lands in Room, so
 * every one of them lands here without a call site to forget.
 *
 * Started once from InventoriaApplication.onCreate, which is also what re-arms everything after a
 * reboot: [BootReceiver] exists only so the process comes up, and coming up runs this.
 *
 * The in-memory [armed] map is an optimisation, not a source of truth. After a process restart it
 * is empty, so every desired alarm is set again (a set on an existing PendingIntent replaces it,
 * so that is idempotent) and any alarm armed by the previous process for a todo that has since
 * finished elsewhere is left to fire -- [TodoAlarmReceiver] re-reads the row before it makes any
 * noise, so that fires silently.
 */
@Singleton
class TodoAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoDao: TodoDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Volatile
    private var started = false

    /** todoId -> trigger time currently handed to AlarmManager by this process. */
    private val armed = mutableMapOf<String, Long>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            todoDao.getVisibleTodos().collect { todos -> reconcile(todos) }
        }
    }

    @Synchronized
    private fun reconcile(todos: List<Todo>) {
        val now = System.currentTimeMillis()
        val desired = todos
            .mapNotNull { todo -> todo.reminderTriggerAt()?.takeIf { it > now }?.let { todo to it } }
            .associate { (todo, at) -> todo.id to (at to todo.title) }

        (armed.keys - desired.keys).toList().forEach { id ->
            cancel(id)
            armed.remove(id)
        }
        desired.forEach { (id, pair) ->
            val (at, title) = pair
            if (armed[id] != at) {
                set(id, title, at)
                armed[id] = at
            }
        }
    }

    private fun set(todoId: String, title: String, triggerAt: Long) {
        val pendingIntent = firePendingIntent(context, todoId, title, PRIMARY_REQUEST_CODE_OFFSET)
        setExactOrBestEffort(alarmManager, triggerAt, pendingIntent)
        Log.d(TAG, "Armed alarm for '$title' at $triggerAt")
    }

    private fun cancel(todoId: String) {
        alarmManager.cancel(firePendingIntent(context, todoId, null, PRIMARY_REQUEST_CODE_OFFSET))
        Log.d(TAG, "Cancelled alarm for $todoId")
    }

    companion object {
        private const val TAG = "TodoAlarmScheduler"

        /** Request-code offsets keep the scheduler-managed alarm and a user's snooze alarm as two
         * distinct PendingIntents for the same todo: [reconcile] only ever touches the primary one,
         * so a snooze survives the next flow emission instead of being cancelled as "not desired". */
        const val PRIMARY_REQUEST_CODE_OFFSET = 0
        const val SNOOZE_REQUEST_CODE_OFFSET = 1

        fun firePendingIntent(context: Context, todoId: String, title: String?, requestCodeOffset: Int): PendingIntent {
            val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
                action = TodoAlarmReceiver.ACTION_FIRE
                // Distinct data URIs per todo so two todos never share a PendingIntent even when
                // their hashCodes collide; the request code alone is not part of Intent identity.
                data = android.net.Uri.parse("inventoria://todo-alarm/$todoId/$requestCodeOffset")
                putExtra(TodoAlarmReceiver.EXTRA_TODO_ID, todoId)
                if (title != null) putExtra(TodoAlarmReceiver.EXTRA_TITLE, title)
            }
            return PendingIntent.getBroadcast(
                context,
                todoId.hashCode() + requestCodeOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Exact when the system lets us, inexact-but-doze-proof when it does not. On Android 12
         * exact alarms need SCHEDULE_EXACT_ALARM, which the user (or an OEM) can revoke; on 13+
         * USE_EXACT_ALARM is granted at install. Either way a missing grant must degrade to a
         * late alarm, never to a crash inside a Room flow collector.
         */
        fun setExactOrBestEffort(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            try {
                if (exactAllowed) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused, falling back to inexact", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
    }
}
