package com.inventoria.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.ui.main.SyncStatusViewModel

/**
 * The app bar every bottom-nav tab uses: bold centred title, sync state, the screen's own actions,
 * then a help button pointing at the part of the manual that covers this tab.
 *
 * It exists so sync status and help are in one place on every tab rather than hand-placed on some
 * of them, and so the tabs stop each inventing their own bar (one had no title, one had a back
 * arrow on a tab root, one had none at all). Drill-down screens -- item detail, task history,
 * the manual itself -- keep their own bars with their own back buttons; they aren't tabs.
 *
 * [onNavigateToHelp] is nullable only so a tab that genuinely has nowhere to point can omit it.
 * Every current caller passes one; prefer adding a help category over passing null.
 *
 * The sync ViewModel is resolved here rather than passed in, because it only re-exposes a
 * repository StateFlow and every caller would otherwise thread an identical parameter through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaTopBar(
    title: String,
    onNavigateToHelp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val syncStatusViewModel: SyncStatusViewModel = hiltViewModel()
    val syncStatus by syncStatusViewModel.syncStatus.collectAsState()

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            SyncStatusIndicator(syncStatus = syncStatus)
            actions()
            // Always last, so it's in the same place on every tab -- a help button you have to
            // look for is worse than no help button.
            if (onNavigateToHelp != null) {
                IconButton(onClick = onNavigateToHelp) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Help for this screen")
                }
            }
        }
    )
}
