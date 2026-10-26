package com.inventoria.app.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class ChatRole { USER, ASSISTANT }

/**
 * One line of the Claude chat. [status] only means something on a user message, and is written by
 * whatever answers it (tools/agent-listener): "pending" until picked up, "working" while Claude runs,
 * then "done" or "error". The app writes "pending" and never changes it again.
 *
 * While the status is "working" the listener also reports what Claude is doing: [activity] is a
 * phase (see [ChatProgress]) and [activityText] a ready-to-show line such as "Looking up todos".
 * [sending] is the app's own knowledge, not the database's: the message is on this phone but the
 * server has not confirmed the write yet.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val createdAt: Long,
    val status: String?,
    val activity: String? = null,
    val activityText: String? = null,
    val sending: Boolean = false
) {
    /** A user message nobody has answered yet. */
    val isAwaitingReply: Boolean
        get() = role == ChatRole.USER && (status == null || status == STATUS_PENDING || status == STATUS_WORKING)

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_WORKING = "working"
    }
}

/**
 * One model Claude offers. Nothing about the models is known to the app itself: the agent listener
 * asks Claude which it offers and publishes the answer, so the list, the names and the effort levels
 * each model takes are always Claude's own. [value] is what is stored as a chat's choice (and handed to
 * `--model`); [efforts] is empty for a model with no effort setting.
 */
data class ModelOption(
    val value: String,
    val name: String,
    val description: String,
    val resolved: String,
    val efforts: List<String>
) {
    val isDefault: Boolean get() = value == DEFAULT_VALUE

    companion object {
        /** Claude's own entry for "whatever you would pick": a chat with no choice runs on this. */
        const val DEFAULT_VALUE = "default"
    }
}

/** What Claude offers, as last published by the listener. Empty until the listener has started once. */
data class ModelCatalog(val models: List<ModelOption>, val updatedAt: Long = 0L) {
    val isEmpty: Boolean get() = models.isEmpty()

    /** The entry for [value]; null (no choice) means the default entry. */
    fun option(value: String?): ModelOption? = models.firstOrNull { it.value == (value ?: ModelOption.DEFAULT_VALUE) }

    companion object {
        val EMPTY = ModelCatalog(emptyList())
    }
}

/**
 * Whether the program that answers the chat is running. The agent listener on the user's PC writes a
 * heartbeat to `agent_chat/_listener` every few seconds; [lastSeen] is that beat in *server* time,
 * and 0 means none has ever been seen (no listener, or one from before it sent heartbeats).
 */
data class ListenerPresence(val lastSeen: Long, val busy: Boolean) {
    fun isAlive(serverNow: Long): Boolean = lastSeen > 0 && serverNow - lastSeen < ALIVE_WINDOW_MILLIS

    companion object {
        val NEVER = ListenerPresence(0L, false)

        /** The listener beats every 20 s; this tolerates two missed beats plus a slow network. */
        const val ALIVE_WINDOW_MILLIS = 75_000L
    }
}

/** This phone's link to the database: whether it is connected, and how far its clock is from the
 * server's (add [serverOffset] to the phone's time to get server time). */
data class Connection(val connected: Boolean, val serverOffset: Long) {
    companion object {
        /** Assumed until Firebase says otherwise, so the screen does not flash "offline" on open. */
        val UNKNOWN = Connection(connected = true, serverOffset = 0L)
    }
}

/** A message the database refused or never accepted: its text, so it can be given back to the user. */
data class SendFailure(val text: String, val reason: String)

/** How full a chat's context is, in the terms the user acts on. */
enum class ContextLevel { FINE, GETTING_FULL, FULL }

/** From here a chat is worth replacing: Claude keeps all of it in mind, and answers get slower, dearer
 * and less sharp as it fills, well before the hard limit. */
const val CONTEXT_GETTING_FULL_PERCENT = 60
const val CONTEXT_FULL_PERCENT = 85

fun contextLevelFor(percent: Int): ContextLevel = when {
    percent >= CONTEXT_FULL_PERCENT -> ContextLevel.FULL
    percent >= CONTEXT_GETTING_FULL_PERCENT -> ContextLevel.GETTING_FULL
    else -> ContextLevel.FINE
}

/**
 * One chat with Claude, as the app lists it. The user's side ([title], [createdAt], [updatedAt]) is
 * written by the app; the rest is written by the agent listener, which runs one Claude session per
 * chat on the user's PC: whether that session is open ([state]: "live" or "closed"), how full its
 * context is, and when it last did anything, so the app can say when it will close and when a new chat
 * is due. A chat the listener has never answered has none of that (state null, context 0).
 */
data class ChatSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: String? = null,
    val contextTokens: Long = 0L,
    val contextWindow: Long = 0L,
    val contextPercent: Int = 0,
    val model: String? = null,
    /** Server time of the session's last activity. */
    val lastActiveAt: Long = 0L,
    /** Minutes with no message after which the listener closes the session. */
    val idleMinutes: Int = 0,
    val closedReason: String? = null,
    /** The model the user picked for this chat (a [ModelOption.value]); null leaves it to Claude's default. */
    val modelChoice: String? = null,
    /** The effort the user picked (one of that model's [ModelOption.efforts]); null leaves it to the default. */
    val effortChoice: String? = null
) {
    val isLive: Boolean get() = state == STATE_LIVE
    val contextLevel: ContextLevel get() = contextLevelFor(contextPercent)

    /** Milliseconds until the listener closes this chat's idle session, or null when none is open. */
    fun closesInMillis(serverNow: Long): Long? =
        if (isLive && lastActiveAt > 0 && idleMinutes > 0) lastActiveAt + idleMinutes * 60_000L - serverNow else null

    companion object {
        const val STATE_LIVE = "live"
        const val STATE_CLOSED = "closed"
        const val DEFAULT_TITLE = "New chat"
    }
}

/**
 * The chat with Claude, kept in the vault's own database node (`users/<uid>/agent_chat`) rather than
 * in Room: it is a conversation, not data to merge between devices, and the thing that answers it
 * is a process on the user's PC that already talks to Firebase. Nothing here calls a model.
 *
 * It lives in the database the device is *syncing* ([FirebaseAuthRepository.getOrCreateUserId]),
 * so a phone that joined someone else's vault chats in that vault, next to the data the answers
 * are about. Message keys are push ids, which sort by creation time, so "the last N" needs no index.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase,
    private val authRepository: FirebaseAuthRepository,
    private val settingsRepository: SettingsRepository
) {

    /** Ids of messages written locally that the server has not confirmed yet. */
    private val unconfirmed = MutableStateFlow<Set<String>>(emptySet())

    private val _sendFailures = MutableSharedFlow<SendFailure>(extraBufferCapacity = 4)

    /** Writes the database refused (a rule, say). The message vanishes from the list when that
     * happens, because Firebase rolls the local write back -- this is how the screen finds out why. */
    val sendFailures: SharedFlow<SendFailure> = _sendFailures.asSharedFlow()

    private suspend fun chatRef(): DatabaseReference =
        firebaseDatabase.getReference("users").child(authRepository.getOrCreateUserId()).child(NODE)

    /**
     * The newest [HISTORY_LIMIT] messages of one chat, oldest first, live. Re-attaches if the synced
     * vault changes. A listener failure (permission denied, say) closes the flow with the exception,
     * so the collector must catch it -- see ErrorLog #19 for what an uncaught one does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun messages(chatId: String): Flow<List<ChatMessage>> =
        settingsRepository.manualSyncId
            .distinctUntilChanged()
            .map { chatRef().child(MESSAGES_KEY).child(chatId) }
            .flatMapLatest { ref -> observe(ref) }
            .let { stored ->
                combine(stored, unconfirmed) { list, pending ->
                    if (pending.isEmpty()) list else list.map { if (it.id in pending) it.copy(sending = true) else it }
                }
            }

    /** Every chat, most recently active first, live. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun chats(): Flow<List<ChatSummary>> =
        settingsRepository.manualSyncId
            .distinctUntilChanged()
            .map { chatRef().child(CHATS_KEY) }
            .flatMapLatest { ref ->
                valueFlow(ref) { snapshot ->
                    snapshot.children.mapNotNull(::decodeChat).sortedByDescending { it.updatedAt }
                }
            }

    /** The listener's heartbeat, live. [ListenerPresence.NEVER] while there is none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun listenerPresence(): Flow<ListenerPresence> =
        settingsRepository.manualSyncId
            .distinctUntilChanged()
            .map { chatRef().child(LISTENER_KEY) }
            .flatMapLatest { ref ->
                valueFlow(ref) { snapshot ->
                    ListenerPresence(
                        lastSeen = snapshot.child("at").getValue(Long::class.java) ?: 0L,
                        busy = snapshot.child("busy").getValue(Boolean::class.java) ?: false
                    )
                }
            }

    /** Whether this phone is connected to the database, and its clock offset from the server. */
    fun connection(): Flow<Connection> {
        val root = firebaseDatabase.reference
        val connected = valueFlow(root.child(".info/connected")) { it.getValue(Boolean::class.java) ?: false }
        val offset = valueFlow(root.child(".info/serverTimeOffset")) { it.getValue(Long::class.java) ?: 0L }
        return combine(connected, offset) { isConnected, serverOffset -> Connection(isConnected, serverOffset) }
    }

    private fun <T> valueFlow(ref: DatabaseReference, read: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(read(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Listener on ${ref.path} cancelled: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun observe(ref: DatabaseReference): Flow<List<ChatMessage>> = callbackFlow {
        val query = ref.limitToLast(HISTORY_LIMIT)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull(::decode).sortedBy { it.id })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Chat listener cancelled: ${error.message}")
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    private fun decode(snapshot: DataSnapshot): ChatMessage? {
        val text = snapshot.child("text").getValue(String::class.java) ?: return null
        val role = when (snapshot.child("role").getValue(String::class.java)) {
            "user" -> ChatRole.USER
            "assistant" -> ChatRole.ASSISTANT
            else -> return null
        }
        return ChatMessage(
            id = snapshot.key ?: return null,
            role = role,
            text = text,
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            status = snapshot.child("status").getValue(String::class.java),
            activity = snapshot.child("activity").getValue(String::class.java),
            activityText = snapshot.child("activityText").getValue(String::class.java)
        )
    }

    private fun decodeChat(snapshot: DataSnapshot): ChatSummary? {
        val id = snapshot.key ?: return null
        fun long(name: String) = snapshot.child(name).getValue(Long::class.java) ?: 0L
        val createdAt = long("createdAt")
        return ChatSummary(
            id = id,
            title = snapshot.child("title").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                ?: ChatSummary.DEFAULT_TITLE,
            createdAt = createdAt,
            updatedAt = long("updatedAt").takeIf { it > 0 } ?: createdAt,
            state = snapshot.child("state").getValue(String::class.java),
            contextTokens = long("contextTokens"),
            contextWindow = long("contextWindow"),
            contextPercent = long("contextPercent").toInt().coerceIn(0, 100),
            model = snapshot.child("model").getValue(String::class.java),
            lastActiveAt = long("lastActiveAt"),
            idleMinutes = long("idleMinutes").toInt(),
            closedReason = snapshot.child("closedReason").getValue(String::class.java),
            modelChoice = snapshot.child("modelChoice").getValue(String::class.java),
            effortChoice = snapshot.child("effortChoice").getValue(String::class.java)
        )
    }

    private fun decodeCatalog(snapshot: DataSnapshot): ModelCatalog {
        val models = snapshot.child("models").children.mapNotNull { entry ->
            val value = entry.child("value").getValue(String::class.java) ?: return@mapNotNull null
            ModelOption(
                value = value,
                name = entry.child("name").getValue(String::class.java) ?: value,
                description = entry.child("description").getValue(String::class.java).orEmpty(),
                resolved = entry.child("resolved").getValue(String::class.java).orEmpty(),
                efforts = entry.child("efforts").children.mapNotNull { it.getValue(String::class.java) }
            )
        }
        return ModelCatalog(models, snapshot.child("at").getValue(Long::class.java) ?: 0L)
    }

    /**
     * The models and effort levels Claude offers, live. Published by the listener from Claude's own
     * answer, so it is empty until the listener has run once, and follows Claude as it changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun modelCatalog(): Flow<ModelCatalog> =
        settingsRepository.manualSyncId
            .distinctUntilChanged()
            .map { chatRef().child(MODELS_KEY) }
            .flatMapLatest { ref -> valueFlow(ref, ::decodeCatalog) }

    /**
     * Sets the model and effort for a chat that already exists; null for either means "Claude's
     * default". The listener reads this before each message and, if it changed, reopens the chat's
     * session with the new settings -- the conversation carries over.
     */
    suspend fun setChoice(chatId: String, model: String?, effort: String?) {
        chatRef().child(CHATS_KEY).child(chatId)
            .updateChildren(mapOf("modelChoice" to model, "effortChoice" to effort))
            .await()
    }

    /**
     * A fresh chat's id. Nothing is written: a chat exists once its first message is sent, so backing
     * out of an empty one leaves nothing behind in the list.
     */
    suspend fun newChatId(): String = chatRef().child(CHATS_KEY).push().key ?: error("No chat id")

    /**
     * Posts a user message to [chatId]. Returns once the write is queued locally, not acknowledged
     * by the server, so the message shows at once (as "sending") and goes out when the phone is next
     * online. The server's answer arrives later: success clears "sending", and a refusal is
     * reported on [sendFailures] with the text so it can be given back.
     *
     * The first message of a chat also creates it, titled from that message. The message and the
     * chat's own details go in one atomic update, and the listener's fields (state, context use) are
     * separate children, so neither side overwrites the other.
     */
    suspend fun send(
        chatId: String,
        text: String,
        firstMessage: Boolean,
        /** A model/effort picked before the chat existed: it is created with them. */
        model: String? = null,
        effort: String? = null
    ) {
        val root = chatRef()
        val key = root.child(MESSAGES_KEY).child(chatId).push().key ?: return
        val now = System.currentTimeMillis()
        unconfirmed.update { it + key }
        val updates = mutableMapOf<String, Any?>(
            "$MESSAGES_KEY/$chatId/$key" to mapOf(
                "role" to "user",
                "text" to text,
                "createdAt" to now,
                "status" to ChatMessage.STATUS_PENDING
            ),
            "$CHATS_KEY/$chatId/updatedAt" to now
        )
        if (firstMessage) {
            updates["$CHATS_KEY/$chatId/title"] = titleFrom(text)
            updates["$CHATS_KEY/$chatId/createdAt"] = now
            if (model != null) updates["$CHATS_KEY/$chatId/modelChoice"] = model
            if (effort != null) updates["$CHATS_KEY/$chatId/effortChoice"] = effort
        }
        root.updateChildren(updates).addOnCompleteListener { task ->
            unconfirmed.update { it - key }
            if (!task.isSuccessful) {
                val reason = task.exception?.message ?: "The server refused it."
                Log.w(TAG, "Chat write refused: $reason")
                _sendFailures.tryEmit(SendFailure(text, reason))
            }
        }
    }

    /**
     * Removes a chat: its messages and its details together. The listener is not told, so a session
     * it still has open for the chat simply idles out; nothing can reopen it, as its messages are gone.
     * The listener's own heartbeat is a sibling of both and is left alone.
     */
    suspend fun deleteChat(chatId: String) {
        chatRef().updateChildren(
            mapOf("$CHATS_KEY/$chatId" to null, "$MESSAGES_KEY/$chatId" to null)
        ).await()
    }

    companion object {
        private const val TAG = "ChatRepository"
        const val NODE = "agent_chat"

        /** Where each chat's details (title, state, context use) and its messages live under [NODE]. */
        const val CHATS_KEY = "chats"
        const val MESSAGES_KEY = "messages"

        /** The listener's published list of models and effort levels. Like [LISTENER_KEY], not a chat. */
        const val MODELS_KEY = "_models"

        /** Not a message: `decode` skips it because it has no text. Sorts after every push id. */
        const val LISTENER_KEY = "_listener"
        const val HISTORY_LIMIT = 200
        private const val TITLE_LENGTH = 40

        /** A chat's title from its first message: the first line, cut short. */
        fun titleFrom(text: String): String {
            val line = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            return when {
                line.isEmpty() -> ChatSummary.DEFAULT_TITLE
                line.length <= TITLE_LENGTH -> line
                else -> line.take(TITLE_LENGTH).trimEnd() + "…"
            }
        }
    }
}
