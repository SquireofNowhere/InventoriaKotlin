package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.ui.screens.help.model.*

/** Inventory guides, part one: the list itself -- finding things, sorting and grouping them,
 * filtering, dragging items around, and the detail screen one tap away from any row. Adding and
 * editing an item's own fields live in InventoryItemArticles.kt instead. */
internal val inventoryListArticles = listOf(

    HelpArticle(
        id = "inventory-overview",
        title = "The Inventory tab",
        summary = "Items and Map, switched locally -- the same arrangement as Todos and Schedule.",
        whatItIs = "The Inventory tab holds two segments: Items, the searchable list every other " +
            "Inventory guide covers, and Map, a location view of everything with a GPS position. " +
            "Collections (sets of items for packing and readiness) are reached from Items rather than " +
            "being a segment of their own.",
        blocks = listOf(
            HelpBlock.Callout(
                CalloutKind.Note,
                "Map and Collections are their own areas of the guide -- this category covers the Items " +
                    "list: adding, finding, organising and moving things around in it."
            )
        ),
        whyItMatters = "One list underlies everything -- Map plots the same items by their resolved " +
            "location, and a Collection is a named set drawn from the same table -- so an edit made from " +
            "any of the three shows up in the other two immediately, never a separate copy to keep in sync.",
        related = listOf("inventory-search-sort-group", "inventory-add-item"),
        keywords = listOf("items tab", "segments", "hub", "map", "collections")
    ),

    HelpArticle(
        id = "inventory-search-sort-group",
        title = "Search, sort and group",
        summary = "One field and two icons, sharing a row above the list.",
        whatItIs = "Search matches name, location and category as you type. Sort orders the whole list " +
            "one way at a time (name, most recently updated, highest quantity, highest price). Group " +
            "clusters it under headers instead -- tapping the icon cycles Category, Location, Collection " +
            "and back to none.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The search field, and the sort / group / filter icons that share its row.",
                spec = DiagramSpec(listOf(inventoryToolbarRow()))
            ),
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the sort icon for a menu of one-at-a-time orderings.",
                        DiagramSpec(listOf(sortMenuPopup()))
                    ),
                    HelpStep(
                        "Tap the group icon to cycle grouping -- its glyph changes with the current choice, shown here mid-cycle on Location.",
                        DiagramSpec(listOf(inventoryToolbarRow(highlight = 1)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "Any sort or group other than the defaults shows a dismissible chip under the search row, " +
                    "so it's obvious the list isn't showing everything in its normal order -- tap the chip's " +
                    "× to reset it."
            )
        ),
        whyItMatters = "Sort and group are independent: grouping doesn't disable sorting, it just orders " +
            "items within each header the same way sort would order the whole list. Both are saved " +
            "preferences, so the list looks the way you left it next time you open the tab.",
        related = listOf("inventory-filter", "inventory-overview"),
        keywords = listOf("find", "order", "category", "location", "collection", "arrange")
    ),

    HelpArticle(
        id = "inventory-filter",
        title = "Filter by tag or collection",
        summary = "The filter sheet: pick tags and collections, choose ALL vs ANY, or invert it.",
        whatItIs = "The filter icon opens a sheet listing every tag (from items' Category field) and " +
            "every Collection. Selecting some narrows the list to items matching them, following two " +
            "independent switches: Hard Filter (must match every selection) vs its opposite (any one is " +
            "enough), and Invert (show what does NOT match instead).",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The two logic switches at the top of the filter sheet.",
                spec = DiagramSpec(listOf(filterPopup()))
            ),
            HelpBlock.Diagram(
                caption = "Tags read from every item's Category field -- tap one or more to filter by them.",
                spec = DiagramSpec(listOf(tagChipRow(listOf("Camping"), listOf("Camping", "Electronics", "Kitchen"))))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A badge dot appears on the filter icon whenever any tag or collection is selected, even " +
                    "with the sheet closed -- and a filtered list highlights its matches in bold rather " +
                    "than simply hiding everything else, so a matching item deep inside a container is " +
                    "still visible with its ancestors auto-expanded to reach it."
            )
        ),
        whyItMatters = "Hard/Invert apply to tags and collections together, not separately, so \"Camping " +
            "AND NOT already-in-a-collection\" is expressible as one filter state rather than two you'd " +
            "have to combine mentally.",
        related = listOf("inventory-search-sort-group"),
        keywords = listOf("tag filter", "collection filter", "AND", "OR", "narrow down")
    ),

    HelpArticle(
        id = "inventory-drag-drop",
        title = "Drag an item to move, link, or unequip it",
        summary = "One gesture, three outcomes depending on where you drop.",
        whatItIs = "Press and drag any row (past a long-press) to pick it up. Where you release decides " +
            "what happens: drop it on a container to pack it inside; drop it on a plain item to link the " +
            "two; drag it up to the top of the list to remove it from wherever it currently sits.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "Onto a container (a \"storage\" item): moves the dragged item inside it.",
                    "Onto a non-container item: links the two (see Linked items).",
                    "Up past the top of the list: removes the item from its container, or unequips it if it was equipped."
                )
            ),
            HelpBlock.Diagram(
                caption = "The drop target tints to say which of the three outcomes is about to happen.",
                spec = DiagramSpec(listOf(
                    dragGhost("Moving into container", DiagramAccent.Primary),
                    dragGhost("Linking items", DiagramAccent.Success),
                    dragGhost("Removing from container", DiagramAccent.Danger)
                ))
            )
        ),
        whyItMatters = "A single drag covering three different outcomes only works because they're mutually " +
            "exclusive by drop target -- a container and a plain item can never be confused for each " +
            "other, so the gesture never has to ask which one you meant.",
        related = listOf("inventory-containers", "inventory-links"),
        keywords = listOf("drag and drop", "pack", "move item", "reorganise")
    ),

    HelpArticle(
        id = "inventory-select-merge",
        title = "Select multiple items: delete, or merge",
        summary = "Long-press to select, then bulk-delete or merge duplicates into one.",
        whatItIs = "Long-pressing a row opens its context menu, which includes Select; tapping further " +
            "rows adds them. With two or more selected, Merge & Rename becomes available: it combines " +
            "their quantities and custom fields into one item under a new name and deletes the rest.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "Selection mode's top bar: Merge (two or more selected) and Delete.",
                spec = DiagramSpec(listOf(mergeSelectionBar()))
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Merging keeps the first selected item as the base -- its category, price and location " +
                    "win -- and sums every selected item's quantity onto it. Custom fields and " +
                    "descriptions from all of them are combined; everything else about the other items is " +
                    "discarded when they're deleted."
            )
        ),
        whyItMatters = "Merge exists for exactly one situation: the same physical stock ended up as two " +
            "separate rows (added twice, or via two sync paths) and should be one entry with the right " +
            "total quantity, not a delete-and-retype.",
        related = listOf("inventory-search-sort-group"),
        keywords = listOf("multi select", "bulk delete", "combine", "duplicate")
    ),

    HelpArticle(
        id = "inventory-item-detail",
        title = "The item detail screen",
        summary = "Everything about one item: stats, actions, what's inside it, and what it's linked to.",
        whatItIs = "Tapping a row (outside selection mode) opens its full detail: photo gallery, " +
            "quantity and price, description, location (tap to view on the map), Equip/Unequip and Move " +
            "buttons, and -- only when relevant -- what's stored inside it, what it's linked to, which " +
            "Collections it belongs to, and its custom fields.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "Items Inside only appears for a container, with its own + to add something directly into it.",
                    "Linked Items only appears when at least one link exists, each row saying which direction it goes.",
                    "Collections lists every Collection the item is a member of, tapping through to that collection.",
                    "Move opens the same container picker the drag gesture uses, for when dragging isn't convenient."
                )
            )
        ),
        whyItMatters = "Every section on this screen is conditional -- a plain item with no links, no " +
            "collections and no contents shows none of those headers, so a simple item's detail page stays " +
            "as short as the item actually is.",
        related = listOf("inventory-containers", "inventory-links", "inventory-equip"),
        keywords = listOf("details", "item page", "view item")
    )
)
