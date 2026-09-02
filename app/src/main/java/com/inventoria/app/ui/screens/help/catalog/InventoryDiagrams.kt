package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import com.inventoria.app.ui.screens.help.model.*

/** Diagram building blocks for the Inventory guides. Items and the search/sort/group/filter
 * toolbar are the workhorses -- every article that shows a row draws it through [inventoryItemRow]
 * so the shape stays consistent across a category with this much surface area. */

internal fun inventoryItemRow(
    name: String = "Camp Stove",
    location: String = "Garage Shelf",
    category: String? = "Camping",
    quantity: Int = 1,
    price: String? = null,
    equipped: Boolean = false,
    isContainer: Boolean = false,
    hasChildren: Boolean = false,
    highlight: Boolean = false,
    callout: Int? = null,
    gesture: DiagramGesture? = null
) = DiagramElement.Row(
    title = name,
    subtitle = location + (category?.let { " · $it" } ?: ""),
    meta = "Qty: $quantity" + (price?.let { " · $it" } ?: ""),
    leadingIcon = if (isContainer) Icons.Default.Inventory else Icons.Default.Category,
    chips = listOfNotNull(
        if (equipped) DiagramChip("Equipped", DiagramAccent.Primary) else null,
        if (hasChildren) DiagramChip("Contains items", DiagramAccent.Neutral) else null
    ),
    highlight = highlight,
    callout = callout,
    gesture = gesture
)

internal fun addItemFab(highlight: Boolean = true, callout: Int? = null) =
    DiagramElement.Fab(Icons.Default.Add, highlight = highlight, callout = callout)

/** The search field plus its three trailing icons -- sort, group, filter -- as they sit in one
 * row above the list. [highlight] names which icon to ring, by index (0 = sort, 1 = group,
 * 2 = filter); null highlights the search field itself. */
internal fun inventoryToolbarRow(highlight: Int? = null, callout: Int? = null) = DiagramElement.Row(
    title = "Search",
    leadingIcon = Icons.Default.Search,
    highlight = highlight == null,
    callout = if (highlight == null) callout else null,
    trailing = listOf(
        DiagramIcon(Icons.Default.Sort, highlight = highlight == 0, callout = if (highlight == 0) callout else null),
        DiagramIcon(Icons.Default.GridView, highlight = highlight == 1, callout = if (highlight == 1) callout else null),
        DiagramIcon(Icons.Default.FilterList, highlight = highlight == 2, callout = if (highlight == 2) callout else null)
    )
)

internal fun filterPopup(highlightField: Int? = null) = DiagramElement.Popup(
    title = "Filters",
    fields = listOf(
        DiagramField("Hard Filter (Pass ALL selected)", "", FieldKind.Toggle, highlight = highlightField == 0, callout = if (highlightField == 0) 1 else null),
        DiagramField("Invert Filter (Block selected)", "", FieldKind.Toggle, highlight = highlightField == 1, callout = if (highlightField == 1) 1 else null)
    ),
    dismissLabel = "Clear All"
)

internal fun tagChipRow(selected: List<String>, all: List<String>) = DiagramElement.ChipRow(
    chips = all.map { DiagramChip(it, if (it in selected) DiagramAccent.Primary else DiagramAccent.Neutral, highlight = it in selected) }
)

/** One of the three drag outcomes: onto a container (Move), onto a plain item (Link), or off the
 * top of the list (Remove/unequip). */
internal fun dragGhost(label: String, accent: DiagramAccent) = DiagramElement.Row(
    title = label,
    leadingBar = accent,
    highlight = true
)

internal fun sortMenuPopup(selected: String = "Recently Updated") = DiagramElement.Popup(
    title = "Sort",
    fields = listOf(
        DiagramField("Name (A-Z)", "", FieldKind.Dropdown, highlight = selected == "Name (A-Z)"),
        DiagramField("Recently Updated", "", FieldKind.Dropdown, highlight = selected == "Recently Updated"),
        DiagramField("Highest Quantity", "", FieldKind.Dropdown, highlight = selected == "Highest Quantity"),
        DiagramField("Highest Price", "", FieldKind.Dropdown, highlight = selected == "Highest Price")
    )
)

internal fun photoStrip(count: Int = 3, hasProfile: Boolean = true) = DiagramElement.ChipRow(
    chips = buildList {
        add(DiagramChip("Add", DiagramAccent.Neutral))
        repeat(count) { i -> add(DiagramChip(if (hasProfile && i == 0) "★ Photo" else "Photo", DiagramAccent.Primary, leadingDot = i == 0 && hasProfile)) }
    }
)

internal fun mergeSelectionBar(count: Int = 3) = DiagramElement.TopBar(
    title = "$count Selected",
    actions = listOf(
        DiagramIcon(Icons.Default.Merge, highlight = true),
        DiagramIcon(Icons.Default.Delete)
    )
)

internal fun equipRepackPopup(itemName: String = "Camp Stove", containerName: String? = "Garage Bin") = DiagramElement.Popup(
    title = "Unequip $itemName",
    body = "Would you like to repack this item back to $containerName or leave it at your current location?",
    confirmLabel = "Repack",
    dismissLabel = "Leave here"
)

/** One row in a linked-items list, as it reads on the item whose detail screen this is. [rowFollows]
 * is true when the row's own item is the follower (it reads "Follows this item"), false when the
 * row's own item is the leader (it reads "Leads this item") -- named for what the text says rather
 * than mirroring ItemLink's own leaderId/followerId, which flip meaning depending on which item's
 * page you're looking from. */
internal fun linkRow(name: String = "Phone Charger", rowFollows: Boolean = true) = DiagramElement.Row(
    title = name,
    subtitle = if (rowFollows) "Follows this item" else "Leads this item",
    leadingIcon = Icons.Default.Link
)

/** The address field: free text, with the Pick on Map shortcut as its trailing icon. */
internal fun locationRow(address: String = "42 Example St", resolved: Boolean = false, highlight: Boolean = false) = DiagramElement.Row(
    title = if (resolved) "inside \"Garage Bin\"" else address,
    leadingIcon = Icons.Default.LocationOn,
    trailing = listOf(DiagramIcon(Icons.Default.Map, highlight = highlight)),
    highlight = highlight
)

/** The full-width Get Current Location button beneath the address field. */
internal fun getLocationButton(highlight: Boolean = true) = DiagramElement.Row(
    title = "Get Current Location",
    leadingIcon = Icons.Default.MyLocation,
    highlight = highlight
)
