package com.inventoria.app.data.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inventoria.app.data.TaskRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.ui.screens.task.TaskTimerService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: FirebaseSyncRepository,
    private val taskRepository: TaskRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            syncRepository.syncOnAppOpen()
            
            // If we synced and found running tasks, ensure the timer service is active
            val tasks = taskRepository.getVisibleTasks().firstOrNull()
            if (tasks?.any { it.isRunning } == true) {
                val intent = Intent(context, TaskTimerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        // In Android 12+, background starts might fail if not exempt, fallback or ignore
                        e.printStackTrace()
                    }
                } else {
                    context.startService(intent)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
