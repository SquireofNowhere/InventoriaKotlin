package com.inventoria.app.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.ChatRepository
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.Connection
import com.inventoria.app.data.ListenerPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The list of chats with Claude. */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Null until the first reading, so an empty list is not shown while it is still loading. */
    val chats: StateFlow<List<ChatSummary>?> = repository.chats()
        .map<List<ChatSummary>, List<ChatSummary>?> { it }
        .catch { e ->
            Log.w(TAG, "Chat list failed", e)
            _error.value = e.message ?: "Could not read your chats."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val presence: StateFlow<ListenerPresence?> = repository.listenerPresence()
        .map<ListenerPresence, ListenerPresence?> { it }
        .catch { emit(ListenerPresence.NEVER) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connection: StateFlow<Connection> = repository.connection()
        .catch { emit(Connection.UNKNOWN) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Connection.UNKNOWN)

    /** Makes the id of a new chat; nothing is stored until its first message is sent. */
    fun newChat(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(repository.newChatId())
            } catch (e: Exception) {
                Log.w(TAG, "Starting a chat failed", e)
                _error.value = e.message ?: "Could not start a new chat."
            }
        }
    }

    fun delete(chatId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
            } catch (e: Exception) {
                Log.w(TAG, "Deleting failed", e)
                _error.value = e.message ?: "Could not delete the chat."
            }
        }
    }

    fun rename(chatId: String, title: String) {
        viewModelScope.launch {
            try {
                repository.renameChat(chatId, title)
            } catch (e: Exception) {
                Log.w(TAG, "Renaming failed", e)
                _error.value = e.message ?: "Could not rename the chat."
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "ChatListViewModel"
    }
}
