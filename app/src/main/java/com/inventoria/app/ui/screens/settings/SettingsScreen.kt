package com.inventoria.app.ui.screens.settings

import android.content.Context
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
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.main.Screen
import com.inventoria.app.ui.screens.task.TodoPriorityDropdownMenu
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
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val showValueOnDashboard by viewModel.showValueOnDashboard.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val customUsername by viewModel.customUsername.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val autoCurrencyEnabled by viewModel.autoCurrencyEnabled.collectAsState()
    val manualSyncId by viewModel.manualSyncId.collectAsState()
    val generatedInviteCode by viewModel.generatedInviteCode.collectAsState()
    val inviteCodeError by viewModel.inviteCodeError.collectAsState()
    val sharedWithUsers by viewModel.sharedWithUsers.collectAsState()
    val innerTaskEnabled by viewModel.innerTaskEnabled.collectAsState()
    val procrastinationTodoEnabled by viewModel.procrastinationTodoEnabled.collectAsState()
    val procrastinationTodoCutoff by viewModel.procrastinationTodoCutoff.collectAsState()
    val procrastinationTaskEnabled by viewModel.procrastinationTaskEnabled.collectAsState()
    val procrastinationTaskKinds by viewModel.procrastinationTaskKinds.collectAsState()
    val procrastinationPenaltyAmount by viewModel.procrastinationPenaltyAmount.collectAsState()

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
                subtitle = "Display total inventory value in the Inventory tab",
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
                customUsername = customUsername,
                manualSyncId = manualSyncId,
                currentUserId = viewModel.getCurrentUserId(),
                generatedInviteCode = generatedInviteCode,
                inviteCodeError = inviteCodeError,
                sharedWithUsers = sharedWithUsers,
                onUsernameChange = { viewModel.updateCustomUsername(it) },
                onSignInClick = {
                    launcher.launch(viewModel.getGoogleSignInIntent())
                },
                onSignOutClick = { viewModel.signOut() },
                onDeleteAccountClick = { viewModel.deleteAccount() },
                onGenerateInviteCode = { viewModel.createInviteCode() },
                onUseInviteCode = { viewModel.useInviteCode(it) },
                onClearSync = { viewModel.setManualSyncId(null) },
                onClearError = { viewModel.clearInviteCodeError() },
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
fun SyncStatusBanner(authState: AuthState, manualSyncId: String?) {
    val (icon, title, subtitle, containerColor, contentColor) = when {
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
    customUsername: String?,
    manualSyncId: String?,
    currentUserId: String?,
    generatedInviteCode: String?,
    inviteCodeError: String?,
    sharedWithUsers: Map<String, String>,
    onUsernameChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onGenerateInviteCode: () -> Unit,
    onUseInviteCode: (String) -> Unit,
    onClearSync: () -> Unit,
    onClearError: () -> Unit,
    onRevokeAccess: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account & Database?") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure? This will permanently DESTROY the entire cloud database branch for ID:")
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
                    Text("This wipes your identity, every database record, and all stored images. This cannot be undone.")
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
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
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
            SyncStatusBanner(authState = authState, manualSyncId = manualSyncId)

            if (currentUserId != null) {
                MaskedIdRow(label = "This device's ID", id = currentUserId)
            }

            when (authState) {
                is AuthState.Authenticated -> {
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
                AuthState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is AuthState.Error -> {
                    Text("Error: ${authState.message}", color = MaterialTheme.colorScheme.error)
                    SignInButton(onSignInClick)
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

            if (currentUserId != null) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (authState is AuthState.Authenticated) "Delete Account" else "Wipe Local Account Data")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Text("Invite System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            if (currentUserId != null) {
                if (generatedInviteCode == null) {
                    Button(
                        onClick = onGenerateInviteCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Invite Code")
                    }
                } else {
                    OutlinedTextField(
                        value = generatedInviteCode,
                        onValueChange = {},
                        label = { Text("Your Invite Code") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(generatedInviteCode))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        supportingText = { Text("Give this code to others to let them sync to your data") }
                    )
                }

                Text(
                    "Connected to your database (${sharedWithUsers.size})",
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
                        codeInput = it.uppercase()
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
                        if (codeInput.length >= 6) {
                            IconButton(onClick = { onUseInviteCode(codeInput) }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync")
                            }
                        }
                    }
                )
            }

            if (manualSyncId != null) {
                Button(
                    onClick = onClearSync,
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
