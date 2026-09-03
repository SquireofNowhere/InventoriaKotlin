package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.ui.screens.help.model.*

/** Collections guides: named, reusable sets of items for packing and readiness -- reached from
 * the Inventory tab but a distinct enough idea to be its own manual category. */
internal val collectionsArticles = listOf(

    HelpArticle(
        id = "collections-overview",
        title = "What a Collection is",
        summary = "A named set of items you pack, equip and track readiness for as a group.",
        whatItIs = "A Collection is a list of existing inventory items grouped under one name -- " +
            "\"Emergency Kit\", \"Camera Bag\", a packing list for a trip. It doesn't duplicate the " +
            "items; it just remembers which ones belong to the set, so you can pack, equip or check the " +
            "readiness of all of them together.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "A collection card: its name, tags, and how many items it contains.",
                spec = DiagramSpec(listOf(collectionCard()))
            )
        ),
        whyItMatters = "Membership is the only thing a Collection stores -- an item's own name, price, " +
            "location and photos stay defined once on the item itself, so editing an item anywhere " +
            "updates it everywhere it's grouped, with nothing to keep in sync by hand.",
        related = listOf("collections-create", "collections-readiness"),
        keywords = listOf("packing list", "kit", "set", "group items")
    ),

    HelpArticle(
        id = "collections-create",
        title = "Create a Collection",
        summary = "Name, icon and colour, tags, and a type -- most of it optional.",
        whatItIs = "The + button opens a blank collection: a name, an icon and colour (the same picker " +
            "an inventory item's own icon/colour uses), comma-separated tags, a description, a Collection " +
            "Type, and a \"same location\" checkbox.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Press the + button on the Collections list.",
                        DiagramSpec(listOf(addCollectionFab()))
                    ),
                    HelpStep("Name it, and pick an icon and colour -- tap the circle to open the picker."),
                    HelpStep(
                        "Optionally pick a Collection Type.",
                        DiagramSpec(listOf(collectionTypeChips()))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Collection Type and \"Require items to be in the same location\" are both purely " +
                    "descriptive right now -- they're saved with the collection but nothing reads them " +
                    "back to change any behaviour."
            )
        ),
        whyItMatters = "A blank collection is deliberately not required to have any items yet -- you " +
            "create the container for the idea first (\"Emergency Kit\"), then add whatever belongs in " +
            "it whenever you get to it.",
        related = listOf("collections-add-items", "inventory-photos"),
        keywords = listOf("new collection", "icon picker", "collection type", "tags")
    ),

    HelpArticle(
        id = "collections-add-items",
        title = "Add items to a Collection",
        summary = "The same Inventory list, in picker mode -- tap items to stage them, then Save.",
        whatItIs = "Add Items opens the full Inventory list in a picker mode: tapping a row toggles its " +
            "membership rather than opening its detail. Nothing is written until you confirm.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "From a collection's detail screen, tap Add Items.",
                        DiagramSpec(listOf(addItemsRow()))
                    ),
                    HelpStep("Tap items to check or uncheck them -- this only stages the change."),
                    HelpStep("Tap the checkmark to save. Backing out with staged changes still pending asks first.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "This picker is also how an item leaves a collection -- there's no separate \"remove\" " +
                    "button on the collection's own item rows, only on this picker and on the collection's " +
                    "own delete."
            )
        ),
        whyItMatters = "Reusing the exact same list (search, sort, filter, containers and all) rather " +
            "than a second simplified item chooser means finding the right item to add works exactly the " +
            "way finding it anywhere else in Inventory does.",
        related = listOf("collections-overview", "inventory-search-sort-group"),
        keywords = listOf("membership", "checklist", "select items", "remove from collection")
    ),

    HelpArticle(
        id = "collections-readiness",
        title = "Readiness: Available, Packed, Equipped",
        summary = "One percentage and three counts, recomputed live from the items themselves.",
        whatItIs = "Readiness answers \"how ready is this kit right now\": Available counts members you " +
            "actually own enough of, and Packed / Equipped further break that down by where those " +
            "available items currently are.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The readiness line under a collection's header.",
                spec = DiagramSpec(listOf(readinessRow()))
            ),
            HelpBlock.Bullets(
                listOf(
                    "Available: the item exists and its quantity meets what the collection needs (1, unless increased -- there's no UI yet to require more than 1 of an item).",
                    "Packed: available, and currently inside some container.",
                    "Equipped: available, and currently marked as carried on your person.",
                    "The percentage is Available ÷ Total membership count -- Packed and Equipped are informational, not part of the percentage."
                )
            )
        ),
        whyItMatters = "Readiness is computed live from the items' own current state every time, never " +
            "stored on the collection -- so packing, equipping or simply running out of stock on a member " +
            "item updates the percentage immediately, with nothing to manually refresh.",
        related = listOf("collections-overview", "collections-batch-actions"),
        keywords = listOf("percentage", "progress", "stock", "ready")
    ),

    HelpArticle(
        id = "collections-batch-actions",
        title = "Pack, unpack, equip and unequip the whole collection",
        summary = "Four actions, two button pairs, acting on every member item at once.",
        whatItIs = "Pack All moves every item in the collection into one container you choose. Unpack " +
            "All takes them all back out (to no container). Equip All marks every member as carried on " +
            "your person. Unequip All takes off whatever's currently equipped, offering to repack it.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The two button pairs -- each label flips once anything in the collection is packed or equipped.",
                spec = DiagramSpec(listOf(collectionActionButtons()))
            ),
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Pack All asks which container to pack everything into.",
                        DiagramSpec(listOf(packToContainerPopup()))
                    ),
                    HelpStep(
                        "Unequip All asks whether to repack, the same prompt a single item's unequip shows.",
                        DiagramSpec(listOf(collectionUnequipPopup()))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Each action only touches items actually in the collection, and only where it applies -- " +
                    "Unequip All, for instance, does nothing to a member that was never equipped."
            )
        ),
        whyItMatters = "These are the same underlying moves a single item's own Move/Equip/Unequip make, " +
            "just applied to every collection member as one action -- there's no separate \"collection " +
            "packing\" data model, just the same item fields changed in bulk.",
        related = listOf("collections-readiness", "inventory-containers", "inventory-equip"),
        keywords = listOf("pack all", "unpack all", "equip all", "unequip all", "batch", "bulk")
    ),

    HelpArticle(
        id = "collections-manage",
        title = "Search, select and delete Collections",
        summary = "The list works like Inventory's own: search, long-press to select, bulk delete.",
        whatItIs = "The Collections list has its own search field, and long-pressing a card enters " +
            "selection mode for deleting more than one at once.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "Search above the list; selection mode's Select All and Delete actions in the top bar.",
                spec = DiagramSpec(listOf(collectionSearchRow(), collectionSelectionBar()))
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Deleting a collection never deletes the items in it -- only the grouping itself goes " +
                    "away. Unlike items, tasks and todos elsewhere in the app, this delete has no Undo " +
                    "snackbar -- the confirmation dialog is the only safety net before it happens."
            )
        ),
        whyItMatters = "A collection is just membership data, so deleting one never touches the items " +
            "it referenced -- they stay exactly as they were, in Inventory and in any other collection " +
            "they also belong to.",
        related = listOf("collections-overview"),
        keywords = listOf("search collections", "multi select", "delete collection")
    )
)
