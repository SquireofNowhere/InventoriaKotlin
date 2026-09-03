package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import com.inventoria.app.ui.screens.help.model.HelpCategory

/** Collections & Readiness, one article file -- unlike Inventory next door, this is a single
 * self-contained idea (create a set, add members, track and act on its readiness) rather than
 * two axes worth splitting across files. */
internal val collectionsCategory = HelpCategory(
    id = "collections",
    title = "Collections & Readiness",
    summary = "Sets of items, packing and equipping, in the Inventory tab",
    icon = Icons.Default.Collections,
    articles = collectionsArticles
)
