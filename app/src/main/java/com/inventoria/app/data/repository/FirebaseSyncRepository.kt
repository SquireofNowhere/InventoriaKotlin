package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val taskDao: TaskDao,
    private val collectionDao: CollectionDao,
    private val itemLinkDao: ItemLinkDao,
    private val firebaseDatabase: FirebaseDatabase,
    private val authRepository: FirebaseAuthRepository,
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "FirebaseSync"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    private val syncIgnoreCount = AtomicInteger(0)
    private var userRef: DatabaseReference? = null
    private var syncJobs = mutableListOf<Job>()

    fun isSyncing(): Boolean = syncIgnoreCount.get() > 0

    fun startSync() {
        repositoryScope.launch {
            settingsRepository.manualSyncId.collect { manualId ->
                val userId = manualId ?: authRepository.getCurrentUserId() ?: return@collect
                restartSyncForUser(userId)
            }
        }
    }

    private fun restartSyncForUser(userId: String) {
        // Cancel existing sync jobs
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
        
        Log.d(TAG, "Starting sync for user: $userId")
        
        val rootRef = firebaseDatabase.getReference("users").child(userId)
        userRef = rootRef

        // Monitor connection status
        firebaseDatabase.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase Connection Status: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sync Items
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("items"),
            localFlow = inventoryDao.getAllItemsForSync(),
            pushAction = { ref, items -> pushItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullItemsFromFirebase(snapshot) }
        ))

        // Sync Item Links
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("item_links"),
            localFlow = itemLinkDao.getAllLinksFlow(),
            pushAction = { ref, links -> pushLinksToFirebase(ref, links) },
            pullAction = { snapshot -> pullLinksFromFirebase(snapshot) }
        ))

        // Sync Tasks
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("tasks"),
            localFlow = taskDao.getAllTasksForSync(),
            pushAction = { ref, tasks -> pushTasksToFirebase(ref, tasks) },
            pullAction = { snapshot -> pullTasksFromFirebase(snapshot) }
        ))

        // Sync Collections
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("collections"),
            localFlow = collectionDao.getAllCollections(),
            pushAction = { ref, colls -> pushCollectionsToFirebase(ref, colls) },
            pullAction = { snapshot -> pullCollectionsFromFirebase(snapshot) }
        ))

        // Sync Collection Items
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("collection_items"),
            localFlow = collectionDao.getAllCollectionItemsFlow(),
            pushAction = { ref, items -> pushCollectionItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullCollectionItemsFromFirebase(snapshot) }
        ))
        
        syncJobs.add(setupSettingsSync(rootRef.child("settings")))
    }

    private fun <T> setupNodeSync(
        nodeRef: DatabaseReference,
        localFlow: Flow<List<T>>,
        pushAction: suspend (DatabaseReference, List<T>) -> Unit,
        pullAction: suspend (DataSnapshot) -> Unit
    ): Job {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        
        scope.launch {
            localFlow.distinctUntilChanged().collect { list ->
                if (syncIgnoreCount.get() == 0) {
                    pushAction(nodeRef, list)
                }
            }
        }

        val firebaseFlow = callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
                override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            }
            nodeRef.addValueEventListener(listener)
            awaitClose { nodeRef.removeEventListener(listener) }
        }

        scope.launch {
            firebaseFlow.collect { snapshot ->
                pullAction(snapshot)
            }
        }
        
        return job
    }

    private fun setupSettingsSync(settingsRef: DatabaseReference): Job {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        
        scope.launch {
            settingsRepository.customUsername.distinctUntilChanged().collect { username ->
                if (syncIgnoreCount.get() == 0) {
                    settingsRef.child("custom_username").setValue(username)
                }
            }
        }

        settingsRef.child("custom_username").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudUsername = snapshot.getValue(String::class.java)
                scope.launch {
                    syncIgnoreCount.incrementAndGet()
                    settingsRepository.saveCustomUsername(cloudUsername)
                    syncIgnoreCount.decrementAndGet()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        
        return job
    }

    private suspend fun pushItemsToFirebase(ref: DatabaseReference, items: List<InventoryItem>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val updates = items.associateBy { it.id.toString() }
            ref.setValue(updates).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Push items failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun pullItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryItem::class.java) }
            
            // Only overwrite local if cloud version is newer
            val itemsToInsert = cloudItems.filter { cloudItem ->
                val localItem = inventoryDao.getItemById(cloudItem.id)
                localItem == null || cloudItem.updatedAt > localItem.updatedAt
            }
            
            if (itemsToInsert.isNotEmpty()) {
                inventoryDao.insertItems(itemsToInsert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull items failed", e)
        } finally {
            delay(1000)
            syncIgnoreCount.decrementAndGet()
        }
    }

    private suspend fun pushLinksToFirebase(ref: DatabaseReference, links: List<ItemLink>) {
        try {
            val updates = links.associateBy { "${it.followerId}_${it.leaderId}" }
            ref.setValue(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Push links failed", e)
        }
    }

    private suspend fun pullLinksFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudLinks = snapshot.children.mapNotNull { it.getValue(ItemLink::class.java) }
            
            cloudLinks.forEach { cloudLink ->
                val localLink = itemLinkDao.getLink(cloudLink.followerId, cloudLink.leaderId)
                if (localLink == null || cloudLink.updatedAt > localLink.updatedAt) {
                    itemLinkDao.insertLink(cloudLink)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull links failed", e)
        } finally {
            delay(1000)
            syncIgnoreCount.decrementAndGet()
        }
    }

    private suspend fun pushTasksToFirebase(ref: DatabaseReference, tasks: List<Task>) {
        try {
            val updates = tasks.associateBy { it.id }
            ref.setValue(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Push tasks failed", e)
        }
    }

    private suspend fun pullTasksFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudTasks = snapshot.children.mapNotNull { it.getValue(Task::class.java) }
            
            // Only overwrite local if cloud version is newer
            val tasksToInsert = cloudTasks.filter { cloudTask ->
                val localTask = taskDao.getTaskById(cloudTask.id)
                localTask == null || cloudTask.updatedAt > localTask.updatedAt
            }
            
            if (tasksToInsert.isNotEmpty()) {
                taskDao.insertTasks(tasksToInsert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull tasks failed", e)
        } finally {
            delay(1000)
            syncIgnoreCount.decrementAndGet()
        }
    }

    private suspend fun pushCollectionsToFirebase(ref: DatabaseReference, collections: List<InventoryCollection>) {
        try {
            val updates = collections.associateBy { it.id.toString() }
            ref.setValue(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Push collections failed", e)
        }
    }

    private suspend fun pullCollectionsFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudColls = snapshot.children.mapNotNull { it.getValue(InventoryCollection::class.java) }
            
            cloudColls.forEach { cloudColl ->
                val localColl = collectionDao.getCollectionById(cloudColl.id)
                if (localColl == null || cloudColl.updatedAt > localColl.updatedAt) {
                    collectionDao.insertCollection(cloudColl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull collections failed", e)
        } finally {
            delay(1000)
            syncIgnoreCount.decrementAndGet()
        }
    }

    private suspend fun pushCollectionItemsToFirebase(ref: DatabaseReference, items: List<InventoryCollectionItem>) {
        try {
            val updates = items.associateBy { "${it.collectionId}_${it.itemId}" }
            ref.setValue(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Push collection items failed", e)
        }
    }

    private suspend fun pullCollectionItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryCollectionItem::class.java) }
            
            cloudItems.forEach { cloudItem ->
                val localItem = collectionDao.getCollectionItem(cloudItem.collectionId, cloudItem.itemId)
                if (localItem == null || cloudItem.updatedAt > localItem.updatedAt) {
                    collectionDao.insertCollectionItem(cloudItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull collection items failed", e)
        } finally {
            delay(1000)
            syncIgnoreCount.decrementAndGet()
        }
    }

    fun triggerFullSync() {
        Log.d(TAG, "Manual sync triggered")
        repositoryScope.launch {
            syncOnAppOpen()
        }
    }

    suspend fun syncOnAppOpen() {
        val userId = authRepository.getOrCreateUserId()
        val ref = firebaseDatabase.getReference("users").child(userId)
        userRef = ref

        try {
            _syncStatus.value = SyncStatus.Syncing
            Log.d(TAG, "Performing pull-first sync on app open")

            // 1. Pull first to overwrite stale local state
            pullItemsFromFirebase(ref.child("items").get().await())
            pullLinksFromFirebase(ref.child("item_links").get().await())
            pullTasksFromFirebase(ref.child("tasks").get().await())
            pullCollectionsFromFirebase(ref.child("collections").get().await())
            pullCollectionItemsFromFirebase(ref.child("collection_items").get().await())

            // 2. Then push local changes
            pushItemsToFirebase(ref.child("items"), inventoryDao.getAllItemsForSyncList())
            pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getAllLinksList())
            pushTasksToFirebase(ref.child("tasks"), taskDao.getAllTasksForSyncList())
            pushCollectionsToFirebase(ref.child("collections"), collectionDao.getAllCollectionsList())
            pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getAllCollectionItemsList())

            _syncStatus.value = SyncStatus.Synced
            Log.d(TAG, "App open sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "App open sync failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }
}
