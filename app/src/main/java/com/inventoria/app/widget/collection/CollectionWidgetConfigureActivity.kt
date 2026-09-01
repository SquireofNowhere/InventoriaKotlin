package com.inventoria.app.widget.collection

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inventoria.app.R
import com.inventoria.app.data.model.InventoryCollectionWithCount
import com.inventoria.app.data.repository.CollectionRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.theme.InventoriaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The Collection widget's configure step: shown by the launcher when the widget is placed (and
 * from its long-press menu, as the provider is reconfigurable). Lists every collection; picking
 * one stores it for this widget id and draws the widget.
 *
 * RESULT_CANCELED is set up front, as the platform requires: if the user backs out, the launcher
 * then knows to discard the half-placed widget.
 */
@AndroidEntryPoint
class CollectionWidgetConfigureActivity : ComponentActivity() {

    @Inject
    lateinit var collectionRepository: CollectionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val isDarkMode by settingsRepository.isDarkMode().collectAsState(initial = false)
            val collections by collectionRepository.getCollectionsWithCounts().collectAsState(initial = emptyList())
            InventoriaTheme(darkTheme = isDarkMode) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_choose_collection)) }) }
                ) { padding ->
                    if (collections.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.widget_no_collections),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                            items(collections, key = { it.collection.id }) { entry ->
                                CollectionChoice(entry) { choose(appWidgetId, entry.collection.id) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, collectionId: Long) {
        CollectionWidgetPrefs.save(this, appWidgetId, collectionId)
        CollectionWidgetProvider.requestUpdate(this, intArrayOf(appWidgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun CollectionChoice(entry: InventoryCollectionWithCount, onClick: () -> Unit) {
    val collection = entry.collection
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text(collection.icon ?: "📦", style = MaterialTheme.typography.titleLarge)
            }
        },
        headlineContent = { Text(collection.name) },
        supportingContent = {
            val count = entry.itemCount
            Text(if (count == 1) "1 item" else "$count items")
        }
    )
}
