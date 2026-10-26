package com.inventoria.app.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRole
import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.ContextLevel
import com.inventoria.app.data.ModelCatalog
import com.inventoria.app.data.ModelOption
import kotlinx.coroutines.delay

/** The widest the conversation gets: wide enough to read comfortably, narrow enough that a tablet's
 * composer sits beside the text. */
private val CHAT_COLUMN_MAX_WIDTH = 720.dp

/** Starter prompts on an empty chat; tapping one fills the message box rather than sending it. */
private val SUGGESTIONS = listOf(
    "What's on my plate today?",
    "Where did my time go this week?",
    "Add a todo for tomorrow",
    "What's in my vault?"
)

/**
 * One chat with Claude, laid out like the Claude mobile app: a quiet top bar, replies as plain text
 * on the page with a sparkle under them, your own messages in a soft rounded bubble, and a rounded
 * composer with the model on a chip beside the send button. Colours all come from the app's theme.
 *
 * Messages are posted to the vault's database and answered by the agent listener on the user's PC
 * (tools/agent-listener), which keeps a Claude session per chat on their own login -- so this screen
 * only ever shows a conversation; it never talks to a model itself. It also shows what the listener
 * reports about this chat's session: whether it is open, when it will close, and how full its context
 * is (in the details sheet), with a nudge to start a new chat as it fills.
 *
 * [onNewChat] is given the id of a fresh chat to move on to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNewChat: (String) -> Unit,
    /** Opens the todo, item, task or block a reply linked to. */
    onOpenLink: (ChatLink) -> Unit = {}
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
    var showDetails by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showDetails = true }
                    ) {
                        Text(
                            chat?.title ?: ChatSummary.DEFAULT_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.startNewChat(onNewChat) }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Model and details") },
                                onClick = { showMenu = false; showDetails = true }
                            )
                            if (messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Rename chat") },
                                    onClick = { showMenu = false; showRename = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete chat", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; confirmClear = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        // On a tablet the chat is a column down the middle of the page, like Claude's own apps, not
        // the full width: the composer and its send button stay beside the text instead of at the
        // far edge of the screen. On a phone the cap is wider than the screen and changes nothing.
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = CHAT_COLUMN_MAX_WIDTH)
                .fillMaxWidth()
        ) {
            contextAdvice(chat?.contextLevel ?: ContextLevel.FINE)?.let { advice ->
                ContextAdvice(
                    text = advice,
                    full = chat?.contextLevel == ContextLevel.FULL,
                    onNewChat = { viewModel.startNewChat(onNewChat) }
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyChat(onSuggestion = viewModel::onInputChange)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(messages, key = { it.id }) { message -> Message(message, onOpenLink) }
                        if (progress != null) {
                            item(key = "progress") { ProgressRow(progress) }
                        }
                    }
                }
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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

            Composer(
                input = input,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                modelLine = choiceLabel(catalog, choice.model, choice.effort),
                onModelClick = { showDetails = true }
            )
        }
        }
    }

    if (showDetails) {
        ChatDetailsSheet(
            chat = chat,
            serverNow = now + connection.serverOffset,
            catalog = catalog,
            choice = choice,
            onModel = viewModel::chooseModel,
            onEffort = viewModel::chooseEffort,
            onDismiss = { showDetails = false }
        )
    }

    if (showRename) {
        RenameChatDialog(
            currentTitle = chat?.title ?: "",
            onRename = viewModel::rename,
            onDismiss = { showRename = false }
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

/** The greeting before the first message, with a few prompts to start from. */
@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask about your todos, items and time, or have things added and changed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            SUGGESTIONS.forEach { suggestion ->
                Surface(
                    onClick = { onSuggestion(suggestion) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Claude answers from your PC, so the agent listener needs to be running there.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Yours in a bubble on the right; Claude's as plain text across the page, signed with a sparkle. */
@Composable
private fun Message(message: ChatMessage, onOpenLink: (ChatLink) -> Unit) {
    val fromUser = message.role == ChatRole.USER
    // Where this one message has got to: sending, sent, being worked on, or not answered.
    val delivery = deliveryLabel(message)
    val deliveryColor = if (message.status == "error") MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    if (fromUser) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                SelectionContainer {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            delivery?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = deliveryColor,
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp)
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Claude writes markdown; draw it (bold, lists, code, links) rather than its raw characters.
            SelectionContainer {
                ChatMarkdownText(message.text, onLink = onOpenLink)
            }
            delivery?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = deliveryColor, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp).size(18.dp)
            )
        }
    }
}

/**
 * The message box: a rounded card with the text on top and, underneath, the model chip on the left
 * and the round send button on the right. The chip opens the details sheet where the model and effort
 * are picked.
 */
@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modelLine: String,
    onModelClick: () -> Unit
) {
    val canSend = input.isNotBlank()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                if (input.isEmpty()) {
                    Text(
                        "Message Claude",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onModelClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modelLine,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Model and effort", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = canSend, onClick = onSend)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
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
 * This chat's model and effort, and how full it is. The model and effort lists are Claude's own,
 * published by the agent listener -- nothing here knows what exists -- and the effort choices are
 * those the picked model takes. A change applies from the next message: the listener reopens the
 * chat's session with the new settings and the conversation carries over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailsSheet(
    chat: ChatSummary?,
    serverNow: Long,
    catalog: ModelCatalog,
    choice: ModelChoice,
    onModel: (String?) -> Unit,
    onEffort: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (chat != null) {
                ContextCard(chat, serverNow = serverNow, modelLine = choiceLabel(catalog, choice.model, choice.effort))
                Spacer(Modifier.height(20.dp))
            }
            if (catalog.isEmpty) {
                Text("Model and effort", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "The list of models appears once the agent listener has started on your PC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text("Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.height(12.dp))
            Text("Effort", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (efforts.isEmpty()) {
                Text(
                    "This model has no effort setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
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
            Spacer(Modifier.height(12.dp))
            Text(
                "Applies from your next message. Claude reopens this chat on the new settings and " +
                    "remembers the conversation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * How full this chat's context is, and whether its session on the PC is open: the two things the
 * user needs to decide whether to carry on here or start over.
 */
@Composable
private fun ContextCard(chat: ChatSummary, serverNow: Long, modelLine: String) {
    val color = when (chat.contextLevel) {
        ContextLevel.FINE -> MaterialTheme.colorScheme.primary
        ContextLevel.GETTING_FULL -> MaterialTheme.colorScheme.tertiary
        ContextLevel.FULL -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contextLine(chat),
                    style = MaterialTheme.typography.labelLarge,
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
                progress = { chat.contextPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        shape = RoundedCornerShape(16.dp),
        color = if (full) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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
            // Claude's own "thinking" mark: the sparkle, breathing.
            val pulse by rememberInfiniteTransition(label = "thinking").animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "thinkingAlpha"
            )
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).alpha(pulse)
            )
        } else {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(progress.headline, style = MaterialTheme.typography.bodyMedium, color = color)
            progress.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
