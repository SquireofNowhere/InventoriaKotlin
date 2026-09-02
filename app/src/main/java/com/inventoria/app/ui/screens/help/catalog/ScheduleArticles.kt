package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/** Schedule guides: the Todos tab's second segment, a week strip and a day timeline. Kept apart
 * from TodoListArticles.kt because it is a different screen with a different vocabulary -- blocks,
 * not rows -- even though both live one tap apart under the same tab. */
internal val scheduleArticles = listOf(

    HelpArticle(
        id = "schedule-overview",
        title = "What the Schedule shows",
        summary = "A week strip to pick a day, and that day as one 24-hour timeline.",
        whatItIs = "The Schedule segment lines up two different things on the same hour scale: schedule " +
            "blocks (designated time -- what an hour is *for*) painted flat on the paper across the whole " +
            "width, and tracked task segments (used time -- what an hour was *actually spent on*) as solid " +
            "cards on the right side of a divider line. Todos due that day appear too, as hairlines at " +
            "their due time.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Switch to the Schedule segment.",
                        DiagramSpec(listOf(todosSegmentedControl("Schedule")))
                    ),
                    HelpStep(
                        "Blocks sit flat and full-width behind everything, in their Kind's colour. Tracked tasks are solid cards on the right, past the divider.",
                        DiagramSpec(listOf(
                            scheduleBlockRow("Deep Work", "09:00 – 11:00", TaskKind.PEACOCK, type = "Coding"),
                            scheduleTaskRow("Client email", "09:15 – 09:40", TaskKind.PEACOCK)
                        ))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A block is the plan; a task is the fact. They're drawn differently on purpose so a glance " +
                    "shows how the day compared to what it was meant to be, without needing two separate " +
                    "screens to compare them."
            )
        ),
        whyItMatters = "Blocks stay full width rather than being squeezed aside so that even a hour with " +
            "several tracked task segments on it still shows the whole plan underneath, not just a sliver " +
            "of it. The divider line is what actually separates \"what the hour was for\" from \"what it " +
            "was used for\" -- a task's own colour never has to fight a block's for the same pixels.",
        related = listOf("schedule-add-block", "schedule-tasks-on-timeline"),
        keywords = listOf("calendar", "timeline", "day view", "planned vs actual", "week strip")
    ),

    HelpArticle(
        id = "schedule-add-block",
        title = "Add a schedule block",
        summary = "Tap an empty hour, or the + button.",
        whatItIs = "A block is a designated stretch of time with a title, a Kind for its colour, and a " +
            "span. Tapping empty space on the timeline opens one pre-filled to that hour; the FAB opens " +
            "one at 09:00 regardless of where you're scrolled.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap an empty hour on the timeline, or press the + button.",
                        DiagramSpec(listOf(addBlockFab()))
                    ),
                    HelpStep(
                        "Fill in what the time is for, its Kind, and the span.",
                        DiagramSpec(listOf(scheduleBlockPopup(isNew = true, highlightField = 0)))
                    ),
                    HelpStep("Save. The block appears as a flat wash of its Kind's colour across the full width of that span.")
                )
            )
        ),
        whyItMatters = "Blocks and tasks are lined up on the same day deliberately so the screen can draw " +
            "them side by side, but one is never turned into the other -- creating a block never starts a " +
            "timer, and stopping a timer never edits a block. They stay two independent records that " +
            "merely happen to share a timeline.",
        related = listOf("schedule-edit-block", "schedule-block-type"),
        keywords = listOf("new block", "plan", "designate", "add", "create")
    ),

    HelpArticle(
        id = "schedule-edit-block",
        title = "Edit or delete a block",
        summary = "Tap the block itself to reopen the same dialog it was created in.",
        whatItIs = "Tapping any existing block opens it for editing, with a Delete button alongside Save " +
            "now that there's something to remove.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the block on the timeline.",
                        DiagramSpec(listOf(scheduleBlockRow("Deep Work", "09:00 – 11:00", TaskKind.PEACOCK, highlight = true)))
                    ),
                    HelpStep(
                        "Change anything, or press Delete.",
                        DiagramSpec(listOf(scheduleBlockPopup(isNew = false)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "A deleted block offers an Undo snackbar for a few seconds, same as everywhere else in the app."
            )
        ),
        whyItMatters = "The same dialog handles both creating and editing, so a block never has two " +
            "different-looking forms depending on how you got to it.",
        related = listOf("schedule-add-block"),
        keywords = listOf("change", "remove", "undo", "delete block")
    ),

    HelpArticle(
        id = "schedule-repeat-weekly",
        title = "Repeat a block every week",
        summary = "One switch in the block dialog -- Just this day, or every week from here on.",
        whatItIs = "A block can repeat on the same weekday, every week, starting from the date it was " +
            "created on. It's a single switch, not a separate recurrence editor.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "In the block dialog, turn on Repeat Weekly.",
                        DiagramSpec(listOf(scheduleBlockPopup(isNew = true, highlightField = 4)))
                    ),
                    HelpStep(
                        "A repeating block shows a small repeat icon on the timeline.",
                        DiagramSpec(listOf(scheduleBlockRow("Deep Work", "09:00 – 11:00", TaskKind.PEACOCK, repeats = true)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Editing a repeating block's time or title changes every occurrence, since they're all the " +
                    "same underlying block rather than separate copies. Deleting it removes every occurrence too."
            )
        ),
        whyItMatters = "A recurring block is one record that the timeline redraws onto every matching " +
            "weekday, not a batch of individually-created blocks -- which is what makes turning the switch " +
            "back off, or deleting it outright, a single action instead of a cleanup job.",
        related = listOf("schedule-add-block", "schedule-edit-block"),
        keywords = listOf("recurring", "every week", "weekly", "routine")
    ),

    HelpArticle(
        id = "schedule-block-type",
        title = "Give a block a Task Type",
        summary = "Optional -- and it's what lets a plan and its tracked time count as the same activity.",
        whatItIs = "A block can carry a Task Type on top of its Kind. Left unset, it simply has none; the " +
            "Type only matters once a real tracked task is started from the block.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "In the block dialog, pick a Task Type the same way you would on a todo or a task.",
                        DiagramSpec(listOf(scheduleBlockPopup(isNew = true, highlightField = 2)))
                    ),
                    HelpStep(
                        "The Type shows as a small chip in the block's own colour, next to its time span.",
                        DiagramSpec(listOf(scheduleBlockRow("Deep Work", "09:00 – 11:00", TaskKind.PEACOCK, type = "Coding")))
                    )
                )
            )
        ),
        whyItMatters = "Today's Now card can start a real tracked task straight from the hour a block " +
            "says you should be in -- and when it does, that task lands under the block's own Task Type, " +
            "so planned hours and tracked hours roll up as the same activity rather than two that happen " +
            "to share a name. Left unset, a task started that way falls back to whatever the block's title " +
            "has already learned as its usual type.",
        related = listOf("schedule-add-block", "tasks-type"),
        keywords = listOf("activity", "category", "plan type")
    ),

    HelpArticle(
        id = "schedule-todos-on-timeline",
        title = "Todos on the timeline",
        summary = "A hairline at the due time, in the todo's priority colour -- tap to tick it off.",
        whatItIs = "A todo due at a specific time on the selected day draws a hairline straight across the " +
            "timeline at that minute, so it's visible against whatever block or task happens to be there " +
            "too. An all-day todo (no time set) appears above the timeline instead, in its own strip.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "A todo's due-time marker crosses the whole timeline, independent of any block or task drawn at that hour.",
                spec = DiagramSpec(listOf(todoDueMarker("Call the vet", "14:00")))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Tapping a todo marker only ticks it off. Editing the todo itself -- its title, deadline, " +
                    "priority -- stays on the Todos segment, one swipe away."
            )
        ),
        whyItMatters = "Todos are read-mostly here deliberately: the Schedule segment lines things up for " +
            "comparison, it doesn't try to be a second place to fully manage every kind of item it shows.",
        related = listOf("todos-deadline", "schedule-overview"),
        keywords = listOf("deadline marker", "due", "hairline")
    ),

    HelpArticle(
        id = "schedule-tasks-on-timeline",
        title = "Tracked tasks on the timeline",
        summary = "Solid cards on the right side of the divider -- tap one to edit it.",
        whatItIs = "Every tracked task segment that overlaps the selected day appears as a solid card on " +
            "the right side of the timeline's divider line, in its own Kind's colour. A still-running task " +
            "visibly grows down the day as time passes.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Find a task on the right side of the timeline.",
                        DiagramSpec(listOf(scheduleTaskRow("Client email", "09:15 – 09:40", TaskKind.PEACOCK)))
                    ),
                    HelpStep("Tap it to open that task's own edit screen on Task Tracker -- its name, Kind, Type and time span.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Several overlapping tasks lane-pack side by side within the right-hand area, the same way " +
                    "overlapping blocks would on the left."
            )
        ),
        whyItMatters = "The Task Tracker owns everything about a tracked task; the Schedule segment only " +
            "displays it lined up against the day's plan. Tapping a task here is a shortcut to that real " +
            "edit screen rather than a second, competing place to change the same data.",
        related = listOf("schedule-overview", "tasks-start"),
        keywords = listOf("tracked time", "actual", "session", "edit task")
    ),

    HelpArticle(
        id = "schedule-week-navigation",
        title = "Navigate the week strip",
        summary = "Arrows move the strip; tapping a day selects it; a Today button jumps back.",
        whatItIs = "The strip above the timeline shows seven days at a time. Its arrows move the whole " +
            "strip a week at once without changing which day is selected -- the selected day can scroll " +
            "out of view, the same way flipping a paper calendar would.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "Seven day cells; tapping one selects it without necessarily moving the strip itself.",
                spec = DiagramSpec(listOf(weekStripNote()))
            ),
            HelpBlock.Bullets(
                listOf(
                    "Tap a day cell to select it and load its timeline.",
                    "Each cell shows up to three small dots -- one per kind of thing that day has: a block, a task, or a todo -- so you can spot a busy day without opening it.",
                    "The Today icon in the top bar jumps the strip and the selection back to today in one tap, wherever you've navigated to."
                )
            ),
            HelpBlock.Diagram(
                caption = "The Today button, in the Schedule segment's own top-bar action.",
                spec = DiagramSpec(listOf(DiagramElement.TopBar(title = "Todos", actions = listOf(todayIcon(true)))))
            )
        ),
        whyItMatters = "Moving the strip and moving the selection are kept as two separate actions because " +
            "browsing nearby days shouldn't have to commit to leaving the day you're actually looking at " +
            "until you deliberately tap one.",
        related = listOf("schedule-overview"),
        keywords = listOf("week", "navigate", "jump to today", "day picker")
    )
)
