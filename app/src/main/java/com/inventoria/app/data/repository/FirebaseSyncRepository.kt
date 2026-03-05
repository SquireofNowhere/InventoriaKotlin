package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.*
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
    private val collectionDao: CollectionDao,
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
        setupNodeSync(
            nodeRef = inventoryRef,
            localFlow = inventoryDao.getAllItems(),
            pushAction = { ref, items -> pushItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullItemsFromFirebase(snapshot) }
        )

        // 2. Task Sync
        val tasksRef = rootRef.child("tasks")
        setupNodeSync(
            nodeRef = tasksRef,
            localFlow = taskDao.getAllTasks(),
            pushAction = { ref, tasks -> pushTasksToFirebase(ref, tasks) },
            pullAction = { snapshot -> pullTasksFromFirebase(snapshot) }
        )

        // 3. Collections Sync
        val collectionsRef = rootRef.child("collections")
        setupNodeSync(
            nodeRef = collectionsRef,
            localFlow = collectionDao.getAllCollections(),
            pushAction = { ref, colls -> pushCollectionsToFirebase(ref, colls) },
            pullAction = { snapshot -> pullCollectionsFromFirebase(snapshot) }
        )

        // 4. Collection Items Sync (Junction Table)
        val collectionItemsRef = rootRef.child("collection_items")
        setupNodeSync(
            nodeRef = collectionItemsRef,
            localFlow = collectionDao.getAllCollectionItemsFlow(),
            pushAction = { ref, items -> pushCollectionItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullCollectionItemsFromFirebase(snapshot) }
        )
    }

    private fun <T> setupNodeSync(
        nodeRef: DatabaseReference,
        localFlow: Flow<List<T>>,
        pushAction: suspend (DatabaseReference, List<T>) -> Unit,
        pullAction: suspend (DataSnapshot) -> Unit
    ) {
        // Local to Firebase
        repositoryScope.launch {
            localFlow.distinctUntilChanged()
                .collect { data ->
                    if (!isUpdatingFromFirebase) {
                        pushAction(nodeRef, data)
                    }
                }
        }

        // Firebase to Local
        nodeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    pullAction(snapshot)
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
                pushItemsToFirebase(ref.child("inventory"), inventoryDao.getAllItems().first())
                
                // Tasks
                val tasksSnapshot = ref.child("tasks").get().await()
                pullTasksFromFirebase(tasksSnapshot)
                pushTasksToFirebase(ref.child("tasks"), taskDao.getAllTasks().first())

                // Collections
                val collSnapshot = ref.child("collections").get().await()
                pullCollectionsFromFirebase(collSnapshot)
                pushCollectionsToFirebase(ref.child("collections"), collectionDao.getAllCollections().first())

                // Collection Items
                val ciSnapshot = ref.child("collection_items").get().await()
                pullCollectionItemsFromFirebase(ciSnapshot)
                pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getItemsForSync())
                
                _syncStatus.value = SyncStatus.Synced
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    // --- Inventory Sync ---
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
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryItem::class.java) }
            val cloudIds = cloudItems.map { it.id }.toSet()
            
            isUpdatingFromFirebase = true
            
            // Mirroring: Delete local items not in cloud
            val localItems = inventoryDao.getAllItems().first()
            localItems.forEach { local ->
                if (!cloudIds.contains(local.id)) {
                    inventoryDao.deleteItemById(local.id)
                }
            }
            
            if (cloudItems.isNotEmpty()) {
                inventoryDao.insertItems(cloudItems)
            }
            
            delay(500)
            isUpdatingFromFirebase = false
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull items failed")
        }
    }

    // --- Task Sync ---
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
            val cloudTasks = snapshot.children.mapNotNull { it.getValue(Task::class.java) }
            val cloudIds = cloudTasks.map { it.id }.toSet()

            isUpdatingFromFirebase = true
            
            // Mirroring: Delete local tasks not in cloud
            val localTasks = taskDao.getAllTasks().first()
            localTasks.forEach { local ->
                if (!cloudIds.contains(local.id)) {
                    taskDao.deleteTaskById(local.id)
                }
            }

            if (cloudTasks.isNotEmpty()) {
                taskDao.insertTasks(cloudTasks)
            }
            
            delay(500)
            isUpdatingFromFirebase = false
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull tasks failed")
        }
    }

    // --- Collections Sync ---
    private suspend fun pushCollectionsToFirebase(ref: DatabaseReference, collections: List<InventoryCollection>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val collsMap = collections.associateBy { it.id.toString() }
            ref.setValue(collsMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push collections failed")
        }
    }

    private suspend fun pullCollectionsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudColls = snapshot.children.mapNotNull { it.getValue(InventoryCollection::class.java) }
            val cloudIds = cloudColls.map { it.id }.toSet()

            isUpdatingFromFirebase = true
            
            // Mirroring: Delete local collections not in cloud
            val localColls = collectionDao.getAllCollections().first()
            localColls.forEach { local ->
                if (!cloudIds.contains(local.id)) {
                    collectionDao.deleteCollection(local)
                }
            }

            if (cloudColls.isNotEmpty()) {
                cloudColls.forEach { collectionDao.insertCollection(it) }
            }
            
            delay(500)
            isUpdatingFromFirebase = false
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull collections failed")
        }
    }

    // --- Collection Items Sync ---
    private suspend fun pushCollectionItemsToFirebase(ref: DatabaseReference, items: List<InventoryCollectionItem>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val itemsMap = items.associateBy { "${it.collectionId}_${it.itemId}" }
            ref.setValue(itemsMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push collection items failed")
        }
    }

    private suspend fun pullCollectionItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryCollectionItem::class.java) }
            val cloudCompositeKeys = cloudItems.map { "${it.collectionId}_${it.itemId}" }.toSet()

            isUpdatingFromFirebase = true
            
            // Mirroring: Delete local links not in cloud
            val localItems = collectionDao.getItemsForSync()
            localItems.forEach { local ->
                if (!cloudCompositeKeys.contains("${local.collectionId}_${local.itemId}")) {
                    collectionDao.deleteCollectionItem(local)
                }
            }

            if (cloudItems.isNotEmpty()) {
                cloudItems.forEach { collectionDao.insertCollectionItem(it) }
            }
            
            delay(500)
            isUpdatingFromFirebase = false
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull collection items failed")
        }
    }
}
