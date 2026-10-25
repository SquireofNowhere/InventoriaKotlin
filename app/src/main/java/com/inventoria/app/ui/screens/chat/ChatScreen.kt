package com.inventoria.app.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRole
import kotlinx.coroutines.delay

/** How long a message can sit unanswered before the screen suggests the PC side is not running. */
private const val SLOW_REPLY_MILLIS = 60_000L

/**
 * A chat with Claude. Messages are posted to the vault's database and answered by the agent
 * listener on the user's PC (tools/agent-listener), which runs Claude on their own login -- so this
 * screen only ever shows a conversation; it never talks to a model itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val error by viewModel.error.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    val waitingOn = messages.lastOrNull()?.takeIf { it.isAwaitingReply }

    // Re-read the clock while a reply is awaited, so the "taking a while" hint can appear on its own.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitingOn?.id) {
        while (waitingOn != null) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, waitingOn?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (waitingOn != null) 1 else 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyChat()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { message -> Bubble(message) }
                        if (waitingOn != null) {
                            item(key = "waiting") {
                                WaitingRow(slow = waitingOn.status == ChatMessage.STATUS_PENDING &&
                                    now - waitingOn.createdAt > SLOW_REPLY_MILLIS)
                            }
                        }
                    }
                }
            }

            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your vault") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = viewModel::send, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this chat?") },
            text = { Text("Every message and answer is removed. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyChat() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Chat with Claude", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            "Ask about your todos, items and time, or have things added and changed. " +
                "Claude answers from your PC, so the agent listener needs to be running there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val fromUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                SelectionContainer {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (fromUser && message.status == "error") {
                    Text(
                        "Not answered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingRow(slow: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (slow) {
                "Nothing has picked this up yet. Is the agent listener running on your PC?"
            } else {
                "Claude is thinking…"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
