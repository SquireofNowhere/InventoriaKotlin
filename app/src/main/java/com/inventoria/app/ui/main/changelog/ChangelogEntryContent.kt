package com.inventoria.app.ui.main.changelog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One release's heading and bullet list -- the single rendering of a [ChangelogEntry], shared by
 * the What's New dialog and the Version History screen so the two never describe the same
 * release differently.
 *
 * [trailingLabel] is a small tag after the version ("Current"), for the history screen to mark
 * the build that is running; the dialog passes none.
 */
@Composable
fun ChangelogEntryContent(
    entry: ChangelogEntry,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Version ${entry.versionName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (trailingLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    trailingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        entry.changes.forEach { change ->
            Text(
                "•  $change",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}
