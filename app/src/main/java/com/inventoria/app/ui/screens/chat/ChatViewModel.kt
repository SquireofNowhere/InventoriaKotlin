package com.inventoria.app.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    /** A listener or write failure worth telling the user about; cleared by the next send. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = repository.messages()
        .catch { e ->
            Log.w(TAG, "Chat stream failed", e)
            _error.value = e.message ?: "Could not read the chat."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty()) return
        _input.value = ""
        _error.value = null
        viewModelScope.launch {
            try {
                repository.send(text)
            } catch (e: Exception) {
                Log.w(TAG, "Sending failed", e)
                _error.value = e.message ?: "Could not send that."
                // Hand the text back so a failed send is not lost.
                if (_input.value.isEmpty()) _input.value = text
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            try {
                repository.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Clearing failed", e)
                _error.value = e.message ?: "Could not clear the chat."
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
