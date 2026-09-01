package com.inventoria.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.BuildConfig
import com.inventoria.app.ui.main.changelog.ChangelogCatalog
import com.inventoria.app.ui.main.changelog.ChangelogEntryContent

/**
 * Every release in [ChangelogCatalog], newest first -- the What's New dialog's contents, kept
 * around for anyone who dismissed it too fast or wants to see what an older update did.
 *
 * Reads the catalog directly rather than through a ViewModel: it is a static list with nothing
 * to observe, and the only per-device fact here (which entry is the running build) comes from
 * BuildConfig. The catalog began with 2.13, when the dialog did, so that is where this starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(onNavigateBack: () -> Unit) {
    val entries = remember { ChangelogCatalog.entries.sortedByDescending { it.versionCode } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Version History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.versionCode }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ChangelogEntryContent(
                        entry = entry,
                        modifier = Modifier.padding(16.dp),
                        trailingLabel = when {
                            entry.versionCode == BuildConfig.VERSION_CODE -> "Current"
                            // A catalog entry written ahead of its release, as the next one
                            // usually is while it is being built.
                            entry.versionCode > BuildConfig.VERSION_CODE -> "Upcoming"
                            else -> null
                        }
                    )
                }
            }
            item {
                Text(
                    "The update log begins at version 2.13, when it was introduced. Earlier " +
                        "releases are not listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
