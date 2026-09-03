package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import com.inventoria.app.ui.screens.help.model.*

/** Diagram building blocks for the Collections guides. A collection's icon/colour circle already
 * has its own picker documented in Inventory's guide (the form is identical, just for a
 * collection instead of an item), so nothing here re-draws that specific dialog. */

internal fun collectionCard(
    name: String = "Emergency Kit",
    tags: String? = "#car #safety",
    itemCount: Int = 8,
    highlight: Boolean = false,
    gesture: DiagramGesture? = null
) = DiagramElement.Row(
    title = name,
    subtitle = tags,
    meta = "$itemCount items",
    highlight = highlight,
    gesture = gesture
)

internal fun addCollectionFab(highlight: Boolean = true) =
    DiagramElement.Fab(Icons.Default.Add, highlight = highlight)

internal fun collectionSearchRow() = DiagramElement.Row(
    title = "Search collections...",
    leadingIcon = Icons.Default.Search
)

internal fun collectionTypeChips(selected: String = "Travel Kit") = DiagramElement.ChipRow(
    chips = listOf("Travel Kit", "Work Gear", "Outfit", "Emergency", "Hobby", "Other")
        .map { DiagramChip(it, if (it == selected) DiagramAccent.Primary else DiagramAccent.Neutral, highlight = it == selected) }
)

/** The readiness line: a percentage plus the three counts it's built from. */
internal fun readinessRow(percent: Int = 75, available: Int = 6, total: Int = 8, packed: Int = 4, equipped: Int = 2) =
    DiagramElement.Row(
        title = "$percent% Ready",
        subtitle = "$available/$total Available",
        meta = "$packed Packed · $equipped Equipped"
    )

/** Pack All / Unpack All and Equip All / Unequip All, the two button pairs under the header.
 * [packed] and [equipped] flip each pair's own label independently, matching the real screen. */
internal fun collectionActionButtons(packed: Boolean = false, equipped: Boolean = false) = DiagramElement.ChipRow(
    chips = listOf(
        DiagramChip(if (packed) "Unpack All" else "Pack All", DiagramAccent.Primary, highlight = true),
        DiagramChip(if (equipped) "Unequip All" else "Equip All", DiagramAccent.Primary, highlight = true)
    )
)

internal fun packToContainerPopup(containers: List<String> = listOf("Garage Bin", "Camping Box")) = DiagramElement.Popup(
    title = "Pack to Container",
    fields = containers.map { DiagramField(it) },
    dismissLabel = "Cancel"
)

internal fun collectionUnequipPopup(containerText: String = "back to Garage Bin") = DiagramElement.Popup(
    title = "Unequip Collection",
    body = "Would you like to repack items $containerText or leave them at your current location?",
    confirmLabel = "Repack",
    dismissLabel = "Leave here"
)

internal fun collectionSelectionBar(count: Int = 2) = DiagramElement.TopBar(
    title = "$count Selected",
    actions = listOf(
        DiagramIcon(Icons.Default.DoneAll),
        DiagramIcon(Icons.Default.Delete, highlight = true)
    )
)

internal fun addItemsRow(highlight: Boolean = true) = DiagramElement.Row(
    title = "Add Items",
    leadingIcon = Icons.Default.Add,
    highlight = highlight
)
