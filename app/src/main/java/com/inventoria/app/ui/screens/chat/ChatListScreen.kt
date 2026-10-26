package com.inventoria.app.ui.screens.chat

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.ContextLevel
import kotlinx.coroutines.delay

/**
 * Every chat with Claude, newest activity first. Each one is its own conversation with its own session
 * on the PC, so the list says how full each one is and whether its session is open or asleep -- which
 * is how you tell which chats are due for replacing.
 *
 * A chat only appears once its first message is sent: [onOpenChat] with a new id opens an empty one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenChat: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val chats by viewModel.chats.collectAsState()
    val error by viewModel.error.collectAsState()
    val presence by viewModel.presence.collectAsState()
    val connection by viewModel.connection.collectAsState()
    var pendingDelete by remember { mutableStateOf<ChatSummary?>(null) }
    var pendingRename by remember { mutableStateOf<ChatSummary?>(null) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val link = linkState(presence, connection, now)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat with Claude", fontWeight = FontWeight.Bold)
                        LinkLine(link)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.newChat(onOpenChat) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New chat") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            val list = chats
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                list.isEmpty() -> EmptyChatList()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Room under the last row for the button.
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            now = now,
                            onClick = { onOpenChat(chat.id) },
                            onRename = { pendingRename = chat },
                            onDelete = { pendingDelete = chat }
                        )
                    }
                }
            }
        }
    }

    pendingRename?.let { chat ->
        RenameChatDialog(
            currentTitle = chat.title,
            onRename = { viewModel.rename(chat.id, it) },
            onDismiss = { pendingRename = null }
        )
    }

    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("\"${chat.title}\" and every answer in it are removed. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(chat.id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyChatList() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No chats yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            "Ask about your todos, items and time, or have things added and changed. Each chat is its own " +
                "conversation, answered from your PC, so the agent listener needs to be running there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, now: Long, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val levelColor = when (chat.contextLevel) {
        ContextLevel.FINE -> MaterialTheme.colorScheme.primary
        ContextLevel.GETTING_FULL -> MaterialTheme.colorScheme.tertiary
        ContextLevel.FULL -> MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        DateUtils.getRelativeTimeSpanString(chat.updatedAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                        if (chat.isLive) "session open" else "asleep"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (chat.contextTokens > 0) {
                    Text(
                        contextLine(chat),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (chat.contextLevel == ContextLevel.FINE) FontWeight.Normal else FontWeight.Bold,
                        color = levelColor
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename chat")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete chat")
            }
        }
    }
}
