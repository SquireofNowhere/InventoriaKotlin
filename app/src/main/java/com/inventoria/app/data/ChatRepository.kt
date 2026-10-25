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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class ChatRole { USER, ASSISTANT }

/**
 * One line of the Claude chat. [status] only means something on a user message, and is written by
 * whatever answers it (tools/agent-listener): "pending" until picked up, "working" while Claude runs,
 * then "done" or "error". The app writes "pending" and never changes it again.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val createdAt: Long,
    val status: String?
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
            status = snapshot.child("status").getValue(String::class.java)
        )
    }

    /**
     * Posts a user message. Returns once the write is queued locally, not acknowledged by the
     * server, so the message shows at once and goes out when the phone is next online.
     */
    suspend fun send(text: String) {
        val ref = chatRef()
        val key = ref.push().key ?: return
        ref.child(key).setValue(
            mapOf(
                "role" to "user",
                "text" to text,
                "createdAt" to System.currentTimeMillis(),
                "status" to ChatMessage.STATUS_PENDING
            )
        )
    }

    /** Wipes the whole conversation, answers included. */
    suspend fun clear() {
        chatRef().removeValue().await()
    }

    companion object {
        private const val TAG = "ChatRepository"
        const val NODE = "agent_chat"
        const val HISTORY_LIMIT = 200
    }
}
