package com.inventoria.app.ui.screens.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.TodoState
import com.inventoria.app.data.repository.NextAlarm
import com.inventoria.app.data.repository.SystemClockRepository
import com.inventoria.app.util.getStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A todo whose deadline carries a time of day, so an alarm can actually be set for it. Todos with
 * a date but no time are excluded: "some point on Thursday" has no hour to ring at. */
data class AlarmableTodo(val todo: Todo, val minuteOfDay: Int, val isToday: Boolean)

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val systemClockRepository: SystemClockRepository,
    taskRepository: TaskRepository,
    todoRepository: TodoRepository
) : ViewModel() {

    /** Re-read on a timer rather than observed: [SystemClockRepository.nextAlarm] is a snapshot of
     * a system value with no change notification, so polling is the only way to notice the user
     * setting or cancelling an alarm in the clock app while this screen is open. */
    private val _nextAlarm = MutableStateFlow<NextAlarm?>(null)
    val nextAlarm: StateFlow<NextAlarm?> = _nextAlarm.asStateFlow()

    private val _lastActionFailed = MutableStateFlow(false)
    val lastActionFailed: StateFlow<Boolean> = _lastActionFailed.asStateFlow()

    val hasClockApp: Boolean = systemClockRepository.hasClockApp()

    /** The currently running task, if any -- a started timer takes its name, so the clock app's
     * notification says what it's for instead of just counting down. */
    val runningTask: StateFlow<Task?> = taskRepository.getVisibleTasks()
        .map { tasks -> tasks.firstOrNull { it.isRunning } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Incomplete todos due today or tomorrow that name a time, soonest first. Deliberately not
     * the whole list: an alarm is a thing you set for the next few hours, and offering to ring for
     * something due next month is noise. */
    val alarmableTodos: StateFlow<List<AlarmableTodo>> = todoRepository.getVisibleTodos()
        .map { todos ->
            val todayStart = getStartOfDay(System.currentTimeMillis())
            val tomorrowEnd = todayStart + 2 * 86_400_000L
            todos
                .filter {
                    it.state != TodoState.COMPLETE &&
                        it.deadlineMinuteOfDay != null &&
                        it.deadline != null &&
                        it.deadline!! >= todayStart && it.deadline!! < tomorrowEnd
                }
                .map { AlarmableTodo(it, it.deadlineMinuteOfDay!!, it.deadline == todayStart) }
                .sortedWith(compareBy({ !it.isToday }, { it.minuteOfDay }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                _nextAlarm.value = systemClockRepository.nextAlarm()
                delay(30_000)
            }
        }
    }

    fun refreshNextAlarm() {
        _nextAlarm.value = systemClockRepository.nextAlarm()
    }

    fun dismissFailure() {
        _lastActionFailed.value = false
    }

    fun startTimer(minutes: Int, label: String) {
        _lastActionFailed.value = !systemClockRepository.startTimer(minutes * 60, label)
    }

    fun setAlarm(minuteOfDay: Int, label: String) {
        val ok = systemClockRepository.setAlarm(minuteOfDay / 60, minuteOfDay % 60, label)
        _lastActionFailed.value = !ok
        // The clock app writes the alarm as it handles the intent, so the value we hold is stale
        // the moment this returns -- re-read rather than waiting up to 30s for the next poll.
        if (ok) refreshNextAlarm()
    }

    fun openAlarms() {
        _lastActionFailed.value = !systemClockRepository.showAlarms()
    }

    fun openTimers() {
        _lastActionFailed.value = !systemClockRepository.showTimers()
    }
}
