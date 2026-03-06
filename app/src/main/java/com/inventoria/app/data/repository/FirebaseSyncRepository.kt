package com.inventoria.app.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.ItemLinkDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicInteger
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
    private val itemLinkDao: ItemLinkDao,
    private val firebaseDatabase: FirebaseDatabase,
    private val authRepository: FirebaseAuthRepository,
    private val settingsRepository: SettingsRepository
) {
    private val TAG = "FirebaseSync"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Use a counter per sync node to handle overlapping updates more robustly
    private val syncIgnoreCount = AtomicInteger(0)
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
        setupNodeSync(
            nodeRef = rootRef.child("inventory"),
            localFlow = inventoryDao.getAllItems(),
            pushAction = { ref, items -> pushItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullItemsFromFirebase(snapshot) }
        )

        // 2. Task Sync
        setupNodeSync(
            nodeRef = rootRef.child("tasks"),
            localFlow = taskDao.getAllTasks(),
            pushAction = { ref, tasks -> pushTasksToFirebase(ref, tasks) },
            pullAction = { snapshot -> pullTasksFromFirebase(snapshot) }
        )

        // 3. Collections Sync
        setupNodeSync(
            nodeRef = rootRef.child("collections"),
            localFlow = collectionDao.getAllCollections(),
            pushAction = { ref, colls -> pushCollectionsToFirebase(ref, colls) },
            pullAction = { snapshot -> pullCollectionsFromFirebase(snapshot) }
        )

        // 4. Collection Items Sync (Junction Table)
        setupNodeSync(
            nodeRef = rootRef.child("collection_items"),
            localFlow = collectionDao.getAllCollectionItemsFlow(),
            pushAction = { ref, items -> pushCollectionItemsToFirebase(ref, items) },
            pullAction = { snapshot -> pullCollectionItemsFromFirebase(snapshot) }
        )

        // 5. Item Links Sync
        setupNodeSync(
            nodeRef = rootRef.child("item_links"),
            localFlow = itemLinkDao.getAllLinksFlow(),
            pushAction = { ref, links -> pushLinksToFirebase(ref, links) },
            pullAction = { snapshot -> pullLinksFromFirebase(snapshot) }
        )

        // 6. Settings Sync (Includes Custom Username)
        val settingsRef = rootRef.child("settings")
        repositoryScope.launch {
            settingsRepository.customUsername.distinctUntilChanged().collect { username ->
                if (syncIgnoreCount.get() == 0) {
                    settingsRef.child("custom_username").setValue(username)
                }
            }
        }

        settingsRef.child("custom_username").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudUsername = snapshot.getValue(String::class.java)
                repositoryScope.launch {
                    syncIgnoreCount.incrementAndGet()
                    settingsRepository.saveCustomUsername(cloudUsername)
                    delay(800)
                    syncIgnoreCount.decrementAndGet()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun <T> setupNodeSync(
        nodeRef: DatabaseReference,
        localFlow: Flow<List<T>>,
        pushAction: suspend (DatabaseReference, List<T>) -> Unit,
        pullAction: suspend (DataSnapshot) -> Unit
    ) {
        // Local to Firebase: Push local changes if we are not currently applying a cloud update
        repositoryScope.launch {
            localFlow.distinctUntilChanged()
                .collect { data ->
                    if (syncIgnoreCount.get() == 0) {
                        pushAction(nodeRef, data)
                    }
                }
        }

        // Firebase to Local: Listen for cloud changes and apply them locally
        // Using callbackFlow + collectLatest ensures that if snapshots arrive rapidly,
        // we only process the most recent one, canceling any stale pull operations.
        val firebaseFlow = callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot)
                }
                override fun onCancelled(error: DatabaseError) {
                    _syncStatus.value = SyncStatus.Error(error.message)
                }
            }
            nodeRef.addValueEventListener(listener)
            awaitClose { nodeRef.removeEventListener(listener) }
        }

        repositoryScope.launch {
            firebaseFlow.collectLatest { snapshot ->
                pullAction(snapshot)
            }
        }
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

                // Item Links
                val linksSnapshot = ref.child("item_links").get().await()
                pullLinksFromFirebase(linksSnapshot)
                pushLinksToFirebase(ref.child("item_links"), itemLinkDao.getAllLinksFlow().first())
                
                // Username
                val usernameSnapshot = ref.child("settings").child("custom_username").get().await()
                settingsRepository.saveCustomUsername(usernameSnapshot.getValue(String::class.java))

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
            Log.e(TAG, "Push items failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push items failed")
        }
    }

    private suspend fun pullItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryItem::class.java) }
            val localItems = inventoryDao.getAllItems().first().associateBy { it.id }
            val cloudIds = cloudItems.map { it.id }.toSet()
            
            val toUpdateLocal = mutableListOf<InventoryItem>()
            
            cloudItems.forEach { cloud ->
                val local = localItems[cloud.id]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toUpdateLocal.add(cloud)
                }
            }

            if (toUpdateLocal.isNotEmpty() || localItems.size != cloudIds.size) {
                syncIgnoreCount.incrementAndGet()
                
                if (toUpdateLocal.isNotEmpty()) {
                    inventoryDao.insertItems(toUpdateLocal)
                }
                
                localItems.values.forEach { local ->
                    if (!cloudIds.contains(local.id)) {
                        val isLikelyNewLocal = System.currentTimeMillis() - local.updatedAt < 30000
                        if (!isLikelyNewLocal) {
                            inventoryDao.deleteItemById(local.id)
                        }
                    }
                }
                
                delay(800)
                syncIgnoreCount.decrementAndGet()
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull items failed", e)
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
            Log.e(TAG, "Push tasks failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push tasks failed")
        }
    }

    private suspend fun pullTasksFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudTasks = snapshot.children.mapNotNull { it.getValue(Task::class.java) }
            val localTasks = taskDao.getAllTasks().first().associateBy { it.id }
            val cloudIds = cloudTasks.map { it.id }.toSet()

            val toUpdateLocal = mutableListOf<Task>()
            
            cloudTasks.forEach { cloud ->
                val local = localTasks[cloud.id]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toUpdateLocal.add(cloud)
                }
            }

            if (toUpdateLocal.isNotEmpty() || localTasks.size != cloudIds.size) {
                syncIgnoreCount.incrementAndGet()
                
                if (toUpdateLocal.isNotEmpty()) {
                    taskDao.insertTasks(toUpdateLocal)
                }
                
                localTasks.values.forEach { local ->
                    if (!cloudIds.contains(local.id)) {
                        val isLikelyNewLocal = System.currentTimeMillis() - local.updatedAt < 30000
                        if (!isLikelyNewLocal) {
                            taskDao.deleteTaskById(local.id)
                        }
                    }
                }

                delay(800)
                syncIgnoreCount.decrementAndGet()
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull tasks failed", e)
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
            Log.e(TAG, "Push collections failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push collections failed")
        }
    }

    private suspend fun pullCollectionsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudColls = snapshot.children.mapNotNull { it.getValue(InventoryCollection::class.java) }
            val localColls = collectionDao.getAllCollections().first().associateBy { it.id }
            val cloudIds = cloudColls.map { it.id }.toSet()

            val toUpdateLocal = mutableListOf<InventoryCollection>()
            
            cloudColls.forEach { cloud ->
                val local = localColls[cloud.id]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toUpdateLocal.add(cloud)
                }
            }

            if (toUpdateLocal.isNotEmpty() || localColls.size != cloudIds.size) {
                syncIgnoreCount.incrementAndGet()
                
                if (toUpdateLocal.isNotEmpty()) {
                    toUpdateLocal.forEach { collectionDao.insertCollection(it) }
                }
                
                localColls.values.forEach { local ->
                    if (!cloudIds.contains(local.id)) {
                        val isLikelyNewLocal = System.currentTimeMillis() - local.updatedAt < 30000
                        if (!isLikelyNewLocal) {
                            collectionDao.deleteCollection(local)
                        }
                    }
                }

                delay(800)
                syncIgnoreCount.decrementAndGet()
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull collections failed", e)
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
            Log.e(TAG, "Push collection items failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push collection items failed")
        }
    }

    private suspend fun pullCollectionItemsFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudItems = snapshot.children.mapNotNull { it.getValue(InventoryCollectionItem::class.java) }
            val localItems = collectionDao.getItemsForSync().associateBy { "${it.collectionId}_${it.itemId}" }
            val cloudKeys = cloudItems.map { "${it.collectionId}_${it.itemId}" }.toSet()

            val toUpdateLocal = mutableListOf<InventoryCollectionItem>()
            
            cloudItems.forEach { cloud ->
                val key = "${cloud.collectionId}_${cloud.itemId}"
                val local = localItems[key]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toUpdateLocal.add(cloud)
                }
            }

            if (toUpdateLocal.isNotEmpty() || localItems.size != cloudKeys.size) {
                syncIgnoreCount.incrementAndGet()
                
                if (toUpdateLocal.isNotEmpty()) {
                    toUpdateLocal.forEach { collectionDao.insertCollectionItem(it) }
                }
                
                localItems.values.forEach { local ->
                    val key = "${local.collectionId}_${local.itemId}"
                    if (!cloudKeys.contains(key)) {
                        val isLikelyNewLocal = System.currentTimeMillis() - local.updatedAt < 30000
                        if (!isLikelyNewLocal) {
                            collectionDao.deleteCollectionItem(local)
                        }
                    }
                }

                delay(800)
                syncIgnoreCount.decrementAndGet()
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull collection items failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull collection items failed")
        }
    }

    // --- Item Links Sync ---
    private suspend fun pushLinksToFirebase(ref: DatabaseReference, links: List<ItemLink>) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val linksMap = links.associateBy { "${it.followerId}_${it.leaderId}" }
            ref.setValue(linksMap).await()
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Push item links failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Push item links failed")
        }
    }

    private suspend fun pullLinksFromFirebase(snapshot: DataSnapshot) {
        try {
            _syncStatus.value = SyncStatus.Syncing
            val cloudLinks = snapshot.children.mapNotNull { it.getValue(ItemLink::class.java) }
            val localLinks = itemLinkDao.getAllLinks().associateBy { "${it.followerId}_${it.leaderId}" }
            val cloudKeys = cloudLinks.map { "${it.followerId}_${it.leaderId}" }.toSet()

            val toUpdateLocal = mutableListOf<ItemLink>()
            
            cloudLinks.forEach { cloud ->
                val key = "${cloud.followerId}_${cloud.leaderId}"
                val local = localLinks[key]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toUpdateLocal.add(cloud)
                }
            }

            if (toUpdateLocal.isNotEmpty() || localLinks.size != cloudKeys.size) {
                syncIgnoreCount.incrementAndGet()
                
                if (toUpdateLocal.isNotEmpty()) {
                    toUpdateLocal.forEach { itemLinkDao.insertLink(it) }
                }
                
                localLinks.values.forEach { local ->
                    val key = "${local.followerId}_${local.leaderId}"
                    if (!cloudKeys.contains(key)) {
                        val isLikelyNewLocal = System.currentTimeMillis() - local.updatedAt < 30000
                        if (!isLikelyNewLocal) {
                            itemLinkDao.removeLink(local.followerId, local.leaderId)
                        }
                    }
                }

                delay(800)
                syncIgnoreCount.decrementAndGet()
            }
            _syncStatus.value = SyncStatus.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull item links failed", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Pull item links failed")
        }
    }
}
