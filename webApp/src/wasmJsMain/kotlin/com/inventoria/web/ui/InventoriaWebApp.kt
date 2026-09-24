package com.inventoria.web.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inventoria.web.WebAppState
import com.inventoria.web.inventoriaHideBoot
import com.inventoria.web.inventoriaPrefersDark

enum class Destination(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    TASKS("Tasks", Icons.AutoMirrored.Filled.List),
    TODOS("Todos", Icons.Filled.CheckCircle),
    INVENTORY("Inventory", Icons.Filled.ShoppingCart),
    ACCOUNT("Account", Icons.Filled.AccountCircle)
}

@Composable
fun InventoriaWebApp() {
    val scope = rememberCoroutineScope()
    val state = remember { WebAppState(scope) }
    val session by state.session.collectAsState()

    LaunchedEffect(Unit) { inventoriaHideBoot() }
    LaunchedEffect(session?.uid) { state.onSessionChanged(session) }

    InventoriaTheme(darkTheme = remember { inventoriaPrefersDark() }) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = session
            if (current == null) {
                SignInScreen(state)
            } else {
                MainScaffold(state)
            }
        }
    }
}

/**
 * A rail on wide windows, a bottom bar on narrow ones -- the same five places either way, so the
 * app reads the same on a laptop and on a phone browser.
 */
@Composable
private fun MainScaffold(state: WebAppState) {
    var destination by remember { mutableStateOf(Destination.TODAY) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Destination.entries.forEach { d ->
                        NavigationRailItem(
                            selected = destination == d,
                            onClick = { destination = d },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(d.title) }
                        )
                    }
                }
                Content(state, destination, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Content(state, destination, Modifier.weight(1f))
                NavigationBar {
                    Destination.entries.forEach { d ->
                        NavigationBarItem(
                            selected = destination == d,
                            onClick = { destination = d },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(d.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(state: WebAppState, destination: Destination, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { ErrorBanner(it, onDismiss = state::dismissError) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                    val snapshot = state.snapshot
                    if (snapshot == null) {
                        EmptyState(if (state.isLoading) "Loading your data…" else "Nothing loaded yet.")
                    } else when (destination) {
                        Destination.TODAY -> TodayScreen(snapshot)
                        Destination.TASKS -> TasksScreen(snapshot)
                        Destination.TODOS -> TodosScreen(snapshot, onToggle = state::setTodoDone)
                        Destination.INVENTORY -> InventoryScreen(snapshot)
                        Destination.ACCOUNT -> AccountScreen(state)
                    }
                }
            }
        }
    }
}
