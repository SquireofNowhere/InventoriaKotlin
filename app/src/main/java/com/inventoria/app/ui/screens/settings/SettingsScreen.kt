package com.inventoria.app.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.theme.PurplePrimary

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                onUsernameChange = { viewModel.updateCustomUsername(it) },
                onSignInClick = { /* Launch Google Sign In */ },
                onSignOutClick = { viewModel.signOut() }
            )

            SettingsCategoryHeader("About")
            AboutCard(context)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AccountSection(
    authState: AuthState,
    customUsername: String?,
    onUsernameChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Text("Sign Out")
                    }
                }
                AuthState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                else -> {
                    Text("Sync your inventory across devices by signing in.")
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
            }
        }
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
