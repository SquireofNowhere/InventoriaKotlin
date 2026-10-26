package com.inventoria.app.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRepository
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.Connection
import com.inventoria.app.data.ListenerPresence
import com.inventoria.app.data.ModelCatalog
import com.inventoria.app.data.ModelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The model and effort picked for a chat; null for either leaves it to Claude's default. */
data class ModelChoice(val model: String?, val effort: String?)

/** One chat with Claude, named by the `chatId` navigation argument. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"]) { "ChatScreen needs a chatId" }

    private val _error = MutableStateFlow<String?>(null)
    /** A listener or write failure worth telling the user about; cleared by the next send. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = repository.messages(chatId)
        .catch { e ->
            Log.w(TAG, "Chat stream failed", e)
            _error.value = e.message ?: "Could not read the chat."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This chat's details from the listener (is its session open, how full is its context). Null
     * until the chat has been sent a message, and before the first reading. */
    val chat: StateFlow<ChatSummary?> = repository.chats()
        .map { chats -> chats.firstOrNull { it.id == chatId } }
        .catch { e ->
            Log.w(TAG, "Chat details stream failed", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The models and effort levels Claude offers, as the listener published them; empty until it has. */
    val catalog: StateFlow<ModelCatalog> = repository.modelCatalog()
        .catch { e ->
            Log.w(TAG, "Model list stream failed", e)
            emit(ModelCatalog.EMPTY)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelCatalog.EMPTY)

    /** A choice made before the chat exists (it has no details to store it in until its first message). */
    private val draftChoice = MutableStateFlow(ModelChoice(null, null))

    /** The model and effort in effect: the chat's stored choice, or the draft while it has none. */
    val choice: StateFlow<ModelChoice> = combine(chat, draftChoice) { stored, draft ->
        if (stored != null) ModelChoice(stored.modelChoice, stored.effortChoice) else draft
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelChoice(null, null))

    /** The listener's heartbeat; null until the first reading, so "checking" is not mistaken for "gone". */
    val presence: StateFlow<ListenerPresence?> = repository.listenerPresence()
        .map<ListenerPresence, ListenerPresence?> { it }
        .catch { e ->
            Log.w(TAG, "Presence stream failed", e)
            emit(ListenerPresence.NEVER)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connection: StateFlow<Connection> = repository.connection()
        .catch { e ->
            Log.w(TAG, "Connection stream failed", e)
            emit(Connection.UNKNOWN)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Connection.UNKNOWN)

    init {
        // A refused write rolls the message back out of the list, so say why and give the text back.
        viewModelScope.launch {
            repository.sendFailures.collect { failure ->
                _error.value = "That message wasn't sent: ${failure.reason}"
                if (_input.value.isEmpty()) _input.value = failure.text
            }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty()) return
        _input.value = ""
        _error.value = null
        // The first message creates the chat, so it needs to know it is the first.
        val first = messages.value.isEmpty()
        val draft = draftChoice.value
        viewModelScope.launch {
            try {
                repository.send(chatId, text, firstMessage = first, model = draft.model, effort = draft.effort)
            } catch (e: Exception) {
                Log.w(TAG, "Sending failed", e)
                _error.value = e.message ?: "Could not send that."
                // Hand the text back so a failed send is not lost.
                if (_input.value.isEmpty()) _input.value = text
            }
        }
    }

    /**
     * Picks a model from the catalog ([ModelOption.DEFAULT_VALUE] or null for Claude's default). An
     * effort that the new model doesn't take is dropped. Takes effect from the next message: the
     * listener reopens this chat's session on the new model, and the conversation carries over.
     */
    fun chooseModel(value: String?) {
        val model = value?.takeIf { it != ModelOption.DEFAULT_VALUE }
        storeChoice(model, effortFor(catalog.value, model, choice.value.effort))
    }

    /** Picks an effort level (null for the default) from the ones the chosen model takes. */
    fun chooseEffort(level: String?) {
        storeChoice(choice.value.model, level)
    }

    private fun storeChoice(model: String?, effort: String?) {
        if (chat.value == null) {
            draftChoice.value = ModelChoice(model, effort)
            return
        }
        viewModelScope.launch {
            try {
                repository.setChoice(chatId, model, effort)
            } catch (e: Exception) {
                Log.w(TAG, "Changing the model failed", e)
                _error.value = e.message ?: "Could not change the model."
            }
        }
    }

    /** Deletes this chat; [onDone] runs once it is gone, so the screen can leave it. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
                onDone()
            } catch (e: Exception) {
                Log.w(TAG, "Deleting failed", e)
                _error.value = e.message ?: "Could not delete the chat."
            }
        }
    }

    /** Makes the id of a new chat to move on to, for when this one is getting full. */
    fun startNewChat(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(repository.newChatId())
            } catch (e: Exception) {
                Log.w(TAG, "Starting a new chat failed", e)
                _error.value = e.message ?: "Could not start a new chat."
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
