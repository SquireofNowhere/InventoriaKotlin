package com.inventoria.mcp

import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.InventoriaJson
import com.inventoria.shared.remote.RealtimeDatabaseRest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/** The nodes under users/$uid that hold synced rows. Settings is not one: it is a free-form map. */
object Nodes {
    const val ITEMS = "items"
    const val ITEM_LINKS = "item_links"
    const val TASKS = "tasks"
    const val COLLECTIONS = "collections"
    const val COLLECTION_ITEMS = "collection_items"
    const val TODOS = "todos"
    const val TASK_TYPES = "task_types"
    const val SCHEDULE_BLOCKS = "schedule_blocks"

    /** Everything a raw read may address. */
    val readable = listOf(
        ITEMS, ITEM_LINKS, TASKS, COLLECTIONS, COLLECTION_ITEMS, TODOS, TASK_TYPES, SCHEDULE_BLOCKS, "settings"
    )
}

/**
 * One vault -- the account whose invite code this server joined -- read and written through the
 * Realtime Database REST API.
 *
 * The write rules mirror what the phone's sync relies on, and are the reason this class exists
 * rather than callers patching nodes themselves:
 *  - every change bumps `updatedAt`, and always past the row's previous value, because the phone
 *    resolves a conflict with an unsent local edit by comparing the two;
 *  - a delete is a tombstone (`isDeleted` + `updatedAt`), never a removal, so the delete reaches
 *    every device and can still be undone -- the phone's own purge removes it after 30 days;
 *  - an edit sends only the fields that changed, so it cannot clobber a field some other client
 *    wrote in the meantime or a field this build does not know about.
 */
class Vault(private val db: RealtimeDatabaseRest, val ownerUid: String) {
    private val root = "users/$ownerUid"

    /** One write at a time: every edit is read-modify-write, and two interleaved would lose one. */
    private val writeLock = Mutex()
    private val lastId = AtomicLong(0L)

    /**
     * A fresh numeric id for an item or collection. The phone numbers its own rows 1, 2, 3, ...
     * (Room auto-increment), so ids from here are millisecond timestamps instead: they cannot
     * collide with a row the phone has made but not yet synced, which would silently replace one
     * of the two.
     */
    fun newNumericId(): Long {
        while (true) {
            val last = lastId.get()
            val next = maxOf(nowMillis(), last + 1)
            if (lastId.compareAndSet(last, next)) return next
        }
    }

    suspend fun readNode(path: String): JsonElement = db.get("$root/$path")

    /** Every row of [node] including tombstones; callers filter. Undecodable rows are skipped. */
    suspend fun <T> rows(node: String, serializer: KSerializer<T>): List<T> =
        db.getChildren("$root/$node", serializer)

    suspend fun <T> row(node: String, key: String, serializer: KSerializer<T>): T? {
        val element = db.get("$root/$node/$key")
        if (element !is JsonObject) return null
        return runCatching { InventoriaJson.decodeFromJsonElement(serializer, element) }.getOrNull()
    }

    suspend fun <T> create(node: String, key: String, row: T, serializer: KSerializer<T>): T = writeLock.withLock {
        db.put("$root/$node/$key", InventoriaJson.encodeToJsonElement(serializer, row))
        row
    }

    /**
     * Applies [change] to the row at [key] and writes back only what differs. Returns the row as
     * written (with its new `updatedAt`). Throws [ToolError] when there is no such row.
     */
    suspend fun <T> edit(node: String, key: String, serializer: KSerializer<T>, change: (T) -> T): T =
        writeLock.withLock {
            val current = db.get("$root/$node/$key") as? JsonObject
                ?: throw ToolError("No row '$key' in $node")
            val old = runCatching { InventoriaJson.decodeFromJsonElement(serializer, current) }.getOrNull()
                ?: throw ToolError("Row '$key' in $node could not be read")
            val updated = change(old)

            val oldJson = InventoriaJson.encodeToJsonElement(serializer, old) as JsonObject
            val newJson = InventoriaJson.encodeToJsonElement(serializer, updated) as JsonObject
            val stamp = nextStamp(current)

            val patch = buildJsonObject {
                for ((field, value) in newJson) {
                    if (field != "updatedAt" && oldJson[field] != value) put(field, value)
                }
                // explicitNulls = false drops a cleared field from the encoding; the database
                // needs an explicit null to remove it.
                for (field in oldJson.keys) {
                    if (field != "updatedAt" && field !in newJson) put(field, JsonNull)
                }
                put("updatedAt", stamp)
            }
            db.patch("$root/$node/$key", patch)
            InventoriaJson.decodeFromJsonElement(serializer, newJson.withField("updatedAt", JsonPrimitive(stamp)))
        }

    /** Soft-deletes (or restores) one row. Returns false when the row does not exist. */
    suspend fun setDeleted(node: String, key: String, deleted: Boolean): Boolean = writeLock.withLock {
        val current = db.get("$root/$node/$key") as? JsonObject ?: return@withLock false
        db.patch("$root/$node/$key", buildJsonObject {
            put("isDeleted", deleted)
            put("updatedAt", nextStamp(current))
        })
        true
    }

    /** Sets one key of the settings node. The only synced one today is `custom_username`. */
    suspend fun putSetting(key: String, value: JsonElement) = writeLock.withLock {
        db.put("$root/settings/$key", value)
    }

    private fun nextStamp(current: JsonObject): Long {
        val previous = (current["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
        return maxOf(nowMillis(), previous + 1)
    }
}
