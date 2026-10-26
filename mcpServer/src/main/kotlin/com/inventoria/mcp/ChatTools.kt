package com.inventoria.mcp

import com.inventoria.shared.model.nowMillis
import com.inventoria.shared.remote.RealtimeDatabaseRest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

/*
 * The app's chat with Claude, which lives in users/<uid>/agent_chat rather than in the synced tables:
 *
 *   chats/<chat id>              title, createdAt, updatedAt (the app); state, context use, model (the
 *                                listener); modelChoice / effortChoice (the app)
 *   messages/<chat id>/<key>     {role: user|assistant, text, createdAt, status, activity...}. A user
 *                                message is "pending" until the listener on the user's PC picks it up
 *                                ("working", then "done" or "error"); the reply is keyed "<key>~r"
 *   _listener                    {at: server time, busy}: the listener's heartbeat
 *   _models                      {at, models: [{value, name, description, resolved, efforts}]}
 *
 * Reading all of this is harmless. Posting a message makes the listener run Claude on the user's own
 * login, so sending is guarded, and there is deliberately no delete: chats have no tombstone.
 */

private const val CHAT = "agent_chat"
private const val CHAT_TITLE_LENGTH = 40
private const val DEFAULT_CHAT_TITLE = "New chat"

/** The listener beats every 20 s; this tolerates two missed beats plus a slow network, as the app does. */
internal const val LISTENER_ALIVE_WINDOW_MILLIS = 75_000L

private const val DEFAULT_MESSAGES = 30
private const val MAX_MESSAGES = 200

// --- Pure helpers ---------------------------------------------------------------------------------

private const val PUSH_ALPHABET = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"

/**
 * A key shaped like the Realtime Database's own push ids: 8 characters of [now] then 12 random ones,
 * all from an alphabet in ASCII order, so keys sort by creation time and a reply keyed "<key>~r" (~
 * sorts after every alphabet character) lands straight after its question. The app lists messages
 * by key, so a hand-made key has to sort the same way.
 */
internal fun newPushId(now: Long, random: Random = Random.Default): String {
    var time = now
    val stamp = CharArray(8)
    for (i in 7 downTo 0) {
        stamp[i] = PUSH_ALPHABET[(time % 64).toInt()]
        time /= 64
    }
    val tail = CharArray(12) { PUSH_ALPHABET[random.nextInt(64)] }
    return String(stamp) + String(tail)
}

/** A chat's title from its first message: the first line, cut short (the app's own rule). */
internal fun chatTitleFrom(text: String): String {
    val line = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    return when {
        line.isEmpty() -> DEFAULT_CHAT_TITLE
        line.length <= CHAT_TITLE_LENGTH -> line
        else -> line.take(CHAT_TITLE_LENGTH).trimEnd() + "…"
    }
}

private fun JsonObject.jstr(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.jlong(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
private fun JsonObject.jbool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

/** One message as the model sees it. Null for the entries that are not messages (no text or role). */
internal fun chatMessageView(key: String, node: JsonElement): JsonObject? {
    val message = node as? JsonObject ?: return null
    val text = message.jstr("text") ?: return null
    val role = message.jstr("role")?.takeIf { it == "user" || it == "assistant" } ?: return null
    val createdAt = message.jlong("createdAt")
    return obj(
        "id" to key,
        "role" to role,
        "text" to text,
        "status" to message.jstr("status"),
        "createdAtIso" to createdAt?.let(::isoString),
        "activity" to message.jstr("activityText")
    )
}

/** The messages of a chat, oldest first (keys sort by creation time), at most the last [limit]. */
internal fun chatMessages(node: JsonElement, limit: Int): List<JsonObject> {
    val all = (node as? JsonObject)?.entries.orEmpty().sortedBy { it.key }.mapNotNull { chatMessageView(it.key, it.value) }
    return all.takeLast(limit)
}

/** Whether Claude still owes this chat an answer: a user message nobody has finished with. */
internal fun awaitsReply(messages: List<JsonObject>): Boolean = messages.any {
    it["role"] == JsonPrimitive("user") && (it["status"] == JsonNull || it["status"] == JsonPrimitive("pending") ||
        it["status"] == JsonPrimitive("working"))
}

/** A chat's details as the model sees them. */
internal fun chatSummaryView(id: String, node: JsonElement): JsonObject? {
    val chat = node as? JsonObject ?: return null
    val createdAt = chat.jlong("createdAt") ?: 0L
    val updatedAt = chat.jlong("updatedAt")?.takeIf { it > 0 } ?: createdAt
    return obj(
        "id" to id,
        "title" to (chat.jstr("title")?.takeIf { it.isNotBlank() } ?: DEFAULT_CHAT_TITLE),
        "createdAtIso" to createdAt.takeIf { it > 0 }?.let(::isoString),
        "updatedAtIso" to updatedAt.takeIf { it > 0 }?.let(::isoString),
        "updatedAt" to updatedAt,
        "sessionState" to chat.jstr("state"),
        "contextPercent" to chat.jlong("contextPercent"),
        "model" to chat.jstr("model"),
        "modelChoice" to chat.jstr("modelChoice"),
        "effortChoice" to chat.jstr("effortChoice"),
        "closedReason" to chat.jstr("closedReason")
    )
}

/** One model Claude offers, as the listener published it. */
internal class ChatModel(val value: String, val name: String, val description: String, val efforts: List<String>)

/** The published catalog (`_models`). Empty until the listener has run once. */
internal fun chatModels(node: JsonElement): List<ChatModel> {
    val models = (node as? JsonObject)?.get("models") ?: return emptyList()
    return RealtimeDatabaseRest.childrenOf(models).mapNotNull { entry ->
        val model = entry as? JsonObject ?: return@mapNotNull null
        val value = model.jstr("value") ?: return@mapNotNull null
        ChatModel(
            value = value,
            name = model.jstr("name") ?: value,
            description = model.jstr("description").orEmpty(),
            efforts = model["efforts"]?.let { RealtimeDatabaseRest.childrenOf(it) }.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        )
    }
}

/**
 * Checks a model/effort pick against what Claude offers, the way the listener will (it silently drops
 * a pick the catalog does not list, which would look like the choice was ignored). With no catalog
 * published yet there is nothing to check against, so the pick goes through unchecked.
 */
internal fun checkChatChoice(catalog: List<ChatModel>, model: String?, effort: String?) {
    if (catalog.isEmpty() || (model == null && effort == null)) return
    val entry = catalog.firstOrNull { it.value == (model ?: "default") }
    if (entry == null) {
        if (model != null) throw ToolError("Claude does not offer a model '$model'; the choices are ${catalog.joinToString { it.value }}")
        return
    }
    if (effort != null && effort !in entry.efforts) {
        val takes = if (entry.efforts.isEmpty()) "takes no effort setting" else "takes ${entry.efforts.joinToString()}"
        throw ToolError("Model '${entry.value}' $takes, not '$effort'")
    }
}

// --- The tools ------------------------------------------------------------------------------------

private suspend fun Vault.chatNode(path: String): JsonElement = readNode("$CHAT/$path")

fun chatTools(vault: Vault): List<Tool> = listOf(
    Tool(
        name = "chat_status",
        description = "Whether the program that answers the app's Claude chat is running on the user's PC (its heartbeat), " +
            "and the models and effort levels Claude offers for a chat. Check this before send_chat_message: with no " +
            "listener running a message is never answered.",
        inputSchema = schema(),
        readOnly = true
    ) {
        val beat = vault.chatNode("_listener") as? JsonObject
        val at = beat?.jlong("at") ?: 0L
        val now = nowMillis()
        obj(
            "listener" to obj(
                "online" to (at > 0 && now - at < LISTENER_ALIVE_WINDOW_MILLIS),
                "busy" to (beat?.jbool("busy") ?: false),
                "lastSeenIso" to at.takeIf { it > 0 }?.let(::isoString)
            ),
            "models" to JsonArray(chatModels(vault.chatNode("_models")).map {
                obj("value" to it.value, "name" to it.name, "description" to it.description, "efforts" to it.efforts)
            })
        )
    },

    Tool(
        name = "list_chats",
        description = "The app's chats with Claude, most recently active first: title, when it last moved, whether its " +
            "session is open, how full its context is (past 60% a new chat is worth starting) and the model/effort picked.",
        inputSchema = schema("limit" to intP("Maximum chats (default 20, max 100)")),
        readOnly = true
    ) { args ->
        val limit = (args.int("limit") ?: 20).coerceIn(1, 100)
        val chats = (vault.chatNode("chats") as? JsonObject)?.entries.orEmpty()
            .mapNotNull { (id, node) -> chatSummaryView(id, node) }
            .sortedByDescending { (it["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0L }
        obj("matched" to chats.size, "chats" to JsonArray(chats.take(limit)))
    },

    Tool(
        name = "read_chat",
        description = "The messages of one chat, oldest first. A user message's status is pending (not picked up), working " +
            "(Claude is on it; `activity` says what), done or error; the reply follows it as an assistant message.",
        inputSchema = schema(
            "chat_id" to strP("Chat id from list_chats"),
            "limit" to intP("How many of the latest messages (default $DEFAULT_MESSAGES, max $MAX_MESSAGES)"),
            required = listOf("chat_id")
        ),
        readOnly = true
    ) { args ->
        val chatId = args.reqStr("chat_id")
        val limit = (args.int("limit") ?: DEFAULT_MESSAGES).coerceIn(1, MAX_MESSAGES)
        if (chatSummaryView(chatId, vault.chatNode("chats/$chatId")) == null) throw ToolError("No chat '$chatId'; see list_chats")
        val messages = chatMessages(vault.chatNode("messages/$chatId"), limit)
        obj("chatId" to chatId, "awaitingReply" to awaitsReply(messages), "messages" to JsonArray(messages))
    },

    Tool(
        name = "send_chat_message",
        description = "Post a message into one of the app's chats with Claude (or start a new chat by leaving out chat_id) " +
            "exactly as typing it in the app would. This makes the listener on the user's PC run Claude on the user's " +
            "own login to answer it, so it uses their Claude usage: only send what the user asked for. Returns at once " +
            "with the message id; the answer arrives later, read it with read_chat. Refused while the chat is still " +
            "waiting on an earlier message. Do not use this from inside a chat run -- it would ask Claude to answer " +
            "itself.",
        inputSchema = schema(
            "text" to strP("What to say"),
            "chat_id" to strP("Chat to post into; leave out to start a new chat"),
            "model" to strP("For a new chat: the model to run it on (a value from chat_status); default is Claude's own pick"),
            "effort" to strP("For a new chat: the effort level (one that model takes)"),
            required = listOf("text")
        ),
        readOnly = false
    ) { args ->
        val text = args.reqStr("text").trim()
        val existingId = args.str("chat_id")
        if (existingId != null && (args.has("model") || args.has("effort"))) {
            throw ToolError("'model' and 'effort' only apply to a new chat; use set_chat_model on an existing one")
        }
        if (existingId != null && chatSummaryView(existingId, vault.chatNode("chats/$existingId")) == null) {
            throw ToolError("No chat '$existingId'; see list_chats, or leave out chat_id to start a new one")
        }
        if (existingId != null && awaitsReply(chatMessages(vault.chatNode("messages/$existingId"), MAX_MESSAGES))) {
            throw ToolError("Claude has not answered the last message in this chat yet; wait for it (read_chat), or start a new chat")
        }
        val model = args.str("model")
        val effort = args.str("effort")
        if (existingId == null) checkChatChoice(chatModels(vault.chatNode("_models")), model, effort)

        val now = nowMillis()
        val chatId = existingId ?: newPushId(now)
        val messageId = newPushId(now)
        val update = buildJsonObject {
            put("messages/$chatId/$messageId", obj("role" to "user", "text" to text, "createdAt" to now, "status" to "pending"))
            put("chats/$chatId/updatedAt", now)
            if (existingId == null) {
                put("chats/$chatId/title", chatTitleFrom(text))
                put("chats/$chatId/createdAt", now)
                if (model != null) put("chats/$chatId/modelChoice", model)
                if (effort != null) put("chats/$chatId/effortChoice", effort)
            }
        }
        vault.patchNode(CHAT, update)

        val beat = vault.chatNode("_listener") as? JsonObject
        val at = beat?.jlong("at") ?: 0L
        val online = at > 0 && now - at < LISTENER_ALIVE_WINDOW_MILLIS
        obj(
            "chatId" to chatId,
            "messageId" to messageId,
            "newChat" to (existingId == null),
            "listenerOnline" to online,
            "note" to if (online) "Sent. Claude's answer arrives as the next message; read it with read_chat."
            else "Sent, but no listener heartbeat in the last ${LISTENER_ALIVE_WINDOW_MILLIS / 1000} s: nothing answers until " +
                "the agent listener is running on the user's PC."
        )
    },

    Tool(
        name = "set_chat_model",
        description = "Change which model and effort an existing chat runs on (values from chat_status). Only the fields you " +
            "send change; null goes back to Claude's default. The listener reopens the chat's session on the new settings " +
            "before its next message and the conversation carries over. Changing the model drops an effort the new model " +
            "does not take.",
        inputSchema = schema(
            "chat_id" to strP("Chat id from list_chats"),
            "model" to strP("Model value, or null for Claude's default", nullable = true),
            "effort" to strP("Effort level that model takes, or null for its default", nullable = true),
            required = listOf("chat_id")
        ),
        readOnly = false
    ) { args ->
        val chatId = args.reqStr("chat_id")
        val chat = vault.chatNode("chats/$chatId") as? JsonObject ?: throw ToolError("No chat '$chatId'; see list_chats")
        if (!args.has("model") && !args.has("effort")) throw ToolError("Send 'model' and/or 'effort'")

        val model = if (args.has("model")) args.str("model") else chat.jstr("modelChoice")
        val catalog = chatModels(vault.chatNode("_models"))
        var effort = if (args.has("effort")) args.str("effort") else chat.jstr("effortChoice")
        if (args.has("model") && !args.has("effort") && effort != null) {
            // A new model may not take the old effort; keep it only if it does.
            val takes = catalog.firstOrNull { it.value == (model ?: "default") }?.efforts
            if (takes != null && effort !in takes) effort = null
        }
        checkChatChoice(catalog, model, effort)

        vault.patchNode(CHAT, buildJsonObject {
            put("chats/$chatId/modelChoice", model?.let { JsonPrimitive(it) } ?: JsonNull)
            put("chats/$chatId/effortChoice", effort?.let { JsonPrimitive(it) } ?: JsonNull)
        })
        obj("chatId" to chatId, "modelChoice" to model, "effortChoice" to effort)
    }
)
