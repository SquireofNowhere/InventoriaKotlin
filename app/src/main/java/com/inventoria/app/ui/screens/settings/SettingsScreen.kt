package com.inventoria.app.ui.screens.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.inventoria.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val showValueOnDashboard by viewModel.showValueOnDashboard.collectAsState()
    val authState by viewModel.authState.collectAsState()

    // Google Sign-In Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { viewModel.onGoogleSignInSuccess(it) }
        } catch (e: ApiException) {
            // Handle error
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCategoryHeader(title = "Cloud Account")
            AccountSection(
                authState = authState,
                onSignInClick = {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id)) // Requires google-services plugin
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    launcher.launch(googleSignInClient.signInIntent)
                },
                onSignOutClick = viewModel::signOut
            )

            SettingsCategoryHeader(title = "Display")
            SettingsToggleRow(
                title = "Dark Mode",
                subtitle = "Enable darker theme for the app",
                icon = Icons.Default.DarkMode,
                checked = isDarkMode,
                onCheckedChange = viewModel::toggleDarkMode
            )
            SettingsToggleRow(
                title = "Show Total Value",
                subtitle = "Display inventory valuation on dashboard",
                icon = Icons.Default.AttachMoney,
                checked = showValueOnDashboard,
                onCheckedChange = viewModel::toggleShowValue
            )

            SettingsCategoryHeader(title = "General")
            SettingsToggleRow(
                title = "Notifications",
                subtitle = "Get alerts for out of stock items",
                icon = Icons.Default.Notifications,
                checked = notificationsEnabled,
                onCheckedChange = viewModel::toggleNotifications
            )

            SettingsCategoryHeader(title = "About")
            AboutCard()
        }
    }
}

@Composable
fun AccountSection(
    authState: AuthState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (authState) {
                is AuthState.Idle -> {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
                is AuthState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                is AuthState.Authenticated -> {
                    val user = authState.user
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Profile (placeholder if photoUrl is null)
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = user.displayName ?: "User", fontWeight = FontWeight.Bold)
                            Text(text = user.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onSignOutClick) {
                            Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                is AuthState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Error: ${authState.message}", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onSignInClick) {
                            Text("Try Again")
                        }
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Inventoria v1.1",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Modern Inventory Management for Android. Built with Jetpack Compose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Made with 💜",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
