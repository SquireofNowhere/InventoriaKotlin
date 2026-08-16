package com.inventoria.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventoria.app.ui.main.SyncStatusViewModel

/**
 * The app bar every bottom-nav tab uses: bold centred title, sync state, then the screen's own
 * actions.
 *
 * It exists so sync status is in one place on every tab rather than hand-placed on two of four,
 * and so the four tabs stop each inventing their own bar (one had no title, one had a back arrow
 * on a tab root, one had none at all). Drill-down screens -- item detail, settings, help, history --
 * keep their own bars with their own back buttons; they aren't tabs and don't want a sync glyph.
 *
 * The sync ViewModel is resolved here rather than passed in, because it only re-exposes a
 * repository StateFlow and every caller would otherwise thread an identical parameter through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriaTopBar(
    title: String,
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
        }
    )
}
