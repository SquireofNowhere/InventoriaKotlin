package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.InventoryItem
import com.inventoria.app.data.model.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Synced : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

@Singleton
class FirebaseSyncRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val taskDao: TaskDao,
    private val firebaseDatabase: FirebaseDatabase,
    private val authRepository: FirebaseAuthRepository
) {
    private val TAG = "FirebaseSync"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var isUpdatingFromFirebase = false
    private var userRef: DatabaseReference? = null

    /**
     * Starts the bi-directional synchronization process.
     */
    fun startSync() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.w(TAG, "Cannot start sync: User not authenticated")
            return
        }

        Log.d(TAG, "Starting sync for user: $userId")
        val rootRef = firebaseDatabase.getReference("users").child(userId)
        userRef = rootRef

        // Check if database is connected
        val connectedRef = firebaseDatabase.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase Connection Status: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 1. Inventory Sync
        val inventoryRef = rootRef.child("inventory")
        repositoryScope.launch {
            inventoryDao.getAllItems()
                .distinctUntilChanged()
                .collect { items ->
                    if (!isUpdatingFromFirebase) {
                        pushItemsToFirebase(inventoryRef, items)
                    }
                }
        }

        inventoryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    pullItemsFromFirebase(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _syncStatus.value = SyncStatus.Error(error.message)
            }
        })

        // 2. Task Sync
        val tasksRef = rootRef.child("tasks")
        repositoryScope.launch {
            taskDao.getAllTasks()
                .distinctUntilChanged()
                .collect { tasks ->
                    if (!isUpdatingFromFirebase) {
                        pushTasksToFirebase(tasksRef, tasks)
                    }
                }
        }

        tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    pullTasksFromFirebase(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _syncStatus.value = SyncStatus.Error(error.message)
            }
        })
    }

    /**
     * Manually triggers a full synchronization.
     */
    fun triggerFullSync() {
        Log.d(TAG, "Manual sync triggered")
        val ref = userRef ?: return
        repositoryScope.launch {
            try {
                _syncStatus.value = SyncStatus.Syncing
                
                // Inventory
                val invSnapshot = ref.child("inventory").get().await()
                pullItemsFromFirebase(invSnapshot)
                val localItems = inventoryDao.getAllItems().first()
                pushItemsToFirebase(ref.child("inventory"), localItems)
                
                // Tasks
                val tasksSnapshot = ref.child("tasks").get().await()
                pullTasksFromFirebase(tasksSnapshot)
                val localTasks = taskDao.getAllTasks().first()
                pushTasksToFirebase(ref.child("tasks"), localTasks)
                
                _syncStatus.value = SyncStatus.Synced
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    private suspend fun pushItemsToFirebase(ref: DatabaseReference, items: List<InventoryItem>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val itemsMap = items.associateBy { it.id.toString() }
            ref.setValue(itemsMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push items failed")
        }
    }

    private suspend fun pullItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudItems = mutableListOf<InventoryItem>()
            snapshot.children.forEach { child ->
                child.getValue(InventoryItem::class.java)?.let { cloudItems.add(it) }
            }

            if (cloudItems.isNotEmpty()) {
                isUpdatingFromFirebase = true
                inventoryDao.insertItems(cloudItems)
                delay(800)
                isUpdatingFromFirebase = false
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull items failed")
        }
    }

    private suspend fun pushTasksToFirebase(ref: DatabaseReference, tasks: List<Task>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val tasksMap = tasks.associateBy { it.id }
            ref.setValue(tasksMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push tasks failed")
        }
    }

    private suspend fun pullTasksFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudTasks = mutableListOf<Task>()
            snapshot.children.forEach { child ->
                child.getValue(Task::class.java)?.let { cloudTasks.add(it) }
            }

            if (cloudTasks.isNotEmpty()) {
                isUpdatingFromFirebase = true
                taskDao.insertTasks(cloudTasks)
                delay(800)
                isUpdatingFromFirebase = false
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull tasks failed")
        }
    }
}
