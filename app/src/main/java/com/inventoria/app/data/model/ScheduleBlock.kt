package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName
import java.util.Calendar

/**
 * A stretch of a day the user has *designated* for something -- "06:00-07:00 Gym", "09:00-12:00
 * Deep work". The Schedule segment draws these next to tracked [Task] segments, which are what the
 * time was actually *used* for, so plan and reality sit side by side.
 *
 * Deliberately cosmetic by default: a block scores nothing, starts no session on its own and is
 * linked to no task or todo. It says what an hour was meant to be for, and that is all -- unless
 * [autoStart] is switched on, which makes the block begin tracking when it starts (and, with
 * [autoStop], end it when the block ends). The one thing it always carries forward is [taskTypeId]:
 * when a session is started from a block, the task lands typed the way the block says, so planned
 * hours count under the same activity as tracked ones instead of guessing from the name.
 *
 * [dayStart] is a start-of-day timestamp in the device's zone, the same convention as
 * Todo.deadline, and the times are minutes since midnight, the same as Todo.deadlineMinuteOfDay.
 * Splitting date from time keeps a block anchored to its calendar day and lets [repeatDaily] and
 * [repeatWeekly] be simple "every day" / "same weekday" rules, both at the same minutes, with no
 * timezone arithmetic.
 *
 * [kind] is only a colour here -- the palette every other tinted thing in the app already uses --
 * not a productivity score. Picking a kind for a block just says "this is what the hour is for".
 */
@Entity
data class ScheduleBlock(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    /** The activity this block is set aside for, same tier as Todo.taskTypeId and Task.taskTypeId.
     * Null is "no particular type": a session started from the block then falls back to whatever
     * type the title has already settled on, exactly as a todo without a type does. */
    @get:PropertyName("taskTypeId") @set:PropertyName("taskTypeId") var taskTypeId: String? = null,
    /** Start-of-day millis of the day this block was created for. With [repeatDaily] or
     * [repeatWeekly] set it is also the first day the block shows on -- never earlier. */
    @get:PropertyName("dayStart") @set:PropertyName("dayStart") var dayStart: Long = 0L,
    /** Minutes since midnight, 0..1439. */
    @get:PropertyName("startMinuteOfDay") @set:PropertyName("startMinuteOfDay") var startMinuteOfDay: Int = 0,
    /** Minutes since midnight, 1..1440 (1440 = the very end of the day). Always > start. */
    @get:PropertyName("endMinuteOfDay") @set:PropertyName("endMinuteOfDay") var endMinuteOfDay: Int = 60,
    @get:PropertyName("repeatWeekly") @set:PropertyName("repeatWeekly") var repeatWeekly: Boolean = false,
    /** Repeats every day from [dayStart] on. Wins over [repeatWeekly] if a stray sync ever leaves
     * both set -- the editor only ever writes one of the two. */
    @get:PropertyName("repeatDaily") @set:PropertyName("repeatDaily") var repeatDaily: Boolean = false,
    /** The last day (inclusive, start-of-day) this block shows on, or null for no end. Removing a
     * repeating block from today on sets this to the day before instead of deleting the row, so the
     * days already gone by keep showing what the schedule used to be. See [historyEnd]. */
    @get:PropertyName("endDay") @set:PropertyName("endDay") var endDay: Long? = null,
    /** Start tracking a session by itself when this block begins, named, kinded and typed like the
     * block. Off by default: a block stays a plan unless asked to do more. */
    @get:PropertyName("autoStart") @set:PropertyName("autoStart") var autoStart: Boolean = false,
    /** Stop that session by itself when the block ends. Only meaningful with [autoStart]. */
    @get:PropertyName("autoStop") @set:PropertyName("autoStop") var autoStop: Boolean = false,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
) {
    /** Whether this block shows on [day] (a start-of-day timestamp): its own day, or -- when
     * repeating -- any later day (daily) or later day on the same weekday (weekly). Not a bean
     * getter, so Firebase's mapper never mistakes it for a field. */
    fun occursOn(day: Long): Boolean {
        if (endDay != null && day > endDay!!) return false
        if (day == dayStart) return true
        if (day < dayStart) return false
        if (repeatDaily) return true
        if (!repeatWeekly) return false
        val own = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
        val target = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK)
        return own == target
    }

    /**
     * The day to end this block on so that everything that has already begun stays on the calendar,
     * or null when there is nothing to keep (it has not started yet) or nothing to end (it does not
     * repeat, or already stops by then). [todayStart] is the start of today and [nowMinute] the
     * minutes since midnight: today's occurrence counts as history once its start time has passed.
     * Not a bean getter, so Firebase's mapper never mistakes it for a field.
     */
    fun historyEnd(todayStart: Long, nowMinute: Int): Long? {
        if (!repeatDaily && !repeatWeekly) return null
        val keepsToday = occursOn(todayStart) && startMinuteOfDay <= nowMinute
        val last = if (keepsToday) todayStart else Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        if (last < dayStart) return null
        val current = endDay
        return if (current != null && current <= last) null else last
    }

    /** Whether any occurrence of this block has already begun -- the case where deleting it would
     * rewrite the past. */
    fun hasHistory(todayStart: Long, nowMinute: Int): Boolean =
        dayStart < todayStart || (dayStart == todayStart && startMinuteOfDay <= nowMinute)

    /**
     * The first moment after [now] this block begins, when it is set to start tracking by itself --
     * null when it is not, is deleted, or has no occurrence left. Occurrences are found on the
     * calendar day by day: a weekly block is at most a week away, so a week is as far as it looks.
     * Not a bean getter, so Firebase's mapper never mistakes it for a field.
     */
    fun nextStartAfter(now: Long): Long? {
        if (isDeleted || !autoStart) return null
        val day = Calendar.getInstance().apply {
            timeInMillis = maxOf(now, dayStart)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(if (repeatDaily || repeatWeekly) 8 else 1) {
            if (occursOn(day.timeInMillis)) {
                val start = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, startMinuteOfDay / 60)
                    set(Calendar.MINUTE, startMinuteOfDay % 60)
                }.timeInMillis
                if (start > now) return start
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
