package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import com.inventoria.app.ui.screens.help.model.ArticleStatus
import com.inventoria.app.ui.screens.help.model.HelpArticle
import com.inventoria.app.ui.screens.help.model.HelpCategory

private fun comingSoon(id: String, title: String, summary: String, detail: String) = HelpArticle(
    id = id,
    title = title,
    summary = summary,
    whatItIs = detail,
    status = ArticleStatus.ComingSoon
)

/**
 * Things named in the app or its documentation that do not work yet.
 *
 * Gathered into one place rather than scattered through the areas they belong to, so that a
 * half-written area is never padded out with entries that lead nowhere. Each says plainly what is
 * missing; none pretends to give instructions.
 *
 * Declared above [stubCategories] deliberately: top-level properties initialise in declaration
 * order, so a category referenced before it is declared would be null at startup.
 */
private val comingSoonCategory = HelpCategory(
    id = "coming-soon",
    title = "Not yet available",
    summary = "Planned or partly-built features, listed so the guide stays honest",
    icon = Icons.Default.HourglassEmpty,
    articles = listOf(
        comingSoon(
            id = "soon-barcode",
            title = "Barcodes and SKUs",
            summary = "Items can store a barcode, but there's no way to enter or scan one.",
            detail = "Items carry barcode and SKU fields internally, and search will match on them, but no screen " +
                "offers a place to type one and there is no scanner."
        ),
        comingSoon(
            id = "soon-proximity",
            title = "Distance to your items",
            summary = "Your location is read but never compared to where your things are.",
            detail = "The map knows where you are and where your items are, but nothing calculates or shows the " +
                "distance between them."
        ),
        comingSoon(
            id = "soon-dynamic-theme",
            title = "Dynamic colour",
            summary = "Theming from your wallpaper is switched off.",
            detail = "The app can take its palette from your Android wallpaper, but this is disabled so the look " +
                "stays consistent. The Dark Mode toggle in Settings is the theming that does work."
        ),
        comingSoon(
            id = "soon-notifications-toggle",
            title = "The Enable Notifications switch",
            summary = "The setting saves, but nothing reads it.",
            detail = "Turning it off does not currently stop any notification. The running-task notification is " +
                "part of the background timer and always shows while a task runs."
        ),
        comingSoon(
            id = "soon-collection-icon",
            title = "Collection icon and colour",
            summary = "The circle on the collection form is tappable but does nothing.",
            detail = "There is no picker behind it yet, so a collection's icon and colour cannot be changed."
        ),
        comingSoon(
            id = "soon-collection-quick-actions",
            title = "Collection quick actions",
            summary = "Equip or pack a whole collection from the list.",
            detail = "Packing and equipping work from inside a collection. Doing it directly from the collections " +
                "list is not wired up."
        ),
        comingSoon(
            id = "soon-expand-all",
            title = "Expand or collapse all containers",
            summary = "No control exposes it.",
            detail = "The inventory list can expand or collapse every container at once, but nothing in the UI " +
                "triggers it. Containers still expand individually with their chevron."
        ),
        comingSoon(
            id = "soon-auto-delete",
            title = "Auto-delete after saving to calendar",
            summary = "The countdown is shown, but nothing acts on it.",
            detail = "A task saved to your calendar shows an \"Auto-delete in…\" countdown. Nothing removes the " +
                "task when it reaches zero — the local copy stays until you delete it."
        )
    )
)

/** The home screen, listed first in the index because it's where the app opens. */
internal val todayCategory = HelpCategory(
    id = "today",
    title = "Today",
    summary = "Your day at a glance: what's due, and the 24-hour timeline",
    icon = Icons.Default.Today
)

/**
 * The rest of the manual.
 *
 * These carry no articles yet. They are listed anyway so the index shows the true shape of the app
 * rather than implying Task Tracking is all there is -- the index renders an empty category as
 * visible but not enterable, so nobody taps into a blank screen.
 */
internal val stubCategories = listOf(
    HelpCategory(
        id = "inventory",
        title = "Inventory & Items",
        summary = "Items, containers, photos, searching and filtering, in the Inventory tab",
        icon = Icons.Default.Inventory
    ),
    HelpCategory(
        id = "collections",
        title = "Collections & Readiness",
        summary = "Sets of items, packing and equipping, in the Inventory tab",
        icon = Icons.Default.Collections
    ),
    HelpCategory(
        id = "map",
        title = "Locations & Map",
        summary = "Where your things are and how locations are inherited, in the Inventory tab",
        icon = Icons.Default.Map
    ),
    HelpCategory(
        id = "todos",
        title = "Todos",
        summary = "Deadlines, priorities, sub-todos and starting work from them, in the Plan tab",
        icon = Icons.Default.Checklist
    ),
    HelpCategory(
        id = "productivity",
        title = "Productivity & Scoring",
        summary = "The daily score, the stats screens and the penalties",
        icon = Icons.Default.Insights
    ),
    HelpCategory(
        id = "task-types",
        title = "Task Types",
        summary = "Managing the activity labels that group your tasks",
        icon = Icons.Default.Category
    ),
    HelpCategory(
        id = "sync",
        title = "Sync & Accounts",
        summary = "Google sign-in, invite codes and connected devices",
        icon = Icons.Default.CloudSync
    ),
    HelpCategory(
        id = "settings",
        title = "Settings",
        summary = "Appearance, currency, notifications and penalties",
        icon = Icons.Default.Settings
    ),
    comingSoonCategory
)
