package com.inventoria.app.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.InviteCode
import kotlinx.coroutines.delay
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.main.Screen
import com.inventoria.app.ui.screens.task.TodoPriorityDropdownMenu
import com.inventoria.app.ui.splash.SplashActivity
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToTaskTypes: () -> Unit,
    /** The top bar's "?": the Settings section of the manual. */
    onNavigateToHelp: () -> Unit,
    /** The "How To" row further down: the manual's index, as that row has always meant. */
    onNavigateToHelpIndex: () -> Unit
) {
    val context = LocalContext.current
    val focusArea by viewModel.focusArea.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val showValueOnDashboard by viewModel.showValueOnDashboard.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val authOperation by viewModel.authOperation.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val customUsername by viewModel.customUsername.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val autoCurrencyEnabled by viewModel.autoCurrencyEnabled.collectAsState()
    val manualSyncId by viewModel.manualSyncId.collectAsState()
    val generatedInviteCode by viewModel.generatedInviteCode.collectAsState()
    val inviteCodeError by viewModel.inviteCodeError.collectAsState()
    val inviteCodeGenerationError by viewModel.inviteCodeGenerationError.collectAsState()
    val sharedWithUsers by viewModel.sharedWithUsers.collectAsState()
    val innerTaskEnabled by viewModel.innerTaskEnabled.collectAsState()
    val procrastinationTodoEnabled by viewModel.procrastinationTodoEnabled.collectAsState()
    val procrastinationTodoCutoff by viewModel.procrastinationTodoCutoff.collectAsState()
    val procrastinationTaskEnabled by viewModel.procrastinationTaskEnabled.collectAsState()
    val procrastinationTaskKinds by viewModel.procrastinationTaskKinds.collectAsState()
    val procrastinationPenaltyAmount by viewModel.procrastinationPenaltyAmount.collectAsState()

    // A wipe leaves every other screen in the back stack rendering the account that no longer
    // exists, and the singletons that would create a replacement only run their init once per
    // process. Restarting at the splash is what actually gives the device a fresh account.
    LaunchedEffect(Unit) {
        viewModel.accountWiped.collect {
            context.startActivity(
                Intent(context, SplashActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
            (context as? Activity)?.finish()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.onGoogleSignInSuccess(idToken)
            } else {
                Log.e("SettingsScreen", "ID Token is NULL")
            }
        } catch (e: ApiException) {
            Log.e("SettingsScreen", "Google Sign In Failed", e)
            Toast.makeText(context, "Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            InventoriaTopBar(
                title = Screen.Settings.title,
                onNavigateToHelp = onNavigateToHelp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First on purpose: focus reshapes the whole app (tab order, dashboard), so it
            // shouldn't hide below settings that only touch one screen.
            SettingsCategoryHeader("Focus")
            FocusSettings(
                selected = focusArea,
                onSelect = { viewModel.setFocusArea(it) }
            )

            SettingsCategoryHeader("Appearance")
            SettingsToggleRow(
                title = "Dark Mode",
                subtitle = "Use dark theme across the app",
                icon = Icons.Default.Brightness4,
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            SettingsCategoryHeader("Localization")
            CurrencySettings(
                autoCurrency = autoCurrencyEnabled,
                selectedCurrency = currencyCode,
                onAutoCurrencyToggle = { viewModel.toggleAutoCurrency(it) },
                onCurrencySelect = { viewModel.updateCurrencyCode(it) }
            )

            SettingsCategoryHeader("Inventory")
            SettingsToggleRow(
                title = "Show Total Value",
                subtitle = "Display total inventory value in the Inventory tab and the Today summary card",
                icon = Icons.Default.AccountBalanceWallet,
                checked = showValueOnDashboard,
                onCheckedChange = { viewModel.toggleShowValue(it) }
            )

            SettingsCategoryHeader("Tasks")
            SettingsNavigationRow(
                title = "Task Types",
                subtitle = "Group tasks by activity -- \"Eating with V\" and \"Eating out\" can share the type \"Eating\" while keeping their own kinds",
                icon = Icons.Default.Category,
                onClick = onNavigateToTaskTypes
            )
            SettingsToggleRow(
                title = "Track Interruptions (Inner Tasks)",
                subtitle = "When pausing a task, start a linked inner task so you know exactly what interrupted you when you resume",
                icon = Icons.Default.NotificationImportant,
                checked = innerTaskEnabled,
                onCheckedChange = { viewModel.toggleInnerTask(it) }
            )
            ProcrastinationPenaltySettings(
                todoEnabled = procrastinationTodoEnabled,
                todoCutoff = procrastinationTodoCutoff,
                taskEnabled = procrastinationTaskEnabled,
                taskKinds = procrastinationTaskKinds,
                penaltyAmount = procrastinationPenaltyAmount,
                onToggleTodo = { viewModel.toggleProcrastinationTodo(it) },
                onCutoffSelected = { viewModel.setProcrastinationTodoCutoff(it) },
                onToggleTask = { viewModel.toggleProcrastinationTask(it) },
                onToggleTaskKind = { viewModel.toggleProcrastinationTaskKind(it) },
                onPenaltyAmountChange = { viewModel.setProcrastinationPenaltyAmount(it) }
            )

            SettingsCategoryHeader("Notifications")
            SettingsToggleRow(
                title = "Enable Notifications",
                subtitle = "Receive alerts for task timers and stock levels",
                icon = Icons.Default.Notifications,
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) }
            )

            SettingsCategoryHeader("Account & Sync")
            AccountSection(
                authState = authState,
                authOperation = authOperation,
                customUsername = customUsername,
                manualSyncId = manualSyncId,
                currentUserId = currentUserId,
                generatedInviteCode = generatedInviteCode,
                inviteCodeError = inviteCodeError,
                inviteCodeGenerationError = inviteCodeGenerationError,
                sharedWithUsers = sharedWithUsers,
                onUsernameChange = { viewModel.updateCustomUsername(it) },
                onSignInClick = {
                    launcher.launch(viewModel.getGoogleSignInIntent())
                },
                onSignOutClick = { viewModel.signOut() },
                onDismissAuthError = { viewModel.clearAuthState() },
                onDeleteAccountClick = { viewModel.deleteAccount() },
                onGenerateInviteCode = { viewModel.createInviteCode() },
                onRevokeInviteCode = { viewModel.revokeInviteCode() },
                onUseInviteCode = { viewModel.useInviteCode(it) },
                onClearSync = { viewModel.clearExternalSync() },
                onClearError = { viewModel.clearInviteCodeError() },
                onClearGenerationError = { viewModel.clearInviteCodeGenerationError() },
                onRevokeAccess = { viewModel.revokeSharedAccess(it) }
            )

            // Sits directly above About, in the same "about the app" band at the bottom -- putting
            // it at the top would push actual settings below the fold.
            SettingsCategoryHeader("Help")
            SettingsNavigationRow(
                title = "How To",
                subtitle = "Step-by-step guides for every feature, with diagrams",
                icon = Icons.Default.HelpOutline,
                onClick = onNavigateToHelpIndex
            )

            SettingsCategoryHeader("About")
            AboutCard(context)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * The current focus as a tappable Card row, opening the same three-way choice the launch prompt
 * offers -- one copy of the titles/descriptions, via FocusArea itself. The bar reorders live on
 * selection because InventoriaApp collects the same preference.
 */
@Composable
fun FocusSettings(
    selected: FocusArea,
    onSelect: (FocusArea) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("App Focus", fontWeight = FontWeight.Bold)
                Text(
                    "${selected.title} -- sits next to Today and leads the dashboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("App Focus") },
            text = {
                Column {
                    FocusArea.entries.forEach { area ->
                        ListItem(
                            headlineContent = { Text(area.title, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(area.description) },
                            trailingContent = {
                                if (area == selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onSelect(area)
                                showDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun CurrencySettings(
    autoCurrency: Boolean,
    selectedCurrency: String,
    onAutoCurrencyToggle: (Boolean) -> Unit,
    onCurrencySelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Currency", fontWeight = FontWeight.Bold)
                    Text("Pick currency based on location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoCurrency, onCheckedChange = onAutoCurrencyToggle)
            }

            if (!autoCurrency) {
                var showDialog by remember { mutableStateOf(false) }
                
                OutlinedTextField(
                    value = selectedCurrency,
                    onValueChange = {},
                    label = { Text("Selected Currency") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog = true },
                    readOnly = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )

                if (showDialog) {
                    val currencies = remember { 
                        Currency.getAvailableCurrencies()
                            .sortedBy { it.currencyCode }
                    }
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Select Currency") },
                        text = {
                            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    currencies.forEach { currency ->
                                        ListItem(
                                            headlineContent = { Text("${currency.currencyCode} - ${currency.displayName}") },
                                            modifier = Modifier.clickable {
                                                onCurrencySelect(currency.currencyCode)
                                                showDialog = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            } else {
                val localeCurrency = remember { 
                    try {
                        Currency.getInstance(Locale.getDefault()).currencyCode
                    } catch (e: Exception) {
                        "USD"
                    }
                }
                Text(
                    text = "System detected: $localeCurrency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProcrastinationPenaltySettings(
    todoEnabled: Boolean,
    todoCutoff: TodoPriority,
    taskEnabled: Boolean,
    taskKinds: Set<TaskKind>,
    penaltyAmount: Int,
    onToggleTodo: (Boolean) -> Unit,
    onCutoffSelected: (TodoPriority) -> Unit,
    onToggleTask: (Boolean) -> Unit,
    onToggleTaskKind: (TaskKind) -> Unit,
    onPenaltyAmountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Penalize Non-Priority Todos", fontWeight = FontWeight.Bold)
                    Text(
                        "Subtract points when you complete a Todo at or below this cutoff, or with no priority set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = todoEnabled, onCheckedChange = onToggleTodo)
            }
            if (todoEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cutoff tier: ", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    TodoPriorityDropdownMenu(
                        selectedPriority = todoCutoff,
                        onPrioritySelected = { it?.let(onCutoffSelected) },
                        allowUnset = false
                    )
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Penalize Procrastination Task Kinds", fontWeight = FontWeight.Bold)
                    Text(
                        "Subtract points whenever a tracked task of a flagged Kind is completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = taskEnabled, onCheckedChange = onToggleTask)
            }
            if (taskEnabled) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kind in taskKinds,
                            onClick = { onToggleTaskKind(kind) },
                            label = { Text(kind.displayName.split(" • ").last()) }
                        )
                    }
                }
            }

            HorizontalDivider()

            var penaltyText by remember(penaltyAmount) { mutableStateOf(penaltyAmount.toString()) }
            OutlinedTextField(
                value = penaltyText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 3) {
                        penaltyText = input
                        input.toIntOrNull()?.let(onPenaltyAmountChange)
                    }
                },
                label = { Text("Penalty Points") },
                supportingText = { Text("Points subtracted per qualifying completion") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * manualSyncId always wins over whatever's actually signed in (see FirebaseAuthRepository.
 * getOrCreateUserId()), so "signed in as X" and "synced to Y" can silently disagree. This makes
 * the one actually-active sync target unambiguous instead of showing them as two separate facts.
 */
@Composable
fun SyncStatusBanner(authState: AuthState, manualSyncId: String?, currentUserId: String?) {
    val (icon, title, subtitle, containerColor, contentColor) = when {
        // "Local account" and "no account at all" used to render identically, because this fell
        // through to the same else branch. They are not the same thing: signing out of Google, or
        // a failed anonymous sign-in, leaves the device with no account and nothing to sync to.
        currentUserId == null && manualSyncId == null -> SyncStatusVisuals(
            Icons.Default.CloudOff,
            "No Account Yet",
            "This device has no account, so there is nothing to sync to. Sign in, or restart the app to set up a local account.",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        manualSyncId != null -> {
            val signedInNote = (authState as? AuthState.Authenticated)?.user?.email?.let { " — not $it" } ?: ""
            SyncStatusVisuals(
                Icons.Default.Warning,
                "Synced to an External Account",
                "This device reads/writes someone else's database$signedInNote. Clear it below to go back to your own.",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
            )
        }
        authState is AuthState.Authenticated -> SyncStatusVisuals(
            Icons.Default.CloudDone,
            "Synced to Your Google Account",
            "${authState.user.email} — syncs across all your own signed-in devices.",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> SyncStatusVisuals(
            Icons.Default.PhoneAndroid,
            "Local Account",
            "Not signed in — data stays on this device only, unless you sign in or use an invite code.",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(color = containerColor, contentColor = contentColor, shape = MaterialTheme.shapes.small) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (manualSyncId != null) {
                Spacer(Modifier.height(8.dp))
                MaskedIdRow(label = "Connected to", id = manualSyncId, color = contentColor)
            }
        }
    }
}

/** Shows an account/device UID masked by default, with a toggle to reveal it -- IDs shouldn't sit in plaintext on screen by default. */
@Composable
fun MaskedIdRow(label: String, id: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    var revealed by remember(id) { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label: ${if (revealed) id else "•".repeat(minOf(id.length, 24))}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(28.dp)) {
            Icon(
                if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (revealed) "Hide ID" else "Show ID",
                tint = color
            )
        }
    }
}

/**
 * Wording for the delete button and its dialog, which must name the account that will *actually*
 * be deleted rather than the one whose data happens to be on screen.
 *
 * The button used to be a two-way choice keyed off an authState that could be stale, so a signed-in
 * Google user could be shown the local-account wording. There is deliberately no external-sync case:
 * delete always acts on this device's own account, so while connected it would destroy the database
 * that is *not* on screen -- and rather than word that carefully, the caller withholds the button
 * entirely until the connection is cleared.
 */
private data class DeleteAccountCopy(
    val buttonLabel: String,
    val dialogTitle: String,
    val lead: String,
    val warning: String,
    val confirmLabel: String
)

private fun deleteAccountCopy(authState: AuthState): DeleteAccountCopy = when {
    authState is AuthState.Authenticated -> DeleteAccountCopy(
        buttonLabel = "Delete Account",
        dialogTitle = "Delete Account & Database?",
        lead = "Are you sure? This will permanently DESTROY the entire cloud database branch for ID:",
        warning = "This wipes your identity, every database record, and all stored images — in the cloud and on this device. You are signed out of Google and the app restarts empty. This cannot be undone.",
        confirmLabel = "Delete Everything"
    )
    else -> DeleteAccountCopy(
        buttonLabel = "Wipe Local Account Data",
        dialogTitle = "Wipe Local Account?",
        lead = "Are you sure? This will permanently DESTROY the local-only account and its cloud branch for ID:",
        warning = "A local account has no password or email to recover with, so there is no way back. Everything on this device goes with it, and the app restarts with a brand-new empty account.",
        confirmLabel = "Wipe Everything"
    )
}

/** "Expires in 3h 20m" / "Expires in 12m". Coarse on purpose -- it is a rough sense of urgency. */
private fun remainingLabel(expiresAt: Long, now: Long): String {
    val remaining = expiresAt - now
    if (remaining <= 0) return "Expired"
    val hours = remaining / 3_600_000
    val minutes = (remaining % 3_600_000) / 60_000
    return if (hours >= 1) "Expires in ${hours}h ${minutes}m" else "Expires in ${minutes}m"
}

/** Shared confirm for the two actions that replace this device's database with another one's. */
@Composable
private fun SyncSwitchDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class SyncStatusVisuals(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color
)

@Composable
fun AccountSection(
    authState: AuthState,
    authOperation: AuthOperation?,
    customUsername: String?,
    manualSyncId: String?,
    currentUserId: String?,
    generatedInviteCode: InviteCode?,
    inviteCodeError: String?,
    inviteCodeGenerationError: String?,
    sharedWithUsers: Map<String, String>,
    onUsernameChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDismissAuthError: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onGenerateInviteCode: () -> Unit,
    onRevokeInviteCode: () -> Unit,
    onUseInviteCode: (String) -> Unit,
    onClearSync: () -> Unit,
    onClearError: () -> Unit,
    onClearGenerationError: () -> Unit,
    onRevokeAccess: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deleteCopy = deleteAccountCopy(authState)

    // Both sync switches now empty this device's database first (see switchSyncTarget), so both
    // need saying out loud rather than happening on one tap.
    var pendingJoinCode by remember { mutableStateOf<String?>(null) }
    var showClearSyncDialog by remember { mutableStateOf(false) }

    pendingJoinCode?.let { code ->
        SyncSwitchDialog(
            title = "Join This Database?",
            body = "This device will stop using its own database and read the account behind code $code instead. Its local copy is replaced by theirs.\n\nYour own data is not deleted — it stays in your account and comes back if you disconnect later.",
            confirmLabel = "Join",
            onConfirm = {
                onUseInviteCode(code)
                pendingJoinCode = null
            },
            onDismiss = { pendingJoinCode = null }
        )
    }

    if (showClearSyncDialog) {
        SyncSwitchDialog(
            title = "Disconnect From External Account?",
            body = "This device goes back to your own database. The local copy of the external account's data is cleared — it stays in their account, untouched.\n\nYour own data comes back from your account.",
            confirmLabel = "Disconnect",
            onConfirm = {
                onClearSync()
                showClearSyncDialog = false
            },
            onDismiss = { showClearSyncDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(deleteCopy.dialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(deleteCopy.lead)
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentUserId ?: "Unknown ID",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(deleteCopy.warning)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccountClick()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(deleteCopy.confirmLabel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncStatusBanner(
                authState = authState,
                manualSyncId = manualSyncId,
                currentUserId = currentUserId
            )

            if (currentUserId != null) {
                MaskedIdRow(label = "This device's ID", id = currentUserId)
            }

            // An in-flight or failed operation is drawn *in front of* the account state, not
            // instead of it -- the two used to share one enum, so a stale error convinced every
            // other branch here that a signed-in Google user was on a local account.
            when {
                authOperation is AuthOperation.InProgress -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                authOperation is AuthOperation.Failed -> {
                    Text("Error: ${authOperation.message}", color = MaterialTheme.colorScheme.error)
                    // Retrying only makes sense where signing in is actually allowed -- offering it
                    // to someone already signed in, or connected to an external account, is exactly
                    // the overlap the error above is usually complaining about.
                    if (authState !is AuthState.Authenticated && manualSyncId == null) {
                        SignInButton(onSignInClick)
                    }
                    // clearAuthState() previously had no caller anywhere, so an auth error covered
                    // the account section until the ViewModel died with the activity.
                    TextButton(onClick = onDismissAuthError, modifier = Modifier.align(Alignment.End)) {
                        Text("Dismiss")
                    }
                }
                authState is AuthState.Authenticated -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(customUsername ?: authState.user.displayName ?: "User", fontWeight = FontWeight.Bold)
                            Text(authState.user.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    var tempUsername by remember(customUsername) { mutableStateOf(customUsername ?: "") }
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        label = { Text("Custom Username") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Shown on the splash screen") },
                        trailingIcon = {
                            if (tempUsername != (customUsername ?: "")) {
                                IconButton(onClick = { onUsernameChange(tempUsername) }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save")
                                }
                            }
                        }
                    )
                    
                    Button(
                        onClick = onSignOutClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("Sign Out")
                    }
                }
                else -> {
                    if (manualSyncId != null) {
                        Text(
                            "Signing in is disabled while connected to an external account — clear the sync connection below first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Sync your inventory across devices by signing in.")
                        SignInButton(onSignInClick)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Text("Display Name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    var tempUsername by remember(customUsername) { mutableStateOf(customUsername ?: "") }
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        label = { Text("Custom Username") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Shown on the splash screen") },
                        trailingIcon = {
                            if (tempUsername != (customUsername ?: "")) {
                                IconButton(onClick = { onUsernameChange(tempUsername) }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save")
                                }
                            }
                        }
                    )
                }
            }

            // Withheld while an external account is connected. Delete acts on this device's own
            // account -- never the one being read -- so here it destroys the database that is not
            // on screen while leaving the visible one untouched. No wording makes that a safe thing
            // to put one tap away; disconnecting first is the only order in which the button means
            // what it appears to mean.
            if (currentUserId != null && manualSyncId != null) {
                Text(
                    "Deleting this device's account is unavailable while an external account is connected — it would destroy your own database, not the one shown here. Disconnect first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (currentUserId != null) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(deleteCopy.buttonLabel)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Text("Invite System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            if (currentUserId != null) {
                // Everything in this block is about *your own* database, because it all keys off
                // your Firebase uid rather than manualSyncId. While an external account is
                // connected that is the one database this device is not showing you, so minting a
                // code here would share something other than what is on screen -- and nobody
                // reading it would reach the inventory you are currently looking at, since your own
                // node stops being written to for as long as you stay connected.
                if (manualSyncId != null) {
                    Text(
                        "This section is about your own database, not the external one you're reading. Disconnect above to share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Re-read on a slow tick so "Expires in 3h 20m" does not sit there going stale while
                // Settings stays on screen. Only runs while there is a code to count down.
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(generatedInviteCode) {
                    while (generatedInviteCode != null) {
                        now = System.currentTimeMillis()
                        delay(60_000)
                    }
                }

                val liveCode = generatedInviteCode?.takeIf { !it.isExpired(now) }
                val expired = generatedInviteCode != null && liveCode == null

                if (liveCode == null) {
                    if (expired) {
                        Text(
                            "Your invite code has expired. Generate a new one to share your database again — anyone already connected stays connected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onGenerateInviteCode,
                        enabled = manualSyncId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (expired) "Generate a New Code" else "Generate Invite Code")
                    }
                } else {
                    OutlinedTextField(
                        value = liveCode.code,
                        onValueChange = {},
                        label = { Text("Your Invite Code") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(liveCode.code))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        supportingText = {
                            Text("${remainingLabel(liveCode.expiresAt, now)} · anyone with it can join until then")
                        }
                    )

                    TextButton(
                        onClick = onRevokeInviteCode,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retire It Now", color = MaterialTheme.colorScheme.error)
                    }
                }

                // Its own channel, so a failure here can no longer appear as an error on the
                // unrelated "Enter Invite Code" field below.
                if (inviteCodeGenerationError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            inviteCodeGenerationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearGenerationError) { Text("Dismiss") }
                    }
                }

                Text(
                    // "your own", not "your", because while an external account is connected the
                    // unqualified version reads as the database currently on screen, which is the
                    // one this list is not about.
                    "Connected to your own database (${sharedWithUsers.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (sharedWithUsers.isEmpty()) {
                    Text(
                        "No one has joined using your invite code yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sharedWithUsers.keys.forEach { joinerUid ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    joinerUid.take(10) + "…",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onRevokeAccess(joinerUid) }) {
                                    Text("Revoke", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Text(
                        "Revoking removes someone from this list, but the code they used still works — retire the code above to stop them rejoining with it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (authState is AuthState.Authenticated) {
                if (inviteCodeError != null) {
                    Text(inviteCodeError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Sign out of your Google account to connect to someone else's database instead — these are separate states.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var codeInput by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        // Normalized as it is typed rather than only uppercased: codes get pasted
                        // with stray spaces and punctuation, and characters like "." or "/" are
                        // illegal in a database key, which made the lookup throw rather than simply
                        // not match. Capped so the confirm icon means "this is a whole code".
                        codeInput = FirebaseAuthRepository.normalizeInviteCode(it)
                            .take(FirebaseAuthRepository.INVITE_CODE_LENGTH)
                        if (inviteCodeError != null) onClearError()
                    },
                    label = { Text("Enter Invite Code") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. ABC123") },
                    isError = inviteCodeError != null,
                    supportingText = {
                        if (inviteCodeError != null) {
                            Text(inviteCodeError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Paste an invite code to access another user's database")
                        }
                    },
                    trailingIcon = {
                        if (codeInput.length == FirebaseAuthRepository.INVITE_CODE_LENGTH) {
                            IconButton(onClick = { pendingJoinCode = codeInput }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync")
                            }
                        }
                    }
                )
            }

            if (manualSyncId != null) {
                Button(
                    onClick = { showClearSyncDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear External Sync")
                }
            }
        }
    }
}

@Composable
fun SignInButton(onSignInClick: () -> Unit) {
    Button(
        onClick = onSignInClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Sign in with Google")
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** Same shape as SettingsToggleRow, but drills into a sub-screen instead of flipping a switch --
 * used where a setting needs more room than a single row (see TaskTypesScreen). */
@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AboutCard(context: Context) {
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0.0"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Inventoria v$versionName", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Modern Inventory Management for Android. Built with Jetpack Compose.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Made with \uD83D\uDC9C", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
