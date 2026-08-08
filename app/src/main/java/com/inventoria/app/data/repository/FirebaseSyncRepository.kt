package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
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

    // Deletes with no isDirty tombstone (collections, links) go local-delete-then-remove-remote.
    // The live Firebase listener fires on every individual removal as it lands server-side, each
    // time delivering a snapshot that still contains sibling rows whose own removal hasn't landed
    // yet -- with the local row already gone, the pull logic reads that as "new data, insert it"
    // and resurrects it. Tracking in-flight deletes here lets pull skip them until the removal
    // actually completes. See ErrorLog.md #30.
    private val pendingCollectionDeletes = ConcurrentHashMap.newKeySet<Long>()
    private val pendingLinkDeletes = ConcurrentHashMap.newKeySet<String>()

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
            localFlow = inventoryDao.getDirtyItemsFlow(),
            pushAction = { ref, items -> pushItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullItemsFromFirebase(snapshot) }
        ))

        // Sync Item Links
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("item_links"),
            localFlow = itemLinkDao.getDirtyLinksFlow(),
            pushAction = { ref, links -> pushLinksToFirebase(ref, links) },
            pullAction = { snapshot -> pullLinksFromFirebase(snapshot) }
        ))

        // Sync Tasks
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("tasks"),
            localFlow = taskDao.getDirtyTasksFlow(),
            pushAction = { ref, tasks -> pushTasksToFirebase(ref, tasks) },
            pullAction = { snapshot -> pullTasksFromFirebase(snapshot) }
        ))

        // Sync Collections
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("collections"),
            localFlow = collectionDao.getDirtyCollectionsFlow(),
            pushAction = { ref, colls -> pushCollectionsToFirebase(ref, colls) },
            pullAction = { snapshot -> pullCollectionsFromFirebase(snapshot) }
        ))

        // Sync Collection Items
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("collection_items"),
            localFlow = collectionDao.getDirtyCollectionItemsFlow(),
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
            try {
                firebaseFlow.collect { snapshot ->
                    pullAction(snapshot)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listener failed for ${nodeRef.path}", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync listener failed")
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
                    try {
                        settingsRepository.saveCustomUsername(cloudUsername)
                    } finally {
                        withContext(NonCancellable) {
                            syncIgnoreCount.decrementAndGet()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        
        return job
    }

    private suspend fun pushItemsToFirebase(ref: DatabaseReference, items: List<InventoryItem>) {
        if (items.isEmpty()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val updates = items.associate { it.id.toString() to it }
            ref.updateChildren(updates).await()
            inventoryDao.markItemsClean(items.map { it.id })
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
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    private suspend fun pushLinksToFirebase(ref: DatabaseReference, links: List<ItemLink>) {
        // Both the reactive dirty-flow push and TaskTimerService's periodic triggerFullSync()
        // (every 30s while a task is running) push whatever the local table currently holds.
        // If either reads a link a moment before deleteLinkRemote's local delete removes it,
        // it would faithfully re-upload the link to Firebase, undoing the delete outright --
        // a different race than the pull-side one pendingLinkDeletes was originally added for
        // (see ErrorLog.md #30/#32). Filtering here closes it at the source.
        val toPush = links.filterNot { "${it.followerId}_${it.leaderId}" in pendingLinkDeletes }
        if (toPush.isEmpty()) return
        try {
            val updates = toPush.associate { "${it.followerId}_${it.leaderId}" to it }
            ref.updateChildren(updates).await()
            itemLinkDao.markLinksClean(toPush)
        } catch (e: Exception) {
            Log.e(TAG, "Push links failed", e)
        }
    }

    private suspend fun pullLinksFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudLinks = snapshot.children.mapNotNull { it.getValue(ItemLink::class.java) }
            
            cloudLinks.forEach { cloudLink ->
                if ("${cloudLink.followerId}_${cloudLink.leaderId}" in pendingLinkDeletes) return@forEach
                val localLink = itemLinkDao.getLink(cloudLink.followerId, cloudLink.leaderId)
                if (localLink == null || cloudLink.updatedAt > localLink.updatedAt) {
                    itemLinkDao.insertLink(cloudLink)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull links failed", e)
        } finally {
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    private suspend fun pushTasksToFirebase(ref: DatabaseReference, tasks: List<Task>) {
        if (tasks.isEmpty()) return
        try {
            val updates = tasks.associate { it.id to it }
            ref.updateChildren(updates).await()
            taskDao.markTasksClean(tasks.map { it.id })
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
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    private suspend fun pushCollectionsToFirebase(ref: DatabaseReference, collections: List<InventoryCollection>) {
        // See pushLinksToFirebase for why: TaskTimerService's periodic triggerFullSync() (every
        // 30s while a task is running) reads and re-pushes every local collection regardless of
        // an in-flight delete, so a stale read caught a moment before deleteCollectionRemote's
        // local delete would re-upload the collection to Firebase and undo the delete.
        val toPush = collections.filterNot { it.id in pendingCollectionDeletes }
        if (toPush.isEmpty()) return
        try {
            val updates = toPush.associate { it.id.toString() to it }
            ref.updateChildren(updates).await()
            collectionDao.markCollectionsClean(toPush.map { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "Push collections failed", e)
        }
    }

    private suspend fun pullCollectionsFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()

            snapshot.children.forEach { child ->
                val key = child.key?.toLongOrNull() ?: return@forEach
                if (key == 0L) {
                    // Pre-autoGenerate builds always wrote new collections as id=0, so this key
                    // holds stale, unaddressable data: since 0 is Room's "generate a new id"
                    // sentinel, re-inserting it here would create a brand new local+cloud row
                    // every single time this listener fires (it fired on its own writes too),
                    // which is exactly what produced runaway duplicate "collections" -- see
                    // ErrorLog.md #27. Removing it is a one-time self-heal.
                    child.ref.removeValue()
                    return@forEach
                }
                if (key in pendingCollectionDeletes) return@forEach
                // Trust the Firebase key as the authoritative id rather than the payload's own
                // `id` field, so key and row can never disagree.
                val cloudColl = child.getValue(InventoryCollection::class.java)?.copy(id = key) ?: return@forEach
                val localColl = collectionDao.getCollectionById(cloudColl.id)
                if (localColl == null || cloudColl.updatedAt > localColl.updatedAt) {
                    collectionDao.insertCollection(cloudColl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull collections failed", e)
        } finally {
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    fun deleteCollectionRemote(collectionId: Long) {
        // Fire-and-forget on repositoryScope rather than suspending: callers delete the local
        // row first and want that to feel instant (both in the UI list and before navigating
        // away from a detail screen), and shouldn't be gated on a Firebase round-trip to do so.
        pendingCollectionDeletes.add(collectionId)
        repositoryScope.launch {
            try {
                userRef?.child("collections")?.child(collectionId.toString())?.removeValue()?.await()
            } catch (e: Exception) {
                Log.e(TAG, "Delete collection remote failed", e)
            } finally {
                // removeValue()'s own Task resolving doesn't guarantee every listener echo of the
                // pre-delete state has already been delivered -- a stale onDataChange can still
                // land a moment later (same straggler-event issue the delay(1000) elsewhere in
                // this file guards against). Hold the guard a bit longer than that to absorb it.
                withContext(NonCancellable) {
                    delay(2000)
                    pendingCollectionDeletes.remove(collectionId)
                }
            }
        }
    }

    fun deleteLinkRemote(followerId: Long, leaderId: Long) {
        // Same fire-and-forget pattern as deleteCollectionRemote -- ItemLink has no isDirty
        // tombstone (it's a hard @Delete with a composite key, unlike Item/Task's soft-delete
        // flag), so unlinking never had anything to push to Firebase at all before this.
        val key = "${followerId}_${leaderId}"
        pendingLinkDeletes.add(key)
        repositoryScope.launch {
            try {
                userRef?.child("item_links")?.child(key)?.removeValue()?.await()
            } catch (e: Exception) {
                Log.e(TAG, "Delete link remote failed", e)
            } finally {
                withContext(NonCancellable) {
                    delay(2000)
                    pendingLinkDeletes.remove(key)
                }
            }
        }
    }

    private suspend fun pushCollectionItemsToFirebase(ref: DatabaseReference, items: List<InventoryCollectionItem>) {
        if (items.isEmpty()) return
        try {
            val updates = items.associate { "${it.collectionId}_${it.itemId}" to it }
            ref.updateChildren(updates).await()
            collectionDao.markCollectionItemsClean(items)
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
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    fun triggerFullSync() {
        Log.d(TAG, "Manual sync triggered")
        val ref = userRef ?: return
        
        repositoryScope.launch {
            try {
                _syncStatus.value = SyncStatus.Syncing
                
                coroutineScope {
                    listOf(
                        async { pushItemsToFirebase(ref.child("items"), inventoryDao.getAllItemsForSyncList()) },
                        async { pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getAllLinksList()) },
                        async { pushTasksToFirebase(ref.child("tasks"), taskDao.getAllTasksForSyncList()) },
                        async { pushCollectionsToFirebase(ref.child("collections"), collectionDao.getAllCollectionsList()) },
                        async { pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getAllCollectionItemsList()) }
                    ).awaitAll()
                }
                
                _syncStatus.value = SyncStatus.Synced
            } catch (e: Exception) {
                Log.e(TAG, "Manual sync failed", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun syncOnAppOpen() {
        try {
            val userId = authRepository.getOrCreateUserId()
            val ref = firebaseDatabase.getReference("users").child(userId)
            userRef = ref

            _syncStatus.value = SyncStatus.Syncing
            Log.d(TAG, "Performing pull-first sync on app open")

            coroutineScope {
                // 1. Pull first to overwrite stale local state (in parallel)
                listOf(
                    async { pullItemsFromFirebase(ref.child("items").get().await()) },
                    async { pullLinksFromFirebase(ref.child("item_links").get().await()) },
                    async { pullTasksFromFirebase(ref.child("tasks").get().await()) },
                    async { pullCollectionsFromFirebase(ref.child("collections").get().await()) },
                    async { pullCollectionItemsFromFirebase(ref.child("collection_items").get().await()) }
                ).awaitAll()

                // 2. Then push local changes (in parallel)
                listOf(
                    async { pushItemsToFirebase(ref.child("items"), inventoryDao.getDirtyItemsList()) },
                    async { pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getDirtyLinksList()) },
                    async { pushTasksToFirebase(ref.child("tasks"), taskDao.getDirtyTasksList()) },
                    async { pushCollectionsToFirebase(ref.child("collections"), collectionDao.getDirtyCollectionsList()) },
                    async { pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getDirtyCollectionItemsList()) }
                ).awaitAll()
            }

            _syncStatus.value = SyncStatus.Synced
            Log.d(TAG, "App open sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "App open sync failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }
}
