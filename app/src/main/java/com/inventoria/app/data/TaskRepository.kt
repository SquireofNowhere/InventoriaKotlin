package com.inventoria.app.data

import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getRunningTasks(): Flow<List<Task>> = taskDao.getRunningTasks()
    
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }
}
