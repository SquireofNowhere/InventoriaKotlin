package com.inventoria.app.data.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Task
import com.inventoria.app.data.model.TaskTimePlan
import com.inventoria.app.data.model.Todo
import com.inventoria.app.data.model.modalTypeIdFor
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.ui.screens.task.TaskTimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts a tracked session with nobody at the screen -- what the auto-start alarms do when they go
 * off. It does what the Start buttons do (a todo's Start in TodoViewModel, a block's in
 * TodayViewModel), minus the pop-ups: insert the running task, bring the timer service up, push.
 *
 * Unlike those buttons it refuses to open a sixth concurrent session. The screen answers a sixth
 * tap with a message; an alarm has no one to tell, so it skips the start and the caller says so in a
 * notification instead.
 */
@Singleton
class SessionStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val todoRepository: TodoRepository,
    private val syncRepository: FirebaseSyncRepository
) {
    enum class Outcome { STARTED, ALREADY_RUNNING, FULL, TOO_LATE }

    /** Starts [todo] as a session under [plan], run under its parent's session if one is going. */
    suspend fun startFromTodo(todo: Todo, plan: TaskTimePlan, now: Long = System.currentTimeMillis()): Outcome {
        val tasks = taskRepository.getVisibleTasksList()
        if (tasks.any { it.originTodoId == todo.id && it.isSessionActive }) return Outcome.ALREADY_RUNNING
        val interruptedGroupId = ancestorSessionToInterrupt(todo, tasks, now)
        // Running under a parent adds no new top-level session, so only a standalone start is capped.
        if (interruptedGroupId == null && taskRepository.activeSessionCount() >= TaskRepository.MAX_ACTIVE_SESSIONS) {
            return Outcome.FULL
        }
        begin(
            Task(
                id = UUID.randomUUID().toString(),
                groupId = UUID.randomUUID().toString(),
                name = todo.title,
                kind = todo.kind,
                taskTypeId = todo.taskTypeId ?: learnedTypeIdForName(todo.title, tasks),
                isRunning = true,
                startTime = now,
                originTodoId = todo.id,
                interruptedGroupId = interruptedGroupId,
                countsForStreak = interruptedGroupId != null,
                timePlan = plan.encode()
            )
        )
        return Outcome.STARTED
    }

    /**
     * Starts [block] as a session named after it, in its kind and type. With [ScheduleBlock.autoStop]
     * the session carries a stop at the block's end, so it ends itself with the block; if that end is
     * already behind us the block is over and nothing starts.
     */
    suspend fun startFromBlock(block: ScheduleBlock, now: Long = System.currentTimeMillis()): Outcome {
        val end = endInstant(block, now)
        if (end <= now) return Outcome.TOO_LATE
        val name = block.title.trim().ifBlank { "Task" }
        val tasks = taskRepository.getVisibleTasksList()
        if (tasks.any { it.isRunning && it.name.trim().equals(name, ignoreCase = true) }) return Outcome.ALREADY_RUNNING
        if (taskRepository.activeSessionCount() >= TaskRepository.MAX_ACTIVE_SESSIONS) return Outcome.FULL
        val plan = if (block.autoStop) TaskTimePlan.of(stopAtMillis = end) else TaskTimePlan.NONE
        begin(
            Task(
                id = UUID.randomUUID().toString(),
                groupId = UUID.randomUUID().toString(),
                name = name,
                kind = block.kind,
                taskTypeId = block.taskTypeId ?: learnedTypeIdForName(name, tasks),
                isRunning = true,
                startTime = now,
                timePlan = plan.encode()
            )
        )
        return Outcome.STARTED
    }

    private suspend fun begin(task: Task) {
        taskRepository.insertTask(task)
        val intent = Intent(context, TaskTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
        syncRepository.pushPendingChanges()
    }

    /** Same walk as TodoViewModel.ancestorSessionToInterrupt: the nearest ancestor with a session
     * going that can be paused for this one, or null for an ordinary top-level session. */
    private suspend fun ancestorSessionToInterrupt(todo: Todo, tasks: List<Task>, now: Long): String? {
        val groupByTodoId = tasks.filter { it.isSessionActive && it.originTodoId != null }.groupBy { it.originTodoId }
        if (groupByTodoId.isEmpty()) return null
        val seen = mutableSetOf(todo.id)
        var parentId = todo.parentTodoId
        while (parentId != null && seen.add(parentId)) {
            val groupId = groupByTodoId[parentId]?.firstOrNull()?.groupId
            if (groupId != null && taskRepository.beginInterruptionOf(groupId, now)) return groupId
            parentId = todoRepository.getTodoById(parentId)?.parentTodoId
        }
        return null
    }

    /** Whatever type tasks named [name] have settled on, so an automatic start is typed the way
     * typing the name would be. */
    private fun learnedTypeIdForName(name: String, tasks: List<Task>): String? {
        val target = name.trim().lowercase()
        if (target.isBlank()) return null
        val sameName = tasks.filter { it.name.trim().lowercase() == target }
        return if (sameName.isEmpty()) null else modalTypeIdFor(sameName)
    }

    companion object {
        /** When [block]'s occurrence on the day of [now] ends. 1440 minutes is the next midnight, which
         * the calendar's lenient hour-of-day (24) lands on. */
        fun endInstant(block: ScheduleBlock, now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, block.endMinuteOfDay / 60)
            set(Calendar.MINUTE, block.endMinuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
