package com.inventoria.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRole
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.ContextLevel
import com.inventoria.app.data.ModelCatalog
import com.inventoria.app.data.ModelOption
import kotlinx.coroutines.delay

/**
 * One chat with Claude. Messages are posted to the vault's database and answered by the agent
 * listener on the user's PC (tools/agent-listener), which keeps a Claude session per chat on their own
 * login -- so this screen only ever shows a conversation; it never talks to a model itself. It also
 * shows what the listener reports about this chat's session: whether it is open, when it will close,
 * and how full its context is, with a nudge to start a new chat as it fills.
 *
 * [onNewChat] is given the id of a fresh chat to move on to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNewChat: (String) -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val error by viewModel.error.collectAsState()
    val presence by viewModel.presence.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val chat by viewModel.chat.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val choice by viewModel.choice.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    val waitingOn = messages.lastOrNull()?.takeIf { it.isAwaitingReply }

    // Re-read the clock every few seconds, so the elapsed time, the "taking a while" hint and the
    // listener's heartbeat going stale all show up on their own -- not only when a database event
    // happens to arrive.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitingOn?.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (waitingOn != null) 1_000 else 5_000)
        }
    }
    val link = linkState(presence, connection, now)
    val progress = chatProgress(waitingOn, presence, connection, now)

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, waitingOn?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (waitingOn != null) 1 else 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chat?.title ?: ChatSummary.DEFAULT_TITLE,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinkLine(link)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Model and effort")
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Delete chat")
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
            ContextStrip(
                chat,
                serverNow = now + connection.serverOffset,
                modelLine = choiceLabel(catalog, choice.model, choice.effort)
            )
            contextAdvice(chat?.contextLevel ?: ContextLevel.FINE)?.let { advice ->
                ContextAdvice(
                    text = advice,
                    full = chat?.contextLevel == ContextLevel.FULL,
                    onNewChat = { viewModel.startNewChat(onNewChat) }
                )
            }
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
                        if (progress != null) {
                            item(key = "progress") { ProgressRow(progress) }
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

    if (showPicker) {
        ModelPicker(
            catalog = catalog,
            choice = choice,
            onModel = viewModel::chooseModel,
            onEffort = viewModel::chooseEffort,
            onDismiss = { showPicker = false }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete this chat?") },
            text = { Text("Every message and answer in it is removed. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.delete(onNavigateBack) }) { Text("Delete") }
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
                // Where this one message has got to: sending, sent, being worked on, or not answered.
                deliveryLabel(message)?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.status == "error") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/** The line under the title: is Claude's PC there to answer at all. */
@Composable
internal fun LinkLine(link: LinkState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (link.isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            link.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Pick the model and effort for this chat. Both lists are Claude's own, published by the agent
 * listener -- nothing here knows what exists -- and the effort choices are those the picked model
 * takes. A change applies from the next message: the listener reopens the chat's session with the new
 * settings and the conversation carries over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    catalog: ModelCatalog,
    choice: ModelChoice,
    onModel: (String?) -> Unit,
    onEffort: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model and effort") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (catalog.isEmpty) {
                    Text(
                        "The list of models appears once the agent listener has started on your PC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@Column
                }
                Text("Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val selected = choice.model ?: ModelOption.DEFAULT_VALUE
                catalog.models.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModel(option.value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option.value == selected, onClick = { onModel(option.value) })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(option.name, style = MaterialTheme.typography.bodyMedium)
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                val efforts = effortOptions(catalog, choice.model)
                Spacer(Modifier.size(12.dp))
                Text("Effort", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (efforts.isEmpty()) {
                    Text(
                        "This model has no effort setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = choice.effort == null,
                            onClick = { onEffort(null) },
                            label = { Text("Default") }
                        )
                        efforts.forEach { level ->
                            FilterChip(
                                selected = choice.effort == level,
                                onClick = { onEffort(level) },
                                label = { Text(effortLabel(level)) }
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    "Applies from your next message. Claude reopens this chat on the new settings and " +
                        "remembers the conversation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/**
 * How full this chat's context is, and whether its session on the PC is open: the two things the
 * user needs to decide whether to carry on here or start over.
 */
@Composable
private fun ContextStrip(chat: ChatSummary?, serverNow: Long, modelLine: String) {
    val level = chat?.contextLevel ?: ContextLevel.FINE
    val color = when (level) {
        ContextLevel.FINE -> MaterialTheme.colorScheme.primary
        ContextLevel.GETTING_FULL -> MaterialTheme.colorScheme.tertiary
        ContextLevel.FULL -> MaterialTheme.colorScheme.error
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contextLine(chat),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    modelLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LinearProgressIndicator(
                progress = { (chat?.contextPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                sessionLine(chat, serverNow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The nudge to move to a new chat as this one fills. */
@Composable
private fun ContextAdvice(text: String, full: Boolean, onNewChat: () -> Unit) {
    Surface(
        color = if (full) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (full) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onNewChat) { Text("New chat") }
        }
    }
}

/** What is happening to the newest unanswered message, or what to check if it is stuck. */
@Composable
private fun ProgressRow(progress: ChatProgress) {
    val warning = progress.tone == ProgressTone.WARNING
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top) {
        if (progress.busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(top = 2.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                tint = color
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(progress.headline, style = MaterialTheme.typography.bodySmall, color = color)
            progress.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
