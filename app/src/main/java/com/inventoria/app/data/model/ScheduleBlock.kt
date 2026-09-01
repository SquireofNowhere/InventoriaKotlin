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
 * Deliberately cosmetic: a block scores nothing, starts no session and is linked to no task or
 * todo. It says what an hour was meant to be for, and that is all.
 *
 * [dayStart] is a start-of-day timestamp in the device's zone, the same convention as
 * Todo.deadline, and the times are minutes since midnight, the same as Todo.deadlineMinuteOfDay.
 * Splitting date from time keeps a block anchored to its calendar day and lets [repeatWeekly] be
 * a simple "same weekday, same minutes" rule with no timezone arithmetic.
 *
 * [kind] is only a colour here -- the palette every other tinted thing in the app already uses --
 * not a productivity score. Picking a kind for a block just says "this is what the hour is for".
 */
@Entity
data class ScheduleBlock(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("kind") @set:PropertyName("kind") var kind: TaskKind = TaskKind.GRAPHITE,
    /** Start-of-day millis of the day this block was created for. With [repeatWeekly] set it is
     * also the first day the block shows on -- never earlier. */
    @get:PropertyName("dayStart") @set:PropertyName("dayStart") var dayStart: Long = 0L,
    /** Minutes since midnight, 0..1439. */
    @get:PropertyName("startMinuteOfDay") @set:PropertyName("startMinuteOfDay") var startMinuteOfDay: Int = 0,
    /** Minutes since midnight, 1..1440 (1440 = the very end of the day). Always > start. */
    @get:PropertyName("endMinuteOfDay") @set:PropertyName("endMinuteOfDay") var endMinuteOfDay: Int = 60,
    @get:PropertyName("repeatWeekly") @set:PropertyName("repeatWeekly") var repeatWeekly: Boolean = false,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
) {
    /** Whether this block shows on [day] (a start-of-day timestamp): its own day, or -- when
     * repeating -- any later day falling on the same weekday. Not a bean getter, so Firebase's
     * mapper never mistakes it for a field. */
    fun occursOn(day: Long): Boolean {
        if (day == dayStart) return true
        if (!repeatWeekly || day < dayStart) return false
        val own = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
        val target = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK)
        return own == target
    }
}
