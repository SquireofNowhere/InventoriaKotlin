package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import com.inventoria.app.ui.screens.help.model.HelpCategory

/**
 * Inventory & Items, assembled from its two article files.
 *
 * Split the same way Task Tracking and Todos are -- by what the reader is trying to do: the list
 * screen itself (finding, sorting, filtering, dragging things around, the detail screen) in
 * InventoryListArticles.kt, and everything about one item's own fields (adding it, photos,
 * location, containers, equipping, links, custom fields) in InventoryItemArticles.kt. Collections
 * and Map are deliberately separate categories, not segments of this one -- both are reached from
 * here but have their own distinct vocabulary and their own not-yet-written guides.
 */
internal val inventoryCategory = HelpCategory(
    id = "inventory",
    title = "Inventory & Items",
    summary = "Items, containers, photos, searching and filtering, in the Inventory tab",
    icon = Icons.Default.Inventory,
    articles = inventoryListArticles + inventoryItemArticles
)
