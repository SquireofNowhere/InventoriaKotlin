package com.inventoria.app.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.inventoria.app.ui.theme.PurplePrimary
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
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
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                subtitle = "Display total inventory value on dashboard",
                icon = Icons.Default.AccountBalanceWallet,
                checked = showValueOnDashboard,
                onCheckedChange = { viewModel.toggleShowValue(it) }
            )

            SettingsCategoryHeader("Notifications")
            SettingsToggleRow(
                title = "Enable Notifications",
                subtitle = "Receive alerts for task timers and stock levels",
                icon = Icons.Default.Notifications,
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) }
            )

            SettingsCategoryHeader("Account")
            AccountSection(
                authState = authState,
                customUsername = customUsername,
                manualSyncId = manualSyncId,
                currentUserId = viewModel.getCurrentUserId(),
                onUsernameChange = { viewModel.updateCustomUsername(it) },
                onSignInClick = { 
                    launcher.launch(viewModel.getGoogleSignInIntent())
                },
                onSignOutClick = { viewModel.signOut() },
                onDeleteAccountClick = { viewModel.deleteAccount() },
                onManualSyncIdChange = { viewModel.setManualSyncId(it) }
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

@Composable
fun AccountSection(
    authState: AuthState,
    customUsername: String?,
    manualSyncId: String?,
    currentUserId: String?,
    onUsernameChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onManualSyncIdChange: (String?) -> Unit
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
                    Text("Sync your inventory across devices by signing in.")
                    SignInButton(onSignInClick)

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
            
            Text("Database Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            if (currentUserId != null) {
                OutlinedTextField(
                    value = currentUserId,
                    onValueChange = {},
                    label = { Text("Your Database ID") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(currentUserId))
                            Toast.makeText(context, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID")
                        }
                    },
                    supportingText = { Text("Share this ID with others to let them sync to your database") }
                )
            }

            var syncIdInput by remember(manualSyncId) { mutableStateOf(manualSyncId ?: "") }
            
            OutlinedTextField(
                value = syncIdInput,
                onValueChange = { syncIdInput = it },
                label = { Text("Sync with Database ID") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste Database ID here") },
                trailingIcon = {
                    if (syncIdInput.isNotEmpty() && syncIdInput != (manualSyncId ?: "")) {
                        IconButton(onClick = { onManualSyncIdChange(syncIdInput) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync")
                        }
                    }
                },
                supportingText = { 
                    if (manualSyncId != null) {
                        Text("Currently synced with external database", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Paste another user's ID to access their database")
                    }
                }
            )

            if (manualSyncId != null) {
                Button(
                    onClick = { onManualSyncIdChange(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Switch back to local account")
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
