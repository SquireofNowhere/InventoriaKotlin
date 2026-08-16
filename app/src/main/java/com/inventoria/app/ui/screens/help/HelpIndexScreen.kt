package com.inventoria.app.ui.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.screens.help.catalog.HelpCatalog
import com.inventoria.app.ui.screens.help.model.HelpCategory

/**
 * The manual's front page: every area of the app, with a filter over article titles.
 *
 * No ViewModel -- the catalog is a compile-time constant and the only state is a search string,
 * which rememberSaveable keeps across configuration changes and process death by itself. Every
 * other screen here has a ViewModel because every other screen touches persistence; this one
 * doesn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpIndexScreen(
    onNavigateBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenArticle: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { HelpCatalog.search(query) }
    val isSearching = query.isNotBlank()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("How To", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search guides") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }

            if (!isSearching) {
                item {
                    Text(
                        text = "Guides for every feature, grouped by where they live in the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(HelpCatalog.categories, key = { it.id }) { category ->
                    CategoryCard(category = category, onClick = { onOpenCategory(category.id) })
                }
            } else if (results.isEmpty()) {
                // A blank screen after a search reads as a crash, so say what happened.
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No guides match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(results, key = { it.article.id }) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenArticle(result.article.id) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(result.article.title, fontWeight = FontWeight.Bold)
                            Text(
                                result.article.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            // Results are articles, and a title alone doesn't say where in the app
                            // you'd be -- so each carries its area.
                            Text(
                                result.category.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: HelpCategory, onClick: () -> Unit) {
    // An area whose guides aren't written yet is shown but not enterable: listing it keeps the map
    // of the app honest, while an empty screen behind it would read as data loss.
    val written = category.articles.isNotEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (written) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = if (written) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.title, fontWeight = FontWeight.Bold)
                Text(
                    text = if (written) category.summary else "Guides coming soon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (written) {
                Text(
                    text = category.articles.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
