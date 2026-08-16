package com.inventoria.app.ui.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventoria.app.ui.screens.help.catalog.HelpCatalog
import com.inventoria.app.ui.screens.help.model.ArticleStatus
import com.inventoria.app.ui.screens.help.render.HelpBlockContent

/**
 * One article. Fixed skeleton: what it is, the steps and diagrams, why it matters, then related
 * guides -- in that order every time, because the ordering is a property of the article type
 * rather than something each author re-decides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpArticleScreen(
    articleId: String,
    onNavigateBack: () -> Unit,
    onOpenArticle: (String) -> Unit
) {
    val article = HelpCatalog.article(articleId)
    val category = HelpCatalog.categoryOf(articleId)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = category?.title ?: "Help",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (article == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("That guide is missing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (article.status == ArticleStatus.ComingSoon) ComingSoonBadge()
                    Text(article.whatItIs, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (article.status == ArticleStatus.ComingSoon) {
                // No steps for something that doesn't work yet -- the badge and the explanation
                // above are the whole article on purpose.
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "This isn't available in the app yet, so there are no steps to follow. " +
                                "It's listed here so the guide covers everything that's planned.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(article.blocks) { block -> HelpBlockContent(block) }

                article.whyItMatters?.let { why ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Why it works this way",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(why, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (article.related.isNotEmpty()) {
                item {
                    Text(
                        "Related",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(article.related) { relatedId ->
                    HelpCatalog.article(relatedId)?.let { related ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenArticle(related.id) },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(related.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // The last block would otherwise sit flush against the system nav bar.
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
