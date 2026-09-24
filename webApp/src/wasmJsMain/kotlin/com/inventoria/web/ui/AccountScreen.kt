package com.inventoria.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inventoria.web.WebAppState

@Composable
fun AccountScreen(state: WebAppState) {
    val session by state.session.collectAsState()
    val snapshot = state.snapshot

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Account")
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session?.displayName ?: "Signed in", style = MaterialTheme.typography.titleLarge)
                session?.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "User ID ${session?.uid.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                snapshot?.let {
                    Text(
                        "Last synced ${formatDay(it.loadedAt)} ${formatTime(it.loadedAt)} · refreshes every minute",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = state::refresh, enabled = !state.isLoading) { Text("Refresh now") }
            OutlinedButton(onClick = state::signOut) { Text("Sign out") }
        }
        SectionHeader("About the web app")
        Text(
            "This is the early online version of Inventoria. It reads the same cloud data as the " +
                "Android app, and you can check todos off here. Creating and editing everything else " +
                "still happens on your phone for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
