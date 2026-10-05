package com.inventoria.mcp

import com.inventoria.shared.model.InventoryCollection
import com.inventoria.shared.model.InventoryCollectionItem
import com.inventoria.shared.model.InventoryCollectionType
import com.inventoria.shared.model.InventoryItem
import com.inventoria.shared.model.ItemLink
import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.InventoriaJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// --- Optional-field helpers shared by every update tool -----------------------------------------
//
// An update tool must tell "leave this alone" (key absent) from "clear this" (key sent as null),
// so these return [current] for an absent key and null for an explicit null.

internal fun Args.pickStr(key: String, current: String?): String? = when {
    !has(key) -> current
    isNull(key) -> null
    else -> str(key)
}

internal fun Args.pickDouble(key: String, current: Double?): Double? = when {
    !has(key) -> current
    isNull(key) -> null
    else -> double(key)
}

internal fun Args.pickInt(key: String, current: Int?): Int? = when {
    !has(key) -> current
    isNull(key) -> null
    else -> int(key)
}

internal fun Args.pickLong(key: String, current: Long?): Long? = when {
    !has(key) -> current
    isNull(key) -> null
    else -> long(key)
}

private fun Args.customFields(): Map<String, String>? {
    val el = json["custom_fields"] ?: return null
    if (el is JsonNull) return emptyMap()
    val o = el as? JsonObject ?: throw ToolError("'custom_fields' must be an object of text values")
    return o.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
}

private suspend fun requireContainer(vault: Vault, id: Long): InventoryItem {
    val parent = vault.requireItem(id)
    if (!parent.storage) {
        throw ToolError("Item $id ('${parent.name}') is not a container; set storage=true on it first")
    }
    return parent
}

/**
 * Moves an item into the container [parentId] (or out to the open when null), taking along every
 * item linked to it as leader or follower, as the app does. Returns one summary per item moved.
 */
internal suspend fun moveItem(vault: Vault, id: Long, parentId: Long?, location: String?): List<JsonElement> {
    vault.requireItem(id)
    val newParent = parentId?.let { requireContainer(vault, it).id }

    // The item plus everything connected to it by leader/follower links, which move as one.
    val adjacency = mutableMapOf<Long, MutableList<Long>>()
    for (link in vault.itemLinks()) {
        adjacency.getOrPut(link.leaderId) { mutableListOf() }.add(link.followerId)
        adjacency.getOrPut(link.followerId) { mutableListOf() }.add(link.leaderId)
    }
    val group = linkedSetOf<Long>()
    val queue = ArrayDeque(listOf(id))
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (group.add(current)) adjacency[current]?.let { queue.addAll(it) }
    }

    // A container may not end up inside itself or anything it holds.
    val all = vault.items().associateBy { it.id }
    var cursor = newParent
    val seen = mutableSetOf<Long>()
    while (cursor != null && seen.add(cursor)) {
        if (cursor in group) throw ToolError("That would put an item inside itself (or something it contains)")
        cursor = all[cursor]?.parentId
    }

    val moved = mutableListOf<JsonElement>()
    for (memberId in group) {
        if (all[memberId] == null) continue
        val updated = vault.edit("items", memberId.toString(), InventoryItem.serializer()) { item ->
            item.copy(
                parentId = newParent,
                lastParentId = if (newParent == null) null else item.lastParentId,
                equipped = if (newParent != null) false else item.equipped,
                location = if (newParent != null) "" else location ?: item.location
            )
        }
        moved += obj("id" to updated.id, "name" to updated.name, "parentId" to updated.parentId)
    }
    return moved
}

private val ITEM_FIELDS = arrayOf(
    "name" to strP("Item name"),
    "quantity" to intP("How many are held"),
    "location" to strP("Where it is, as text (ignored while the item is inside a container)"),
    "latitude" to numP("GPS latitude", nullable = true),
    "longitude" to numP("GPS longitude", nullable = true),
    "price" to numP("Unit price", nullable = true),
    "storage" to boolP("true if this item is a container that can hold other items"),
    "category" to strP("Comma-separated tags, e.g. 'tools, camping'", nullable = true),
    "description" to strP("Free-text notes", nullable = true),
    "barcode" to strP("Barcode", nullable = true),
    "sku" to strP("SKU", nullable = true),
    "custom_fields" to prop("object", "Extra key/value text fields; replaces the whole set (send {} to clear)")
)

fun inventoryTools(vault: Vault): List<Tool> = listOf(
    Tool(
        name = "create_item",
        description = "Add an inventory item. Give parent_id to put it inside a container item (which must have " +
            "storage=true); otherwise set location as text. It appears on the phone at its next sync.",
        inputSchema = schema(
            *ITEM_FIELDS,
            "parent_id" to intP("Container item to place it in"),
            required = listOf("name")
        ),
        readOnly = false
    ) { args ->
        val parent = args.long("parent_id")?.let { requireContainer(vault, it) }
        val now = nowMillis()
        val item = InventoryItem(
            id = vault.newNumericId(),
            name = args.reqStr("name").trim(),
            quantity = args.int("quantity") ?: 1,
            location = if (parent != null) "" else args.str("location") ?: "",
            latitude = args.double("latitude"),
            longitude = args.double("longitude"),
            price = args.double("price"),
            storage = args.bool("storage") ?: false,
            parentId = parent?.id,
            customFields = args.customFields() ?: emptyMap(),
            createdAt = now,
            updatedAt = now,
            category = args.str("category"),
            description = args.str("description"),
            barcode = args.str("barcode"),
            sku = args.str("sku")
        )
        if (item.quantity < 0) throw ToolError("'quantity' cannot be negative")
        obj("item" to itemView(vault.create("items", item.id.toString(), item, InventoryItem.serializer())))
    },

    Tool(
        name = "update_item",
        description = "Change fields of an item. Only the fields you send change; send null to clear a clearable " +
            "field. Use quantity_delta to add or remove stock relative to what is there (never goes below 0). " +
            "To put it in or take it out of a container use move_item; to wear it use set_equipped.",
        inputSchema = schema(
            "id" to intP("Item id"),
            *ITEM_FIELDS,
            "quantity_delta" to intP("Add (positive) or remove (negative) this many units"),
            required = listOf("id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqLong("id")
        vault.requireItem(id)
        if (args.has("quantity") && args.has("quantity_delta")) throw ToolError("Send quantity or quantity_delta, not both")
        val updated = vault.edit("items", id.toString(), InventoryItem.serializer()) { item ->
            val quantity = when {
                args.has("quantity_delta") -> (item.quantity + (args.int("quantity_delta") ?: 0)).coerceAtLeast(0)
                args.has("quantity") -> args.int("quantity") ?: throw ToolError("'quantity' cannot be null")
                else -> item.quantity
            }
            if (quantity < 0) throw ToolError("'quantity' cannot be negative")
            item.copy(
                name = if (args.has("name")) args.reqStr("name").trim() else item.name,
                quantity = quantity,
                location = if (args.has("location")) args.str("location") ?: "" else item.location,
                latitude = args.pickDouble("latitude", item.latitude),
                longitude = args.pickDouble("longitude", item.longitude),
                price = args.pickDouble("price", item.price),
                storage = args.bool("storage") ?: item.storage,
                category = args.pickStr("category", item.category),
                description = args.pickStr("description", item.description),
                barcode = args.pickStr("barcode", item.barcode),
                sku = args.pickStr("sku", item.sku),
                customFields = args.customFields() ?: item.customFields
            )
        }
        obj("item" to itemView(updated))
    },

    Tool(
        name = "move_item",
        description = "Put an item inside a container (parent_id) or take it out to the open (parent_id null, optional " +
            "location text). Items linked to it as leader/follower move together, as in the app. The target must " +
            "have storage=true and not be inside the moved item. A moved item is no longer equipped.",
        inputSchema = schema(
            "id" to intP("Item to move"),
            "parent_id" to intP("Container to move it into, or null to take it out", nullable = true),
            "location" to strP("Where it now is, as text; only used when parent_id is null"),
            required = listOf("id", "parent_id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqLong("id")
        if (!args.has("parent_id")) throw ToolError("'parent_id' is required (use null to take the item out)")
        obj("moved" to JsonArray(moveItem(vault, id, args.long("parent_id"), args.str("location"))))
    },

    Tool(
        name = "set_equipped",
        description = "Mark items as on the person (equipped), or put them down. Equipping takes an item out of its " +
            "container and remembers where it was; unequipping with repack=true puts it back there.",
        inputSchema = schema(
            "ids" to arrP("integer", "Item ids"),
            "equipped" to boolP("true = equip, false = unequip"),
            "repack" to boolP("When unequipping, return each item to the container it was taken from"),
            "location" to strP("When unequipping without repack: where the item was left, as text"),
            required = listOf("ids", "equipped")
        ),
        readOnly = false
    ) { args ->
        val ids = args.longList("ids")?.distinct()?.takeIf { it.isNotEmpty() } ?: throw ToolError("'ids' must list at least one item")
        val equip = args.bool("equipped") ?: throw ToolError("'equipped' is required")
        val repack = args.bool("repack") ?: false
        val all = vault.items().associateBy { it.id }
        val results = mutableListOf<JsonElement>()
        for (id in ids) {
            vault.requireItem(id)
            val updated = vault.edit("items", id.toString(), InventoryItem.serializer()) { item ->
                when {
                    equip && item.equipped -> item
                    equip -> item.copy(equipped = true, parentId = null, lastParentId = item.parentId)
                    else -> {
                        // The old container may have been deleted since; fall back to the open.
                        val back = if (repack) item.lastParentId?.takeIf { all[it] != null } else null
                        item.copy(
                            equipped = false,
                            parentId = back,
                            location = if (back != null) "" else args.str("location") ?: "Dropped"
                        )
                    }
                }
            }
            results += obj("id" to updated.id, "name" to updated.name, "equipped" to updated.equipped, "parentId" to updated.parentId)
        }
        obj("items" to JsonArray(results))
    },

    Tool(
        name = "delete_item",
        description = "Delete an item. It is soft-deleted (kept as a tombstone, restorable with restore) like the app does. " +
            "Items inside it and collection entries pointing at it are left as they are; the result says how many.",
        inputSchema = schema("id" to intP("Item id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val id = args.reqLong("id")
        val item = vault.requireItem(id)
        val inside = vault.items().count { it.parentId == id }
        val entries = vault.collectionItems().count { it.itemId == id }
        vault.setDeleted("items", id.toString(), true)
        obj("deleted" to item.name, "itemsLeftInside" to inside, "collectionEntriesLeft" to entries)
    },

    Tool(
        name = "link_items",
        description = "Link two items as leader and follower: a follower with no location of its own shows its leader's.",
        inputSchema = schema(
            "follower_id" to intP("The follower item"),
            "leader_id" to intP("The leader item"),
            required = listOf("follower_id", "leader_id")
        ),
        readOnly = false
    ) { args ->
        val follower = args.reqLong("follower_id")
        val leader = args.reqLong("leader_id")
        if (follower == leader) throw ToolError("An item cannot be linked to itself")
        vault.requireItem(follower)
        vault.requireItem(leader)
        val key = "${follower}_$leader"
        val existing = vault.row("item_links", key, ItemLink.serializer())
        if (existing == null) {
            vault.create("item_links", key, ItemLink(follower, leader, nowMillis()), ItemLink.serializer())
        } else if (existing.isDeleted) {
            vault.setDeleted("item_links", key, false)
        }
        // The app touches both items so each device re-reads them; an identity edit bumps updatedAt.
        for (id in listOf(follower, leader)) vault.edit("items", id.toString(), InventoryItem.serializer()) { it }
        obj("linked" to key)
    },

    Tool(
        name = "unlink_items",
        description = "Remove the leader/follower link between two items.",
        inputSchema = schema(
            "follower_id" to intP("The follower item"),
            "leader_id" to intP("The leader item"),
            required = listOf("follower_id", "leader_id")
        ),
        readOnly = false,
        destructive = true
    ) { args ->
        val follower = args.reqLong("follower_id")
        val leader = args.reqLong("leader_id")
        if (!vault.setDeleted("item_links", "${follower}_$leader", true)) throw ToolError("Those items are not linked")
        runCatching { vault.edit("items", follower.toString(), InventoryItem.serializer()) { it } }
        obj("unlinked" to "${follower}_$leader")
    },

    Tool(
        name = "create_collection",
        description = "Create a collection: a named set of items you gather together, such as a travel kit. Add entries " +
            "afterwards with set_collection_item.",
        inputSchema = schema(
            "name" to strP("Collection name"),
            "description" to strP("Notes"),
            "collection_type" to strP("Label only; changes no behaviour", enum = InventoryCollectionType.entries.map { it.name }),
            "tags" to arrP("string", "Tags"),
            "icon" to strP("Icon name"),
            "color" to intP("ARGB colour as a signed integer"),
            required = listOf("name")
        ),
        readOnly = false
    ) { args ->
        val now = nowMillis()
        val c = InventoryCollection(
            id = vault.newNumericId(),
            name = args.reqStr("name").trim(),
            description = args.str("description"),
            icon = args.str("icon"),
            color = args.int("color") ?: 0,
            tags = args.strList("tags") ?: emptyList(),
            collectionType = args.enum<InventoryCollectionType>("collection_type") ?: InventoryCollectionType.OTHER,
            createdAt = now,
            updatedAt = now
        )
        obj("collection" to collectionView(vault.create("collections", c.id.toString(), c, InventoryCollection.serializer())))
    },

    Tool(
        name = "update_collection",
        description = "Change a collection's name, description, type, tags, icon or colour. Only sent fields change.",
        inputSchema = schema(
            "id" to intP("Collection id"),
            "name" to strP("Collection name"),
            "description" to strP("Notes", nullable = true),
            "collection_type" to strP("Label only", enum = InventoryCollectionType.entries.map { it.name }),
            "tags" to arrP("string", "Tags (replaces the list)"),
            "icon" to strP("Icon name", nullable = true),
            "color" to intP("ARGB colour as a signed integer"),
            required = listOf("id")
        ),
        readOnly = false
    ) { args ->
        val id = args.reqLong("id")
        vault.requireCollection(id)
        val updated = vault.edit("collections", id.toString(), InventoryCollection.serializer()) { c ->
            c.copy(
                name = if (args.has("name")) args.reqStr("name").trim() else c.name,
                description = args.pickStr("description", c.description),
                icon = args.pickStr("icon", c.icon),
                color = args.int("color") ?: c.color,
                tags = args.strList("tags") ?: c.tags,
                collectionType = args.enum<InventoryCollectionType>("collection_type") ?: c.collectionType
            )
        }
        obj("collection" to collectionView(updated))
    },

    Tool(
        name = "delete_collection",
        description = "Delete a collection (soft delete, restorable). The items in it are untouched.",
        inputSchema = schema("id" to intP("Collection id"), required = listOf("id")),
        readOnly = false,
        destructive = true
    ) { args ->
        val c = vault.requireCollection(args.reqLong("id"))
        vault.setDeleted("collections", c.id.toString(), true)
        obj("deleted" to c.name)
    },

    Tool(
        name = "set_collection_item",
        description = "Add an item to a collection, or change its required quantity or notes if it is already in. " +
            "The entry counts as ready while the item's stock covers required_quantity.",
        inputSchema = schema(
            "collection_id" to intP("Collection id"),
            "item_id" to intP("Item id"),
            "required_quantity" to intP("How many of the item the collection needs (default 1)"),
            "notes" to strP("Notes on this entry", nullable = true),
            required = listOf("collection_id", "item_id")
        ),
        readOnly = false
    ) { args ->
        val collectionId = args.reqLong("collection_id")
        val itemId = args.reqLong("item_id")
        vault.requireCollection(collectionId)
        vault.requireItem(itemId)
        val required = args.int("required_quantity")
        if (required != null && required < 1) throw ToolError("'required_quantity' must be at least 1")
        val key = "${collectionId}_$itemId"
        val existing = vault.row("collection_items", key, InventoryCollectionItem.serializer())
        val entry = if (existing == null) {
            val sortOrder = vault.collectionItems().count { it.collectionId == collectionId }
            val now = nowMillis()
            vault.create(
                "collection_items", key,
                InventoryCollectionItem(collectionId, itemId, required ?: 1, args.str("notes"), sortOrder, now, now),
                InventoryCollectionItem.serializer()
            )
        } else {
            vault.edit("collection_items", key, InventoryCollectionItem.serializer()) { e ->
                e.copy(
                    requiredQuantity = required ?: e.requiredQuantity,
                    notes = args.pickStr("notes", e.notes),
                    isDeleted = false
                )
            }
        }
        obj("entry" to InventoriaJson.encodeToJsonElement(InventoryCollectionItem.serializer(), entry))
    },

    Tool(
        name = "remove_collection_item",
        description = "Take an item out of a collection (the item itself is untouched).",
        inputSchema = schema(
            "collection_id" to intP("Collection id"),
            "item_id" to intP("Item id"),
            required = listOf("collection_id", "item_id")
        ),
        readOnly = false,
        destructive = true
    ) { args ->
        val key = "${args.reqLong("collection_id")}_${args.reqLong("item_id")}"
        if (!vault.setDeleted("collection_items", key, true)) throw ToolError("That item is not in that collection")
        obj("removed" to key)
    }
)
