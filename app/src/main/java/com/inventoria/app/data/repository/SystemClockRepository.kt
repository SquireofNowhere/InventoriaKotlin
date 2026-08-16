package com.inventoria.app.data.repository

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The one thing Android will actually tell us about the user's alarms: when the next one fires,
 * and which app owns it. [owningPackage] is null when the platform doesn't say. */
data class NextAlarm(val triggerTime: Long, val owningPackage: String?)

/**
 * The device's own clock app, as far as it can be reached.
 *
 * Android deliberately offers no read access to alarms or timers -- there is no provider to query,
 * no list to enumerate, and no way to observe a timer counting down. What exists is:
 *
 *  - **Create**: [AlarmClock.ACTION_SET_ALARM] / [AlarmClock.ACTION_SET_TIMER], which the clock app
 *    honours. With EXTRA_SKIP_UI it happens without leaving Inventoria.
 *  - **Read**: [AlarmManager.getNextAlarmClock], and only that -- the *next* alarm across the whole
 *    device, as a timestamp. Nothing about the alarms behind it, and nothing at all about timers.
 *  - **Hand off**: ACTION_SHOW_ALARMS / ACTION_SHOW_TIMERS open the clock app's own lists, which is
 *    the only honest way to offer editing, snoozing or cancelling of something we cannot see.
 *
 * So this is a create-and-hand-off surface with a single readable value, not a mirror of the clock
 * app. Every method returns whether it worked: on a device with no app answering these intents
 * (rare, but a stripped ROM or a work profile can do it) they simply fail, and the UI says so
 * rather than pretending an alarm exists.
 */
@Singleton
class SystemClockRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** When the next device alarm fires, or null if none is set (or the platform withholds it).
     * This is every app's alarms, not just the clock's, and not just ones we created. */
    fun nextAlarm(): NextAlarm? {
        val info = alarmManager?.nextAlarmClock ?: return null
        return NextAlarm(
            triggerTime = info.triggerTime,
            owningPackage = info.showIntent?.creatorPackage
        )
    }

    /** Starts a countdown timer in the clock app. [seconds] must be positive; the clock app caps
     * long values itself. [skipUi] keeps the user here rather than bouncing to the clock. */
    fun startTimer(seconds: Int, label: String, skipUi: Boolean = true): Boolean {
        if (seconds <= 0) return false
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
        return launch(intent)
    }

    /** Sets an alarm for a wall-clock time today or tomorrow -- the clock app decides which, by
     * taking the next occurrence of [hour]:[minute]. */
    fun setAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean = true): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
        return launch(intent)
    }

    /** Opens the clock app's alarm list. The only route to editing or cancelling an alarm we
     * can't see. */
    fun showAlarms(): Boolean = launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    /** Opens the clock app's timer list -- same reasoning as [showAlarms], and the only way to
     * watch or stop a running timer, since nothing about one is readable from here. */
    fun showTimers(): Boolean = launch(Intent(AlarmClock.ACTION_SHOW_TIMERS))

    /** True when something on this device actually answers alarm-clock intents. Checked before
     * offering the controls at all, rather than letting a tap fail silently. */
    fun hasClockApp(): Boolean =
        Intent(AlarmClock.ACTION_SET_TIMER).resolveActivity(context.packageManager) != null

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        // ActivityNotFound is the expected one (no clock app); anything else is equally a
        // "didn't happen", and the caller's job is to say so rather than crash the screen.
        Log.e(TAG, "No app handled ${intent.action}", e)
        false
    }

    companion object {
        private const val TAG = "SystemClockRepository"
    }
}
