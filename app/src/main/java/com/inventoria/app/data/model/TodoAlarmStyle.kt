package com.inventoria.app.data.model

/**
 * How a todo alarm makes itself known when it fires. Chosen in Settings, applied to every todo
 * alarm on this device.
 *
 * Two styles rather than one adjustable one because Android notification channels are fixed once
 * created: importance, sound and vibration cannot be changed by the app afterwards. So each style
 * is its own channel (see TodoAlarmReceiver) and this picks which one a given alarm posts to.
 */
enum class TodoAlarmStyle(val title: String, val description: String) {
    ALARM("Alarm", "Alarm sound and vibration, shows on the lock screen -- hard to miss"),
    NOTIFICATION("Notification", "A normal notification with the default sound");

    companion object {
        fun fromName(name: String): TodoAlarmStyle =
            try { valueOf(name) } catch (e: IllegalArgumentException) { ALARM }
    }
}
