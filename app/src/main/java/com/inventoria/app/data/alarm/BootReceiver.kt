package com.inventoria.app.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-arms todo alarms after a reboot -- by doing nothing.
 *
 * AlarmManager forgets every alarm when the device restarts. Being registered for BOOT_COMPLETED
 * makes the system start this app's process to deliver the broadcast, and starting the process
 * runs InventoriaApplication.onCreate, which starts [TodoAlarmScheduler], which reads the Todo
 * table and sets every pending alarm again. The work is all there; this class only has to exist.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "${intent.action}: process up, TodoAlarmScheduler re-arms from the table")
        }
    }
}
