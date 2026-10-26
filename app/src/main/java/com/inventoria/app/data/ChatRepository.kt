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
     * The newest [HISTORY_LIMIT] messages, oldest first, live. Re-attaches if the synced vault
     * changes. A listener failure (permission denied, say) closes the flow with the exception, so
     * the collector must catch it -- see ErrorLog #19 for what an uncaught one does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun messages(): Flow<List<ChatMessage>> =
        settingsRepository.manualSyncId
            .distinctUntilChanged()
            .map { chatRef() }
            .flatMapLatest { ref -> observe(ref) }
            .let { stored ->
                combine(stored, unconfirmed) { list, pending ->
                    if (pending.isEmpty()) list else list.map { if (it.id in pending) it.copy(sending = true) else it }
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

    /**
     * Posts a user message. Returns once the write is queued locally, not acknowledged by the
     * server, so the message shows at once (as "sending") and goes out when the phone is next
     * online. The server's answer arrives later: success clears "sending", and a refusal is
     * reported on [sendFailures] with the text so it can be given back.
     */
    suspend fun send(text: String) {
        val ref = chatRef()
        val key = ref.push().key ?: return
        unconfirmed.update { it + key }
        ref.child(key).setValue(
            mapOf(
                "role" to "user",
                "text" to text,
                "createdAt" to System.currentTimeMillis(),
                "status" to ChatMessage.STATUS_PENDING
            )
        ).addOnCompleteListener { task ->
            unconfirmed.update { it - key }
            if (!task.isSuccessful) {
                val reason = task.exception?.message ?: "The server refused it."
                Log.w(TAG, "Chat write refused: $reason")
                _sendFailures.tryEmit(SendFailure(text, reason))
            }
        }
    }

    /** Wipes the whole conversation, answers included -- but not the listener's heartbeat, which is
     * not part of it and would otherwise read as "PC offline" until its next beat. */
    suspend fun clear() {
        val ref = chatRef()
        val keys = ref.get().await().children.mapNotNull { it.key }.filter { it != LISTENER_KEY }
        if (keys.isNotEmpty()) ref.updateChildren(keys.associateWith { null }).await()
    }

    companion object {
        private const val TAG = "ChatRepository"
        const val NODE = "agent_chat"

        /** Not a message: `decode` skips it because it has no text. Sorts after every push id. */
        const val LISTENER_KEY = "_listener"
        const val HISTORY_LIMIT = 200
    }
}
