package com.inventoria.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.ui.main.changelog.ChangelogEntry
import com.inventoria.app.ui.main.changelog.ChangelogEntryContent

/**
 * The one-time launch dialogs AppLaunchViewModel drives. Both are ordinary informational
 * AlertDialogs in the "Track Interruptions?" mold -- dismissable any way the user likes, and
 * never shown again once answered.
 */

/**
 * Asks which area the user wants the app to focus on. Every dismissal path is a valid answer:
 * picking a row chooses it, "Use Task Tracker" (and back / outside-tap) keeps the default.
 */
@Composable
fun FocusPromptDialog(
    onChoose: (FocusArea) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's your focus?") },
        text = {
            Column {
                Text(
                    "Pick what you mostly use Inventoria for. Your focus tab moves next to " +
                        "Today and the dashboard leads with it -- everything else stays right " +
                        "where it is.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                FocusArea.entries.forEach { area ->
                    ListItem(
                        headlineContent = { Text(area.title, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(area.description) },
                        modifier = Modifier.clickable { onChoose(area) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can change this later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Use ${FocusArea.DEFAULT.title}")
            }
        }
    )
}

/**
 * The update log: every catalog entry newer than the last version this device acknowledged.
 *
 * [onSeeAll] opens the full Version History screen; the caller is expected to dismiss (and so
 * acknowledge) the dialog as part of that, since the same entries are what the screen shows.
 */
@Composable
fun WhatsNewDialog(
    entries: List<ChangelogEntry>,
    onDismiss: () -> Unit,
    onSeeAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New") },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { entry ->
                        ChangelogEntryContent(entry)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        dismissButton = {
            TextButton(onClick = onSeeAll) {
                Text("See all")
            }
        }
    )
}
