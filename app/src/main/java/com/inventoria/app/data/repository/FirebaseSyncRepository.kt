package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
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
        val ref = firebaseDatabase.getReference("users").child(userId).child("inventory")
        userRef = ref

        // Check if database is connected
        val connectedRef = firebaseDatabase.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase Connection Status: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 1. Push Local Changes to Firebase
        repositoryScope.launch {
            inventoryDao.getAllItems()
                .distinctUntilChanged()
                .collect { items ->
                    if (!isUpdatingFromFirebase) {
                        Log.d(TAG, "Local change detected. Items count: ${items.size}. Pushing to cloud...")
                        pushToFirebase(ref, items)
                    } else {
                        Log.d(TAG, "Local change ignored (currently pulling from cloud)")
                    }
                }
        }

        // 2. Pull Cloud Changes to Local Room
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Cloud data changed. Snapshot size: ${snapshot.childrenCount} items")
                repositoryScope.launch {
                    pullFromFirebase(snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _syncStatus.value = SyncStatus.Error(error.message)
                Log.e(TAG, "Firebase pull error: ${error.message} (Code: ${error.code})")
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
                val snapshot = ref.get().await()
                pullFromFirebase(snapshot)
                
                val localItems = inventoryDao.getAllItems().first()
                pushToFirebase(ref, localItems)
                
                _syncStatus.value = SyncStatus.Synced
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    private suspend fun pushToFirebase(ref: DatabaseReference, items: List<InventoryItem>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val itemsMap = items.associateBy { it.id.toString() }
            ref.setValue(itemsMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push failed")
        }
    }

    private suspend fun pullFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudItems = mutableListOf<InventoryItem>()
            
            snapshot.children.forEach { child ->
                child.getValue(InventoryItem::class.java)?.let { item ->
                    cloudItems.add(item)
                }
            }

            if (cloudItems.isNotEmpty()) {
                isUpdatingFromFirebase = true
                inventoryDao.insertItems(cloudItems)
                delay(800)
                isUpdatingFromFirebase = false
            }
            
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull failed")
        }
    }
}
