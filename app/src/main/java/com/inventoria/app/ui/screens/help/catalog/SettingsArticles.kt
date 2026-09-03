package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.ui.screens.help.model.*

/** Settings guides, top to bottom as the screen itself lays out. Two sections deliberately have no
 * article here even though they live on this screen: Task Types (opens its own not-yet-written
 * category) and Account & Sync (Google sign-in, invite codes, connected devices -- also its own
 * not-yet-written category, Sync & Accounts, since it's a distinct enough area of the app to want
 * its own vocabulary rather than being folded into general Settings). */
internal val settingsArticles = listOf(

    HelpArticle(
        id = "settings-focus",
        title = "Change your Focus later",
        summary = "The same three-way choice the launch prompt asked, reopened any time.",
        whatItIs = "App Focus, right at the top of Settings, reopens the same dialog the first-launch " +
            "prompt showed -- Task Tracker, Todos, or Inventory -- for whichever area you mostly use the " +
            "app for now.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the App Focus row.",
                        DiagramSpec(listOf(focusRow()))
                    ),
                    HelpStep(
                        "Pick a different area, or leave it as is.",
                        DiagramSpec(listOf(focusChoicePopup(selected = "Todos")))
                    )
                )
            )
        ),
        whyItMatters = "This is the same preference the launch prompt sets, read from the same place -- " +
            "changing your mind later about what the app is mostly for doesn't mean reinstalling to see " +
            "the prompt again.",
        related = listOf("today-focus-order"),
        keywords = listOf("focus area", "task tracker", "tab order", "dashboard")
    ),

    HelpArticle(
        id = "settings-appearance",
        title = "Dark Mode",
        summary = "One switch, no \"system default\" option.",
        whatItIs = "A single toggle for the app's theme across every screen.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The Dark Mode row, under Appearance.",
                spec = DiagramSpec(listOf(darkModeRow()))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Wallpaper-based dynamic colour is deliberately switched off, so the app's palette stays " +
                    "the same regardless of your device's wallpaper -- this toggle is the only theming " +
                    "control that does anything."
            )
        ),
        whyItMatters = "A plain on/off switch rather than a three-way \"Light / Dark / System\" picker, " +
            "since the app has no need to differ from what you explicitly asked for here.",
        keywords = listOf("theme", "light mode", "colour scheme")
    ),

    HelpArticle(
        id = "settings-currency",
        title = "Currency: automatic or chosen",
        summary = "Auto Currency reads it from your device locale; turning it off lets you pick any of them.",
        whatItIs = "Prices throughout Inventory display in one currency, either detected automatically " +
            "from your device's region or chosen manually from every currency code your device knows about.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "With Auto Currency on, the detected code shows underneath -- nothing else to pick.",
                        DiagramSpec(listOf(currencyToggleRow()))
                    ),
                    HelpStep(
                        "Turn it off to pick one manually from the full list.",
                        DiagramSpec(listOf(currencyPickerField("EUR")))
                    )
                )
            )
        ),
        whyItMatters = "This is purely a display setting -- prices are stored as plain numbers, so " +
            "switching currencies later re-labels every price shown rather than converting any of them.",
        related = listOf("inventory-add-item"),
        keywords = listOf("money", "region", "locale", "price display")
    ),

    HelpArticle(
        id = "settings-show-value",
        title = "Show Total Value",
        summary = "Hide the price rollup if you'd rather Inventory not show a dollar figure.",
        whatItIs = "Controls whether the total value of everything you own (quantity × price, summed) " +
            "appears on the Inventory tab and the Today dashboard's Inventory-focus summary card.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "With this off, Today's Inventory-focus card leads with item and collection counts instead of a value.",
                spec = DiagramSpec(listOf(showValueRow()))
            )
        ),
        whyItMatters = "Turning it off doesn't stop the app from tracking price at all -- individual " +
            "items still show their own price wherever you've entered one, this only hides the summed " +
            "total for anyone who'd rather not see a running dollar figure.",
        related = listOf("inventory-add-item", "today-timeline-kinds"),
        keywords = listOf("total value", "net worth", "price rollup", "hide value")
    ),

    HelpArticle(
        id = "settings-task-types-entry",
        title = "Task Types live in their own section",
        summary = "The Tasks section's first row opens the Task Types manager.",
        whatItIs = "Task Types -- the activity labels that let differently-named, differently-scored " +
            "sittings still count as the same thing (\"Eating with V\" and \"Eating out\" both under " +
            "\"Eating\") -- are managed on their own screen, reached from this row.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "Tapping this row opens the Task Types manager.",
                spec = DiagramSpec(listOf(taskTypesRow()))
            )
        ),
        whyItMatters = "Task Types are used from three different places -- tasks, todos and schedule " +
            "blocks -- so managing the list itself gets a dedicated screen rather than being buried in " +
            "whichever of those three happened to need editing it first.",
        related = listOf("tasks-type"),
        keywords = listOf("activity labels", "categories", "manage types")
    ),

    HelpArticle(
        id = "settings-track-interruptions",
        title = "Track Interruptions",
        summary = "The on/off switch for inner tasks -- what happens when you pause is documented on Task Tracker.",
        whatItIs = "One toggle: with it on, pausing a running task offers to start a linked \"inner " +
            "task\" capturing whatever interrupted you, auto-stopping when you resume the original.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The Track Interruptions row, under Tasks.",
                spec = DiagramSpec(listOf(interruptionsRow()))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "This is just the switch. What an inner task actually does -- interruption chains, the " +
                    "streak-break toggle -- is covered in Task Tracking's own guide."
            )
        ),
        whyItMatters = "Off by default in spirit but on by name here since some people never want to be " +
            "asked \"what interrupted you\" mid-pause -- the switch exists so that prompt can be turned " +
            "off entirely rather than dismissed every single time.",
        related = listOf("tasks-interruptions", "tasks-pause-resume"),
        keywords = listOf("inner task", "interruption", "pause prompt")
    ),

    HelpArticle(
        id = "settings-procrastination",
        title = "Procrastination penalties",
        summary = "Two independent docks on your score: low-priority todos, and flagged task Kinds.",
        whatItIs = "Two separate switches, each subtracting the same configurable point amount when " +
            "triggered: completing a todo at or below a chosen priority cutoff (or with no priority at " +
            "all), and completing a tracked task of a Kind you've flagged as a procrastination risk.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Turn on Penalize Non-Priority Todos and pick the cutoff tier -- anything at or below it (and anything unprioritized) counts.",
                        DiagramSpec(listOf(procrastinationTodoRow()))
                    ),
                    HelpStep(
                        "Turn on Penalize Procrastination Task Kinds and pick which Kinds count, from chips for all of them.",
                        DiagramSpec(listOf(procrastinationTaskRow()))
                    ),
                    HelpStep(
                        "One Penalty Points field sets how much both docks subtract per qualifying completion.",
                        DiagramSpec(listOf(DiagramElement.Popup(title = "Penalty amount", fields = listOf(penaltyAmountField()))))
                    )
                )
            )
        ),
        whyItMatters = "Both penalties are computed live from today's completed items rather than stored " +
            "on them, the same way the overdue penalty works -- so changing the cutoff or the flagged " +
            "Kinds reshapes today's score immediately rather than only affecting what you complete from " +
            "here on.",
        related = listOf("todos-priority", "tasks-kind"),
        keywords = listOf("penalty", "score", "cutoff", "low priority", "productivity")
    ),

    HelpArticle(
        id = "settings-notifications",
        title = "Enable Notifications",
        summary = "The master switch for todo alarms.",
        whatItIs = "Turns todo alarms on or off app-wide. Off silences every alarm regardless of what " +
            "you've set on individual todos, even if the system's own notification permission is granted.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The Enable Notifications row, under Notifications.",
                spec = DiagramSpec(listOf(enableNotificationsRow()))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "This section also holds the Alarm vs Notification style choice and, when the system is " +
                    "currently delaying this app's alarms, an Allow Exact Alarms row -- both covered in " +
                    "the Todos guide's alarm articles."
            )
        ),
        whyItMatters = "This can't reach the task-tracking foreground service's own notification, which " +
            "is mandatory while a task runs -- so turning this off silences todo alarms specifically, not " +
            "every notification the app can show.",
        related = listOf("todos-alarm", "todos-alarm-style"),
        keywords = listOf("silence alarms", "mute", "notification permission")
    ),

    HelpArticle(
        id = "settings-help-about",
        title = "Help, About, and Version History",
        summary = "The manual's index, what version you're on, and the full changelog.",
        whatItIs = "Three rows at the bottom of Settings: How To opens the manual's index (the same " +
            "place every tab's \"?\" falls back to when its own section isn't written yet), an About " +
            "card names the installed version, and Version History lists every update's notes.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "How To and Version History, either side of the About card.",
                spec = DiagramSpec(listOf(helpIndexRow(), versionHistoryRow()))
            )
        ),
        whyItMatters = "Version History is what the post-update \"What's New\" pop-up draws from -- " +
            "reopening it here is how to reread an update note after dismissing that pop-up once.",
        keywords = listOf("manual", "version", "changelog", "what's new", "about")
    )
)
