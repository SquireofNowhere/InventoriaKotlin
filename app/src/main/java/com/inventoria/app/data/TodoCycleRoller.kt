package com.inventoria.app.data

import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts repeating todos over when their cycle ends -- see [TodoRepository.settleRepeatingTodos].
 *
 * Runs once as the process comes up, which is what covers a phone that was off or an app that was
 * closed across one or more cycle ends (every skipped cycle is counted then, not lost), and then
 * once a minute so a cycle ending at midnight is picked up while the app is open. Polling is
 * cheap here: settling is a pass over the todo table that does nothing unless a repeating todo's
 * deadline day is behind today.
 *
 * Started once from InventoriaApplication.onCreate, next to TodoAlarmScheduler. That one reacts to
 * the rows this one rewrites -- a reset todo has a new deadline, so it picks up its new alarm
 * without this class knowing alarms exist.
 */
@Singleton
class TodoCycleRoller @Inject constructor(
    private val todoRepository: TodoRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                todoRepository.settleRepeatingTodos(getStartOfDay(System.currentTimeMillis()))
                delay(POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 60_000L
    }
}
