package com.inventoria.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.database.*
import com.inventoria.app.data.deletedRowPurgeThreshold
import com.inventoria.app.data.local.*
import com.inventoria.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Room and users/$uid in the Realtime Database mirroring each other, live, per table.
 *
 * The model every node follows (see [NodeSync]):
 *  - **Local -> cloud.** Every write in the app marks its row dirty; each table's dirty rows are
 *    pushed the moment Room reports them, and a row is only marked clean if it still holds exactly
 *    what was pushed.
 *  - **Cloud -> local.** A per-child listener keeps an in-memory copy of the cloud's current view
 *    of the node, and each changed child is merged into Room: a clean local row always takes the
 *    cloud's version (the cloud holds whatever was written last, so a clean row that differs is by
 *    definition behind), while a dirty row keeps the local edit unless the cloud's copy is newer.
 *
 * That rule is what makes two phones converge on the same state rather than each keeping the copy
 * it happens to consider newest by its own clock.
 */
@Singleton
class FirebaseSyncRepository @Inject constructor(
    private val database: InventoryDatabase,
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

    /**
     * Whose node [activeNodes] are currently listening on, so [syncOnAppOpen] can spot a change of
     * account. Volatile because it is written under [syncLock] but read outside it, from whichever
     * coroutine happens to be running the app-open sync.
     */
    @Volatile
    private var syncedUserId: String? = null

    /** One per synced table, attached to [syncedUserId]'s node. Replaced wholesale under [syncLock]. */
    @Volatile
    private var activeNodes: List<NodeSync<*>> = emptyList()
    private var settingsJob: Job? = null

    /**
     * Listeners attached straight to a DatabaseReference rather than owned by a [NodeSync], so
     * cancelling [settingsJob] does not detach them. Tracked here because one of them writes the
     * cloud's custom_username back into DataStore: left attached, it outlives the account it
     * belongs to and can repopulate a store that the account-delete wipe just cleared.
     */
    private val rawListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    /** The .info/connected listener is per-database, not per-account, so it is attached once. */
    private var connectionLoggingStarted = false

    /**
     * Guards the teardown/setup of the listener set above. [restartSyncForUser] has two callers on
     * different coroutines -- [startSync]'s manualSyncId collector and [syncOnAppOpen] -- and both
     * mutate the same state. None of the guarded code suspends.
     */
    private val syncLock = Any()

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
     * attached to the outgoing uid, and a push would otherwise land in a node that no longer exists.
     */
    fun stopSync() {
        synchronized(syncLock) {
            Log.d(TAG, "Stopping sync")
            detachFromCurrentUser()
            syncedUserId = null
            _syncStatus.value = SyncStatus.Idle
        }
    }

    /** Callers must hold [syncLock]. */
    private fun detachFromCurrentUser() {
        activeNodes.forEach { it.detach() }
        activeNodes = emptyList()
        settingsJob?.cancel()
        settingsJob = null
        rawListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        rawListeners.clear()
    }

    /**
     * Idempotent for the account already being synced: [startSync] and [syncOnAppOpen] both run at
     * process start and both land here, and tearing the listeners down just to rebuild them would
     * throw away the initial load the other caller is waiting on.
     */
    private fun restartSyncForUser(userId: String) = synchronized<Unit>(syncLock) {
        if (syncedUserId == userId && activeNodes.isNotEmpty()) return@synchronized

        detachFromCurrentUser()
        syncedUserId = userId

        Log.d(TAG, "Starting sync for user: $userId")

        val rootRef = firebaseDatabase.getReference("users").child(userId)
        startConnectionLoggingOnce()

        activeNodes = nodeSpecs().map { spec -> NodeSync(rootRef.child(spec.path), spec) }
        settingsJob = setupSettingsSync(rootRef.child("settings"))
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

    /**
     * Everything [NodeSync] needs to know about one table, so the sync logic exists once rather
     * than as eight hand-copied push/pull pairs that had already started to drift apart.
     *
     * [key] is the child name the row lives under in the cloud; [decode] turns a child back into a
     * row and may return null to skip it.
     */
    private class NodeSpec<T : Any>(
        val path: String,
        val decode: (DataSnapshot) -> T?,
        val key: (T) -> String,
        val updatedAt: (T) -> Long,
        val isDirty: (T) -> Boolean,
        val isDeleted: (T) -> Boolean,
        val asClean: (T) -> T,
        val dirtyFlow: () -> Flow<List<T>>,
        val dirtyList: suspend () -> List<T>,
        val allLocal: suspend () -> List<T>,
        val findLocal: suspend (T) -> T?,
        val upsert: suspend (List<T>) -> Unit,
        val markClean: suspend (List<T>) -> Unit
    )

    private fun nodeSpecs(): List<NodeSpec<*>> = listOf(
        NodeSpec<InventoryItem>(
            path = "items",
            decode = { it.getValue(InventoryItem::class.java) },
            key = { it.id.toString() },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = inventoryDao::getDirtyItemsFlow,
            dirtyList = inventoryDao::getDirtyItemsList,
            allLocal = inventoryDao::getAllItemsForSyncList,
            findLocal = { inventoryDao.getItemById(it.id) },
            upsert = inventoryDao::insertItems,
            markClean = { rows -> inventoryDao.markItemsClean(rows.map { it.id }) }
        ),
        NodeSpec<ItemLink>(
            path = "item_links",
            decode = { it.getValue(ItemLink::class.java) },
            key = { "${it.followerId}_${it.leaderId}" },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = itemLinkDao::getDirtyLinksFlow,
            dirtyList = itemLinkDao::getDirtyLinksList,
            allLocal = itemLinkDao::getAllLinksForSyncList,
            findLocal = { itemLinkDao.getLink(it.followerId, it.leaderId) },
            upsert = itemLinkDao::insertLinks,
            markClean = itemLinkDao::markLinksClean
        ),
        NodeSpec<Task>(
            path = "tasks",
            decode = { it.getValue(Task::class.java) },
            key = { it.id },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = taskDao::getDirtyTasksFlow,
            dirtyList = taskDao::getDirtyTasksList,
            allLocal = taskDao::getAllTasksForSyncList,
            findLocal = { taskDao.getTaskById(it.id) },
            upsert = taskDao::insertTasks,
            markClean = { rows -> taskDao.markTasksClean(rows.map { it.id }) }
        ),
        NodeSpec<InventoryCollection>(
            path = "collections",
            decode = ::decodeCollection,
            key = { it.id.toString() },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = collectionDao::getDirtyCollectionsFlow,
            dirtyList = collectionDao::getDirtyCollectionsList,
            allLocal = collectionDao::getAllCollectionsForSyncList,
            findLocal = { collectionDao.getCollectionById(it.id) },
            upsert = { rows -> rows.forEach { collectionDao.insertCollection(it) } },
            markClean = { rows -> collectionDao.markCollectionsClean(rows.map { it.id }) }
        ),
        NodeSpec<InventoryCollectionItem>(
            path = "collection_items",
            decode = { it.getValue(InventoryCollectionItem::class.java) },
            key = { "${it.collectionId}_${it.itemId}" },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = collectionDao::getDirtyCollectionItemsFlow,
            dirtyList = collectionDao::getDirtyCollectionItemsList,
            allLocal = collectionDao::getAllCollectionItemsForSyncList,
            findLocal = { collectionDao.getCollectionItem(it.collectionId, it.itemId) },
            upsert = collectionDao::insertCollectionItems,
            markClean = collectionDao::markCollectionItemsClean
        ),
        NodeSpec<Todo>(
            path = "todos",
            decode = { it.getValue(Todo::class.java) },
            key = { it.id },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = todoDao::getDirtyTodosFlow,
            dirtyList = todoDao::getDirtyTodosList,
            allLocal = todoDao::getAllTodosForSyncList,
            findLocal = { todoDao.getTodoById(it.id) },
            upsert = todoDao::insertTodos,
            markClean = { rows -> todoDao.markTodosClean(rows.map { it.id }) }
        ),
        NodeSpec<TaskType>(
            path = "task_types",
            decode = { it.getValue(TaskType::class.java) },
            key = { it.id },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = taskTypeDao::getDirtyTaskTypesFlow,
            dirtyList = taskTypeDao::getDirtyTaskTypesList,
            allLocal = taskTypeDao::getAllTaskTypesForSyncList,
            findLocal = { taskTypeDao.getTaskTypeById(it.id) },
            upsert = taskTypeDao::insertTaskTypes,
            markClean = { rows -> taskTypeDao.markTaskTypesClean(rows.map { it.id }) }
        ),
        NodeSpec<ScheduleBlock>(
            path = "schedule_blocks",
            decode = { it.getValue(ScheduleBlock::class.java) },
            key = { it.id },
            updatedAt = { it.updatedAt }, isDirty = { it.isDirty }, isDeleted = { it.isDeleted },
            asClean = { it.copy(isDirty = false) },
            dirtyFlow = scheduleBlockDao::getDirtyBlocksFlow,
            dirtyList = scheduleBlockDao::getDirtyBlocksList,
            allLocal = scheduleBlockDao::getAllBlocksForSyncList,
            findLocal = { scheduleBlockDao.getBlockById(it.id) },
            upsert = scheduleBlockDao::insertBlocks,
            markClean = { rows -> scheduleBlockDao.markBlocksClean(rows.map { it.id }) }
        )
    )

    private fun decodeCollection(child: DataSnapshot): InventoryCollection? {
        val key = child.key?.toLongOrNull() ?: return null
        if (key == 0L) {
            // Pre-autoGenerate builds always wrote new collections as id=0, so this key holds
            // stale, unaddressable data: since 0 is Room's "generate a new id" sentinel,
            // re-inserting it here would create a brand new local+cloud row every time the node
            // changed, which is exactly what produced runaway duplicate "collections" -- see
            // ErrorLog.md #27. Removing it is a one-time self-heal.
            child.ref.removeValue()
            return null
        }
        // Trust the Firebase key as the authoritative id rather than the payload's own `id`
        // field, so key and row can never disagree.
        return child.getValue(InventoryCollection::class.java)?.copy(id = key)
    }

    /**
     * Live two-way sync of one table with one cloud node.
     *
     * What this replaced, and why each part is shaped the way it is:
     *  - Pulls used to hold a global "ignore" counter for a second after every snapshot, and every
     *    push that landed in that window was silently dropped -- including the echo of this
     *    device's own last push. Tapping two checkboxes in quick succession left the second one
     *    unsynced until the next app open. Nothing needs suppressing: a merged row is written clean,
     *    so it never re-enters the dirty flow.
     *  - The same one-second delay sat inside each node's snapshot collector, so a burst of edits
     *    on one phone trickled onto the other at one per second.
     *  - A push marked its rows clean by id alone, so an edit made while the upload was in flight
     *    was marked clean too and never sent.
     *  - Each snapshot re-read the whole node, and a row was only accepted if its updatedAt beat
     *    the local copy's. updatedAt comes from each phone's own clock, so a phone whose clock ran
     *    ahead kept its copy forever while the cloud held the other phone's newer write.
     */
    private inner class NodeSync<T : Any>(
        private val ref: DatabaseReference,
        private val spec: NodeSpec<T>
    ) {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.IO + job)

        /**
         * The cloud's current view of this node, child by child, as the listener last reported it.
         * Firebase's local view overlays this device's own writes still in flight on top of the
         * server's data, so while a push is pending this shows the pushed value, and once it is
         * acknowledged it shows whatever the server holds.
         *
         * Merges always read from here rather than from the event that queued them, so no merge
         * can ever apply a copy older than one already seen.
         */
        private val cloud = ConcurrentHashMap<String, DataSnapshot>()
        private val pendingKeys = Channel<String>(Channel.UNLIMITED)
        private val queued = AtomicLong(0)
        private val merged = MutableStateFlow(0L)
        private val initialLoad = CompletableDeferred<Boolean>()

        private val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = onCloudChild(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = onCloudChild(snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Absence is never treated as a delete -- deletes are tombstones -- so this only
                // keeps [cloud] honest for pushMissing.
                snapshot.key?.let { cloud.remove(it) }
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listener failed for ${ref.path}", error.toException())
                _syncStatus.value = SyncStatus.Error(error.message)
                initialLoad.complete(false)
            }
        }

        /**
         * Value events are raised after the child events of the same update, so this firing means
         * every child of the initial load has already been handed to [onCloudChild].
         */
        private val initialLoadListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { initialLoad.complete(true) }
            override fun onCancelled(error: DatabaseError) { initialLoad.complete(false) }
        }

        init {
            ref.addChildEventListener(childListener)
            ref.addListenerForSingleValueEvent(initialLoadListener)

            scope.launch {
                for (first in pendingKeys) {
                    // Drain whatever else is already queued, so an initial load of a few thousand
                    // children is merged in a handful of transactions rather than one per child.
                    val batch = mutableListOf(first)
                    while (true) batch += pendingKeys.tryReceive().getOrNull() ?: break
                    try {
                        mergeFromCloud(batch.toSet())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Merge into ${spec.path} failed", e)
                    } finally {
                        merged.update { it + batch.size }
                    }
                }
            }

            scope.launch {
                spec.dirtyFlow().distinctUntilChanged().collect { rows -> push(rows) }
            }
        }

        fun detach() {
            ref.removeEventListener(childListener)
            ref.removeEventListener(initialLoadListener)
            initialLoad.complete(false)
            job.cancel()
        }

        private fun onCloudChild(snapshot: DataSnapshot) {
            val key = snapshot.key ?: return
            cloud[key] = snapshot
            enqueue(listOf(key))
        }

        private fun enqueue(keys: Collection<String>) {
            if (keys.isEmpty()) return
            queued.addAndGet(keys.size.toLong())
            keys.forEach { pendingKeys.trySend(it) }
        }

        private suspend fun mergeFromCloud(keys: Set<String>) {
            // Decoded outside the transaction: it is the slow part, and the transaction holds the
            // database's write lock against the app's own edits for as long as it runs.
            val cloudRows = keys.mapNotNull { key -> cloud[key]?.let(spec.decode) }
            if (cloudRows.isEmpty()) return
            val purgeBefore = deletedRowPurgeThreshold()

            // One transaction, so a local edit cannot slip in between reading a row and replacing it.
            database.withTransaction {
                val accepted = cloudRows.filter { cloudRow ->
                    val local = spec.findLocal(cloudRow)
                    when {
                        // A tombstone old enough to have been purged here would only be purged again.
                        local == null -> !(spec.isDeleted(cloudRow) && spec.updatedAt(cloudRow) < purgeBefore)
                        // An edit not yet (fully) pushed survives unless the cloud's is newer.
                        spec.isDirty(local) -> spec.updatedAt(cloudRow) > spec.updatedAt(local)
                        // Clean means the cloud has already seen this row; if they differ, the cloud
                        // holds a later write, whatever the two clocks say.
                        else -> cloudRow != spec.asClean(local)
                    }
                }
                if (accepted.isNotEmpty()) spec.upsert(accepted)
            }
        }

        suspend fun push(rows: List<T>) {
            if (rows.isEmpty()) return
            withSyncStatus(spec.path) {
                ref.updateChildren(rows.associate { spec.key(it) to it }).await()
                database.withTransaction {
                    // Only rows that still hold exactly what was sent: anything edited while the
                    // upload was in flight stays dirty, and the dirty flow pushes it next.
                    val unchanged = rows.filter { pushed ->
                        spec.findLocal(pushed)?.let { spec.asClean(it) == spec.asClean(pushed) } == true
                    }
                    if (unchanged.isNotEmpty()) spec.markClean(unchanged)
                }
                // A change from the other phone that arrived while these rows were still dirty was
                // held back by the merge; now that they are clean, look again.
                enqueue(rows.map(spec.key))
            }
        }

        suspend fun pushDirty() = push(spec.dirtyList())

        /** Re-merges every child the cloud is known to hold -- a cheap safety net, not a fetch. */
        fun remergeAll() = enqueue(cloud.keys.toList())

        /**
         * Uploads local rows the cloud has no copy of at all. Nothing else would ever send a row
         * that is clean locally, and before live sync was reliable a full re-upload of every row
         * papered over that; this keeps the repair without letting stale rows overwrite newer ones.
         */
        suspend fun pushMissing() {
            push(spec.allLocal().filter { spec.key(it) !in cloud })
        }

        /**
         * Suspends until the initial load has arrived and been merged. False if it did not arrive
         * in time (offline with nothing cached) or the listener was refused.
         */
        suspend fun awaitSettled(): Boolean {
            val loaded = withTimeoutOrNull(SETTLE_TIMEOUT_MS) { initialLoad.await() } ?: false
            if (!loaded) return false
            val target = queued.get()
            return withTimeoutOrNull(SETTLE_TIMEOUT_MS) { merged.first { it >= target } } != null
        }
    }

    private class CloudValue(val value: String?)

    private fun setupSettingsSync(settingsRef: DatabaseReference): Job {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val usernameRef = settingsRef.child("custom_username")

        // Null until the cloud's value is known. Pushing before then is what used to let a phone
        // that had been closed for a while overwrite a newer name with the one it last saw.
        val cloudUsername = MutableStateFlow<CloudValue?>(null)

        val usernameListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cloudUsername.value = CloudValue(snapshot.getValue(String::class.java))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        usernameRef.addValueEventListener(usernameListener)
        // Cancelling [job] stops the coroutines below but not this listener -- see [rawListeners].
        rawListeners.add(usernameRef to usernameListener)

        scope.launch {
            cloudUsername.filterNotNull().collect { cloud ->
                if (cloud.value != null) {
                    settingsRepository.saveCustomUsername(cloud.value)
                } else {
                    // Never set in the cloud: seed it from this device rather than erase ours.
                    settingsRepository.customUsername.first()?.let { usernameRef.setValue(it) }
                }
            }
        }

        scope.launch {
            settingsRepository.customUsername.distinctUntilChanged().collect { local ->
                val cloud = cloudUsername.value ?: return@collect
                if (local != cloud.value) usernameRef.setValue(local)
            }
        }

        return job
    }

    /**
     * Wraps one node's push with the syncStatus transitions every push should make -- Syncing
     * while it runs, Synced on success, Error on failure. Pushes return early on an empty list
     * before calling this, so a no-op push never flickers the indicator.
     */
    private suspend fun withSyncStatus(label: String, block: suspend () -> Unit) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            block()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Push $label failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Pushes every dirty row now. Live sync already does this as rows change; this is the safety
     * net for a push that failed and would otherwise wait for the row's next edit.
     */
    fun pushPendingChanges() {
        val nodes = activeNodes
        repositoryScope.launch {
            nodes.forEach { launch { it.pushDirty() } }
        }
    }

    /** Pull-to-refresh / manual sync: re-merge everything the cloud holds and push what is pending. */
    fun triggerFullSync() {
        Log.d(TAG, "Manual sync triggered")
        val nodes = activeNodes
        repositoryScope.launch {
            nodes.forEach { node ->
                node.remergeAll()
                launch { node.pushDirty() }
            }
        }
    }

    suspend fun syncOnAppOpen() {
        try {
            val userId = authRepository.getOrCreateUserId()

            // A deleted account has to stop this device too, not just the server. Absence would
            // never do it -- a merge reads an emptied node as "nothing new" -- so the tombstone is
            // what tells a device that was offline during the delete, or is simply a second phone,
            // that what it is holding is gone. Runs here because this is the one entry point that
            // fires on every app open and every background sync.
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

            // startSync()'s collector only fires when manualSyncId changes, so an account that
            // appears any other way -- the first anonymous one on a fresh install, or the
            // replacement created after an account delete -- would otherwise get no live
            // listeners at all until the process was restarted. A no-op if already listening.
            restartSyncForUser(userId)
            val nodes = activeNodes

            _syncStatus.value = SyncStatus.Syncing
            Log.d(TAG, "Waiting for the cloud's state on app open")

            // Callers rely on this returning only once the cloud's rows are in Room -- task-type
            // seeding runs straight after and must see an existing account's own types first.
            val allSettled = coroutineScope {
                nodes.map { node ->
                    async {
                        val settled = node.awaitSettled()
                        if (settled) {
                            node.remergeAll()
                            node.pushMissing()
                        }
                        node.pushDirty()
                        settled
                    }
                }.awaitAll().all { it }
            }

            if (allSettled) {
                _syncStatus.value = SyncStatus.Synced
                Log.d(TAG, "App open sync completed successfully")
            } else {
                _syncStatus.value = SyncStatus.Error("Couldn't load everything from the cloud")
                Log.w(TAG, "App open sync: not every node finished its initial load")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "App open sync failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 15_000L
    }
}
