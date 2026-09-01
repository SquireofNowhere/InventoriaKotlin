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
    private val todoDao: TodoDao,
    private val taskTypeDao: TaskTypeDao,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val firebaseDatabase: FirebaseDatabase,
    private val authRepository: FirebaseAuthRepository,
    private val settingsRepository: SettingsRepository,
    private val localDataRepository: LocalDataRepository
) {
    private val TAG = "FirebaseSync"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val syncIgnoreCount = AtomicInteger(0)
    private var userRef: DatabaseReference? = null
    /**
     * Whose node [syncJobs] are currently listening on, so [syncOnAppOpen] can spot a change of
     * account. Volatile because it is written under [syncLock] but read outside it, from whichever
     * coroutine happens to be running the app-open sync.
     */
    @Volatile
    private var syncedUserId: String? = null
    private var syncJobs = mutableListOf<Job>()

    /**
     * Listeners attached straight to a DatabaseReference rather than through a callbackFlow, so
     * cancelling [syncJobs] does not detach them. Tracked here because one of them writes the
     * cloud's custom_username back into DataStore: left attached, it outlives the account it
     * belongs to and can repopulate a store that the account-delete wipe just cleared.
     */
    private val rawListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    /** The .info/connected listener is per-database, not per-account, so it is attached once. */
    private var connectionLoggingStarted = false

    /**
     * Guards the teardown/setup of the listener set above. [restartSyncForUser] now has two
     * callers on different coroutines -- [startSync]'s manualSyncId collector and [syncOnAppOpen]
     * -- and both mutate the same plain lists. None of the guarded code suspends.
     */
    private val syncLock = Any()

    fun isSyncing(): Boolean = syncIgnoreCount.get() > 0

    fun startSync() {
        repositoryScope.launch {
            settingsRepository.manualSyncId.collect { manualId ->
                val userId = manualId ?: authRepository.getCurrentUserId() ?: return@collect
                restartSyncForUser(userId)
            }
        }
    }

    /**
     * Tears the live listeners down and forgets the node they were attached to.
     *
     * Deleting an account has to do this before the local wipe: the per-node listeners are still
     * attached to the outgoing uid, and [triggerFullSync] pushes to whatever [userRef] happens to
     * hold, which after a delete is a node that no longer exists.
     */
    fun stopSync() {
        synchronized(syncLock) {
            Log.d(TAG, "Stopping sync")
            detachFromCurrentUser()
            userRef = null
            syncedUserId = null
            _syncStatus.value = SyncStatus.Idle
        }
    }

    /** Callers must hold [syncLock]. */
    private fun detachFromCurrentUser() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
        rawListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        rawListeners.clear()
    }

    private fun restartSyncForUser(userId: String) = synchronized<Unit>(syncLock) {
        detachFromCurrentUser()
        syncedUserId = userId

        Log.d(TAG, "Starting sync for user: $userId")

        val rootRef = firebaseDatabase.getReference("users").child(userId)
        userRef = rootRef

        startConnectionLoggingOnce()

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

        // Sync Todos
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("todos"),
            localFlow = todoDao.getDirtyTodosFlow(),
            pushAction = { ref, todos -> pushTodosToFirebase(ref, todos) },
            pullAction = { snapshot -> pullTodosFromFirebase(snapshot) }
        ))

        // Sync Task Types
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("task_types"),
            localFlow = taskTypeDao.getDirtyTaskTypesFlow(),
            pushAction = { ref, taskTypes -> pushTaskTypesToFirebase(ref, taskTypes) },
            pullAction = { snapshot -> pullTaskTypesFromFirebase(snapshot) }
        ))

        // Sync Schedule Blocks
        syncJobs.add(setupNodeSync(
            nodeRef = rootRef.child("schedule_blocks"),
            localFlow = scheduleBlockDao.getDirtyBlocksFlow(),
            pushAction = { ref, blocks -> pushScheduleBlocksToFirebase(ref, blocks) },
            pullAction = { snapshot -> pullScheduleBlocksFromFirebase(snapshot) }
        ))

        syncJobs.add(setupSettingsSync(rootRef.child("settings")))
    }

    /**
     * Connection state belongs to the database, not to whoever is signed in, and this listener was
     * previously re-added on every restart without ever being removed -- so each change of account
     * left another copy logging the same transitions.
     */
    private fun startConnectionLoggingOnce() {
        if (connectionLoggingStarted) return
        connectionLoggingStarted = true
        firebaseDatabase.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase Connection Status: ${if (connected) "CONNECTED" else "DISCONNECTED"}")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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

        val usernameRef = settingsRef.child("custom_username")
        val usernameListener = object : ValueEventListener {
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
        }
        usernameRef.addValueEventListener(usernameListener)
        // Cancelling [job] stops the coroutine above but not this listener -- see [rawListeners].
        rawListeners.add(usernameRef to usernameListener)

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
        if (links.isEmpty()) return
        try {
            val updates = links.associate { "${it.followerId}_${it.leaderId}" to it }
            ref.updateChildren(updates).await()
            itemLinkDao.markLinksClean(links)
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
        if (collections.isEmpty()) return
        try {
            val updates = collections.associate { it.id.toString() to it }
            ref.updateChildren(updates).await()
            collectionDao.markCollectionsClean(collections.map { it.id })
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

    private suspend fun pushTodosToFirebase(ref: DatabaseReference, todos: List<Todo>) {
        if (todos.isEmpty()) return
        try {
            val updates = todos.associate { it.id to it }
            ref.updateChildren(updates).await()
            todoDao.markTodosClean(todos.map { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "Push todos failed", e)
        }
    }

    private suspend fun pullTodosFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudTodos = snapshot.children.mapNotNull { it.getValue(Todo::class.java) }

            // Only overwrite local if cloud version is newer
            val todosToInsert = cloudTodos.filter { cloudTodo ->
                val localTodo = todoDao.getTodoById(cloudTodo.id)
                localTodo == null || cloudTodo.updatedAt > localTodo.updatedAt
            }

            if (todosToInsert.isNotEmpty()) {
                todoDao.insertTodos(todosToInsert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull todos failed", e)
        } finally {
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    private suspend fun pushTaskTypesToFirebase(ref: DatabaseReference, taskTypes: List<TaskType>) {
        if (taskTypes.isEmpty()) return
        try {
            val updates = taskTypes.associate { it.id to it }
            ref.updateChildren(updates).await()
            taskTypeDao.markTaskTypesClean(taskTypes.map { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "Push task types failed", e)
        }
    }

    private suspend fun pullTaskTypesFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudTaskTypes = snapshot.children.mapNotNull { it.getValue(TaskType::class.java) }

            // Only overwrite local if cloud version is newer
            val taskTypesToInsert = cloudTaskTypes.filter { cloudTaskType ->
                val localTaskType = taskTypeDao.getTaskTypeById(cloudTaskType.id)
                localTaskType == null || cloudTaskType.updatedAt > localTaskType.updatedAt
            }

            if (taskTypesToInsert.isNotEmpty()) {
                taskTypeDao.insertTaskTypes(taskTypesToInsert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull task types failed", e)
        } finally {
            withContext(NonCancellable) {
                delay(1000)
                syncIgnoreCount.decrementAndGet()
            }
        }
    }

    private suspend fun pushScheduleBlocksToFirebase(ref: DatabaseReference, blocks: List<ScheduleBlock>) {
        if (blocks.isEmpty()) return
        try {
            val updates = blocks.associate { it.id to it }
            ref.updateChildren(updates).await()
            scheduleBlockDao.markBlocksClean(blocks.map { it.id })
        } catch (e: Exception) {
            Log.e(TAG, "Push schedule blocks failed", e)
        }
    }

    private suspend fun pullScheduleBlocksFromFirebase(snapshot: DataSnapshot) {
        try {
            syncIgnoreCount.incrementAndGet()
            val cloudBlocks = snapshot.children.mapNotNull { it.getValue(ScheduleBlock::class.java) }

            // Only overwrite local if cloud version is newer
            val blocksToInsert = cloudBlocks.filter { cloudBlock ->
                val localBlock = scheduleBlockDao.getBlockById(cloudBlock.id)
                localBlock == null || cloudBlock.updatedAt > localBlock.updatedAt
            }

            if (blocksToInsert.isNotEmpty()) {
                scheduleBlockDao.insertBlocks(blocksToInsert)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull schedule blocks failed", e)
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
                        async { pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getAllLinksForSyncList()) },
                        async { pushTasksToFirebase(ref.child("tasks"), taskDao.getAllTasksForSyncList()) },
                        async { pushCollectionsToFirebase(ref.child("collections"), collectionDao.getAllCollectionsForSyncList()) },
                        async { pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getAllCollectionItemsForSyncList()) },
                        async { pushTodosToFirebase(ref.child("todos"), todoDao.getAllTodosForSyncList()) },
                        async { pushTaskTypesToFirebase(ref.child("task_types"), taskTypeDao.getAllTaskTypesForSyncList()) },
                        async { pushScheduleBlocksToFirebase(ref.child("schedule_blocks"), scheduleBlockDao.getAllBlocksForSyncList()) }
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

            // A deleted account has to stop this device too, not just the server. Absence would
            // never do it -- an insert-only pull reads an emptied node as "nothing new" -- so the
            // tombstone is what tells a device that was offline during the delete, or is simply a
            // second phone, that what it is holding is gone. Runs here because this is the one
            // entry point that fires on every app open and every background sync.
            if (authRepository.isAccountDeleted(userId)) {
                stopSync()
                if (userId == authRepository.getCurrentUserId()) {
                    // Our own account, deleted from another device. Everything goes.
                    Log.w(TAG, "This device's account ($userId) was deleted elsewhere; wiping")
                    localDataRepository.wipeAllLocalData()
                    authRepository.signOut()
                } else {
                    // We were only *reading* this account over an invite code and its owner deleted
                    // it. Their data goes and so does the connection -- which is also the first
                    // time a joiner gets told anything at all instead of just failing to sync
                    // forever -- but our own identity and preferences were never theirs to take.
                    Log.w(TAG, "Externally synced account $userId was deleted; disconnecting")
                    localDataRepository.clearSyncedData()
                    settingsRepository.saveManualSyncId(null)
                }
                _syncStatus.value = SyncStatus.Idle
                return
            }

            val ref = firebaseDatabase.getReference("users").child(userId)
            userRef = ref

            // startSync()'s collector only fires when manualSyncId changes, so an account that
            // appears any other way -- the first anonymous one on a fresh install, or the
            // replacement created after an account delete -- would otherwise get no live
            // listeners at all until the process was restarted, leaving this one-shot sync as
            // the only thing keeping the device up to date.
            if (syncedUserId != userId) {
                restartSyncForUser(userId)
            }

            _syncStatus.value = SyncStatus.Syncing
            Log.d(TAG, "Performing pull-first sync on app open")

            coroutineScope {
                // 1. Pull first to overwrite stale local state (in parallel)
                listOf(
                    async { pullItemsFromFirebase(ref.child("items").get().await()) },
                    async { pullLinksFromFirebase(ref.child("item_links").get().await()) },
                    async { pullTasksFromFirebase(ref.child("tasks").get().await()) },
                    async { pullCollectionsFromFirebase(ref.child("collections").get().await()) },
                    async { pullCollectionItemsFromFirebase(ref.child("collection_items").get().await()) },
                    async { pullTodosFromFirebase(ref.child("todos").get().await()) },
                    async { pullTaskTypesFromFirebase(ref.child("task_types").get().await()) },
                    async { pullScheduleBlocksFromFirebase(ref.child("schedule_blocks").get().await()) }
                ).awaitAll()

                // 2. Then push local changes (in parallel)
                listOf(
                    async { pushItemsToFirebase(ref.child("items"), inventoryDao.getDirtyItemsList()) },
                    async { pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getDirtyLinksList()) },
                    async { pushTasksToFirebase(ref.child("tasks"), taskDao.getDirtyTasksList()) },
                    async { pushCollectionsToFirebase(ref.child("collections"), collectionDao.getDirtyCollectionsList()) },
                    async { pushCollectionItemsToFirebase(ref.child("collection_items"), collectionDao.getDirtyCollectionItemsList()) },
                    async { pushTodosToFirebase(ref.child("todos"), todoDao.getDirtyTodosList()) },
                    async { pushTaskTypesToFirebase(ref.child("task_types"), taskTypeDao.getDirtyTaskTypesList()) },
                    async { pushScheduleBlocksToFirebase(ref.child("schedule_blocks"), scheduleBlockDao.getDirtyBlocksList()) }
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
