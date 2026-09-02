package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.ui.screens.help.model.*

/** Inventory guides, part two: everything about one item -- adding it, its photos and location,
 * containers and equipping, links to other items, and custom fields. The list screen itself
 * (search, sort, filter, drag, the detail screen) lives in InventoryListArticles.kt instead. */
internal val inventoryItemArticles = listOf(

    HelpArticle(
        id = "inventory-add-item",
        title = "Add an item",
        summary = "Name, quantity and price are the core fields; everything else is optional.",
        whatItIs = "The + button opens a form for one item: a name, a quantity, an optional price (in " +
            "your chosen currency), comma-separated category tags, and a description.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Press the + button on the Items list.",
                        DiagramSpec(listOf(addItemFab()))
                    ),
                    HelpStep("Fill in a name, and however much of the rest applies right now."),
                    HelpStep("Save. The item appears in the list immediately, sorted and grouped however the list is currently set.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "Category is comma-separated on purpose: \"Camping, Electronics\" gives one item two " +
                    "tags. Tags drive both the Filter sheet and Group-by-Category."
            )
        ),
        whyItMatters = "Quantity and price aren't just descriptive: a Total Value figure on the Today " +
            "dashboard (Inventory focus) and on the Inventory hub sums quantity × price across every " +
            "item, so keeping both current is what keeps that number honest.",
        related = listOf("inventory-photos", "inventory-location", "inventory-custom-fields"),
        keywords = listOf("new item", "create", "add", "price", "currency", "tags")
    ),

    HelpArticle(
        id = "inventory-photos",
        title = "Photos, and the primary picture",
        summary = "Camera or gallery, as many as you like, one marked as the primary.",
        whatItIs = "An item can carry any number of photos, taken with the camera or picked from the " +
            "gallery. One is the primary picture -- the star -- and it's what shows as the item's " +
            "thumbnail everywhere else in the app.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap Add in the photo strip and choose Camera or Gallery.",
                        DiagramSpec(listOf(photoStrip(count = 0)))
                    ),
                    HelpStep(
                        "Tap the star on any photo to make it the primary picture; the × removes it.",
                        DiagramSpec(listOf(photoStrip(count = 3, hasProfile = true)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A newly added photo uploads in the background and shows a Pending badge until it " +
                    "finishes -- it's already attached to the item either way, just not synced yet."
            )
        ),
        whyItMatters = "The primary picture is a deliberate choice rather than always \"the first photo " +
            "taken\", since the most identifiable angle of an item isn't necessarily the one you " +
            "happened to photograph first.",
        related = listOf("inventory-add-item"),
        keywords = listOf("camera", "gallery", "picture", "thumbnail", "star", "primary")
    ),

    HelpArticle(
        id = "inventory-location",
        title = "Give an item a location",
        summary = "Type an address, use your current GPS position, or pick a point on the map.",
        whatItIs = "Location is free text (so \"top shelf, hall closet\" works as well as a street " +
            "address) with an optional GPS coordinate attached, filled in one of three ways: typing an " +
            "address, tapping Get Current Location, or picking a point on the map.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The address field with its Pick on Map shortcut, and the Get Current Location button beneath it.",
                spec = DiagramSpec(listOf(
                    locationRow(highlight = true),
                    getLocationButton()
                ))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "An item packed inside a container with no location of its own displays \"inside " +
                    "'ContainerName'\" instead, and inherits the container's coordinates for the map -- " +
                    "see Containers."
            )
        ),
        whyItMatters = "GPS and the text field are independent: a coordinate lets the Map segment plot " +
            "the item, while the text is what every list and detail view actually displays, so an item " +
            "can have a precise map pin and still read as \"top shelf, hall closet\" everywhere else.",
        related = listOf("inventory-containers", "inventory-add-item"),
        keywords = listOf("address", "GPS", "coordinates", "map pin", "where")
    ),

    HelpArticle(
        id = "inventory-containers",
        title = "Containers: pack items inside another item",
        summary = "Mark an item as a container, then drop other items into it -- by dropdown or by drag.",
        whatItIs = "Any item can be a container: check \"This item is a container\" and other items can " +
            "be stored inside it, nested to any depth (a bin inside a shelf inside a room, say). A " +
            "contained item's row indents under its container and can be expanded or collapsed.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Check \"This item is a container (can store other items)\" on the container itself."),
                    HelpStep(
                        "On the item going inside it, pick the container from the \"Stored Inside\" dropdown -- or drag the item's row onto the container in the list instead.",
                        DiagramSpec(listOf(inventoryItemRow("Camp Stove", location = "inside \"Garage Bin\"", isContainer = false)))
                    ),
                    HelpStep(
                        "The container's own row shows a chevron to expand or collapse what's inside it.",
                        DiagramSpec(listOf(inventoryItemRow("Garage Bin", location = "Garage Shelf", isContainer = true, hasChildren = true)))
                    )
                )
            )
        ),
        whyItMatters = "A contained item with no location of its own displays as \"inside " +
            "'ContainerName'\" and inherits the container's coordinates, so packing something away " +
            "doesn't orphan it from the map -- moving the container brings everything inside it along, " +
            "on the map and in the text, without editing each item individually.",
        related = listOf("inventory-drag-drop", "inventory-location", "inventory-equip"),
        keywords = listOf("nesting", "storage", "box", "bin", "bag", "pack")
    ),

    HelpArticle(
        id = "inventory-equip",
        title = "Equip an item",
        summary = "Mark it as carried on your person -- and choose where it goes back to later.",
        whatItIs = "Equipping an item marks it as currently on your person rather than at any fixed " +
            "location; its display location reads \"Equipped (On Person)\" until you unequip it.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Tap the equip icon on a row, or the Equip button on the item's detail screen."),
                    HelpStep(
                        "Unequipping an item that was packed in a container before asks whether to put it back.",
                        DiagramSpec(listOf(equipRepackPopup()))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "That prompt only appears when the item has a container to go back to. An item with no " +
                    "prior container just unequips straight away."
            )
        ),
        whyItMatters = "Remembering the last container (rather than forgetting it the moment you equip) " +
            "is what makes Repack a one-tap action later, instead of having to re-drag the item back to " +
            "wherever it came from.",
        related = listOf("inventory-containers"),
        keywords = listOf("carry", "wear", "on person", "unequip", "repack")
    ),

    HelpArticle(
        id = "inventory-links",
        title = "Linked items",
        summary = "One item's location follows another's, without being packed inside it.",
        whatItIs = "A link makes one item (the follower) inherit another's (the leader's) location " +
            "whenever the follower has none of its own -- for accessories that travel with a main item " +
            "without literally living inside it, like a charger that's \"wherever the laptop is\" even " +
            "though it isn't packed in the laptop bag.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "Viewed from Work Laptop's own detail screen, the Phone Charger link reads \"Follows this item\".",
                spec = DiagramSpec(listOf(linkRow("Phone Charger", rowFollows = true)))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Give the follower its own location and the link stops mattering for display -- the " +
                    "item's own field always wins over an inherited one."
            )
        ),
        whyItMatters = "A link is deliberately not the same relationship as a container: packing " +
            "something moves it physically into another item, while linking just says \"track this one's " +
            "location the same way\" -- the two mechanisms exist because \"contained by\" and \"goes " +
            "wherever\" are genuinely different facts about two items.",
        related = listOf("inventory-drag-drop", "inventory-containers"),
        keywords = listOf("follow", "leader", "accessory", "relationship")
    ),

    HelpArticle(
        id = "inventory-custom-fields",
        title = "Custom fields",
        summary = "Arbitrary key-value pairs for whatever the built-in fields don't cover.",
        whatItIs = "Any number of free-form key/value pairs attached to an item -- a serial number, a " +
            "warranty date, a size, anything the standard fields don't have a place for.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Tap + next to Custom Fields on the edit screen."),
                    HelpStep("Fill in a key and a value; add as many pairs as you need, or remove any with its own ×.")
                )
            )
        ),
        whyItMatters = "Kept as an open key-value map rather than a fixed set of extra fields, since what " +
            "one household needs to track (warranty dates, serial numbers, sizes) is never the same list " +
            "another household would pick.",
        related = listOf("inventory-add-item"),
        keywords = listOf("metadata", "extra fields", "serial number", "attributes")
    )
)
