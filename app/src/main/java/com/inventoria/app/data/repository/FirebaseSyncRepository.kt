package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.model.InventoryItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var isUpdatingFromFirebase = false

    /**
     * Starts the bi-directional synchronization process.
     */
    fun startSync() {
        val userId = authRepository.getCurrentUserId() ?: return
        val userRef = firebaseDatabase.getReference("users").child(userId).child("inventory")

        // 1. Push Local Changes to Firebase
        repositoryScope.launch {
            inventoryDao.getAllItems()
                .distinctUntilChanged()
                .collect { items ->
                    if (!isUpdatingFromFirebase) {
                        pushToFirebase(userRef, items)
                    }
                }
        }

        // 2. Pull Cloud Changes to Local Room
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    pullFromFirebase(snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _syncStatus.value = SyncStatus.Error(error.message)
                Log.e("FirebaseSync", "Firebase sync cancelled: ${error.message}")
            }
        })
    }

    private suspend fun pushToFirebase(ref: DatabaseReference, items: List<InventoryItem>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            // Convert list to map for easier Firebase storage (id as key)
            val itemsMap = items.associateBy { it.id.toString() }
            ref.setValue(itemsMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown push error")
            Log.e("FirebaseSync", "Error pushing to Firebase", e)
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
                delay(500) // Small delay to allow Room to settle and avoid immediate re-push
                isUpdatingFromFirebase = false
            }
            
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown pull error")
            Log.e("FirebaseSync", "Error pulling from Firebase", e)
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result, null)
                } else {
                    continuation.resumeWithException(task.exception ?: Exception("Unknown Firebase error"))
                }
            }
        }
    }
}
