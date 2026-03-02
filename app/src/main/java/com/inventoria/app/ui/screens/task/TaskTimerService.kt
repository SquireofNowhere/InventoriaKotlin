package com.inventoria.app.ui.screens.task

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.inventoria.app.ui.main.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

class TaskTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val binder = TimerBinder()
    
    // Using String ID for persistence and cross-device sync
    private val runningTasks = mutableMapOf<String, Long>()
    private val _taskUpdates = MutableStateFlow<Map<String, Long>>(emptyMap())
    val taskUpdates = _taskUpdates
    
    private val taskNames = mutableMapOf<String, String>()
    
    private var isServiceRunning = false

    inner class TimerBinder : Binder() {
        fun getService(): TaskTimerService = this@TaskTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceRunning) {
            startForegroundService()
            isServiceRunning = true
            startTimerLoop()
        }
        return START_STICKY
    }

    private fun startTimerLoop() {
        serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val updates = runningTasks.mapValues { now - it.value }
                _taskUpdates.value = updates
                updateNotification(updates)
                delay(1000)
            }
        }
    }

    fun startTask(id: String, startTime: Long) {
        runningTasks[id] = startTime
        if (!taskNames.containsKey(id)) {
            taskNames[id] = "Task"
        }
    }
    
    fun updateTaskName(id: String, name: String) {
        taskNames[id] = name
    }

    fun stopTask(id: String) {
        runningTasks.remove(id)
        taskNames.remove(id)
        if (runningTasks.isEmpty()) {
            stopSelf()
        }
    }

    private fun startForegroundService() {
        val notification = createNotification("Task Tracking Active", emptyMap())
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(updates: Map<String, Long>) {
        val count = runningTasks.size
        if (count > 0) {
            val notification = createNotification("$count active task(s) being tracked", updates)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(content: String, updates: Map<String, Long>): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Inventoria Task Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (updates.isNotEmpty()) {
            val inboxStyle = NotificationCompat.InboxStyle()
            updates.forEach { (id, elapsed) ->
                val name = taskNames[id] ?: "Task"
                inboxStyle.addLine("$name: ${formatTime(elapsed)}")
            }
            builder.setStyle(inboxStyle)
        }

        return builder.build()
    }

    private fun formatTime(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "task_tracker_channel"
        const val NOTIFICATION_ID = 1
    }
}
