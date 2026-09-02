package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/** Today guides: the app's home screen, and the one dashboard that leads with whatever's
 * happening right now rather than a fixed layout. Ordered roughly top-to-bottom as the screen
 * actually lays out (nudge, Now, quick capture, up next, the rest), since that's also the order a
 * new user encounters them in. */
internal val todayArticles = listOf(

    HelpArticle(
        id = "today-now-card",
        title = "The Now card",
        summary = "What's running, what's paused, or what your Schedule says this hour is for.",
        whatItIs = "The Now card always leads the dashboard and always shows exactly one of four " +
            "things, in priority order: a running session, a paused one, a schedule block covering " +
            "this hour, or -- if none of those -- whatever's next later today.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "Running or Paused: every live session, tap the card to jump to Task Tracker for pause/stop/details.",
                    "Planned: the schedule block covering right now, with a Start This button.",
                    "Idle: \"Nothing running\", plus whatever's next later today if anything is."
                )
            ),
            HelpBlock.Diagram(
                caption = "A running session on the Now card -- tapping anywhere on the card opens Task Tracker.",
                spec = DiagramSpec(listOf(nowLiveRow("Writing", "00:18:42", TaskKind.PEACOCK, highlight = true)))
            ),
            HelpBlock.Diagram(
                caption = "Idle, with nothing planned right now: the tracker is one tap away either way.",
                spec = DiagramSpec(listOf(DiagramElement.ChipRow(listOf(openTrackerChip(), planDayChip()))))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "The Now card can start a session but never pauses or stops one -- that runs the " +
                    "tracker's interruption-chain and Flow Mode logic, so a live session always taps " +
                    "through to Task Tracker for those instead of duplicating the controls here."
            )
        ),
        whyItMatters = "It re-checks itself once a minute on its own, so a block starting or ending " +
            "flips the card without you needing to reopen the screen -- the one thing on this dashboard " +
            "that changes minute to minute gets to sit above everything that doesn't.",
        related = listOf("today-start-block", "tasks-start", "schedule-overview"),
        keywords = listOf("running", "paused", "current", "home screen", "dashboard")
    ),

    HelpArticle(
        id = "today-start-block",
        title = "Start what your schedule planned",
        summary = "\"Start This\" on a Planned Now card begins tracking it immediately.",
        whatItIs = "When nothing is running and a schedule block covers the current hour, the Now card " +
            "shows that block and offers to start a real tracked session for it on the spot -- named " +
            "after the block, in the block's Kind and Task Type.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "See the planned block on the Now card.",
                        DiagramSpec(listOf(scheduleBlockRow("Deep Work", "09:00 – 11:00", TaskKind.PEACOCK, type = "Coding", highlight = true)))
                    ),
                    HelpStep(
                        "Press Start This to begin tracking it now, or Schedule to open the full timeline instead.",
                        DiagramSpec(listOf(nowCardActions("Start This", "Schedule")))
                    )
                )
            )
        ),
        whyItMatters = "Starting it this way carries the block's Task Type onto the new task, the same " +
            "as starting a session from a Schedule block directly -- so a hard-earned plan and the time " +
            "actually spent on it land under the same activity, whichever route you started it from.",
        related = listOf("today-now-card", "schedule-block-type"),
        keywords = listOf("start this", "one tap", "begin", "plan")
    ),

    HelpArticle(
        id = "today-nudge-banner",
        title = "The red nudge banner",
        summary = "Overdue, past due today, or ringing within the hour -- above everything else.",
        whatItIs = "A red banner appears at the very top of Today, above even the Now card, whenever " +
            "there's an incomplete todo that's overdue, past its time today, or due (or about to alarm) " +
            "within the next hour. Nothing shows here when there's nothing to say.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The banner's counts line, and the single soonest thing underneath it.",
                spec = DiagramSpec(listOf(nudgeBanner()))
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Tapping the banner opens the Todos list, where all of it can actually be dealt with."
            )
        ),
        whyItMatters = "The whole point is putting a deadline you're about to miss on the home screen " +
            "before the alarm rings, not only in the alarm itself -- by the time a rent reminder actually " +
            "fires, this banner has usually already been telling you for up to an hour.",
        related = listOf("today-up-next", "todos-alarm"),
        keywords = listOf("overdue", "warning", "banner", "reminder", "due soon")
    ),

    HelpArticle(
        id = "today-quick-capture",
        title = "Quick capture",
        summary = "One field: Enter (or the checklist icon) makes a todo, the play button starts tracking.",
        whatItIs = "A single text field for getting a thought down without leaving the home screen. What " +
            "you type becomes either a todo due today, or a task tracked from this second -- your choice, " +
            "same text either way.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Type into the field.",
                        DiagramSpec(listOf(quickCaptureRow()))
                    ),
                    HelpStep(
                        "Press Enter or the checklist icon to add it as a todo due today (with the same default alarm a new todo gets); press the play icon to start tracking it immediately instead.",
                        DiagramSpec(listOf(quickCaptureRow(highlightTrailing = 1)))
                    ),
                    HelpStep("The field clears itself. The result shows up in the todo list or the Now card above, which is the confirmation.")
                )
            )
        ),
        whyItMatters = "Kind, Task Type and priority all stay at their defaults either way -- this is for " +
            "getting the thought captured before it's gone, not for filling in every field on the spot. A " +
            "todo made this way can always be opened and filled in properly later.",
        related = listOf("today-now-card", "todos-add"),
        keywords = listOf("capture", "jot down", "note", "add quickly")
    ),

    HelpArticle(
        id = "today-up-next",
        title = "Up next",
        summary = "The next few things on today's clock, each with a countdown.",
        whatItIs = "A card listing whatever's coming later today -- schedule blocks and todos due at a " +
            "time -- soonest first, up to three. It only appears when there's actually something later; " +
            "an empty Up Next would just repeat what an Idle Now card already says.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "A block and a todo, each showing its clock time and a plain-language countdown.",
                spec = DiagramSpec(listOf(
                    upNextRow("11:00", "in 1 h 20 min", "Team standup", "Schedule · until 11:30", DiagramAccent.Kind(TaskKind.BLUEBERRY)),
                    upNextRow("14:00", "in 4 h 20 min", "Call the vet", "Todo due", DiagramAccent.Warning, hasAlarm = true)
                ))
            )
        ),
        whyItMatters = "A block and a todo are drawn from two different tables entirely, but they're " +
            "merged into one list and sorted by clock time together -- what matters here is when " +
            "something happens next, not which kind of thing it technically is.",
        related = listOf("today-nudge-banner", "schedule-overview"),
        keywords = listOf("next", "countdown", "later today", "upcoming")
    ),

    HelpArticle(
        id = "today-todo-list",
        title = "Today's todo list",
        summary = "The same rows as the Todos screen -- check off or start tracking, editing stays there.",
        whatItIs = "Everything due today (including anything carried in as overdue) shows as a plain " +
            "list, the same row style the Todos screen itself uses. Tapping a row's checkbox or play " +
            "button acts immediately; tapping the row itself opens the Todos screen instead of an edit " +
            "dialog here.",
        blocks = listOf(
            HelpBlock.Callout(
                CalloutKind.Note,
                "Dragging to reparent, deleting, and folding sub-todos away all live on the Todos screen " +
                    "only -- Today shows the list, it doesn't manage it."
            )
        ),
        whyItMatters = "Today reads the todo list through its own copy of the same view model the Todos " +
            "screen uses, kept deliberately separate -- so opening an edit dialog for a row tapped here " +
            "would set state on an instance nothing on screen is watching. Sending the tap to Todos " +
            "instead is what actually works, not a missing feature.",
        related = listOf("todos-sections", "todos-complete"),
        keywords = listOf("due today", "todo list", "checklist")
    ),

    HelpArticle(
        id = "today-timeline-kinds",
        title = "The day's timeline and kind breakdown",
        summary = "How today has gone so far, and where the tracked minutes went.",
        whatItIs = "Two cards summarise today's tracked time: a 24-hour timeline bar on the gradient " +
            "header showing elapsed time and a now-line, and a donut breaking today's minutes down by " +
            "Kind underneath it.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The refresh icon in Today's top bar triggers a manual sync, so both cards catch up immediately rather than waiting for the next background pass.",
                spec = DiagramSpec(listOf(todayTopBar(highlight = true)))
            ),
            HelpBlock.Bullets(
                listOf(
                    "The timeline card is the same headline treatment the Inventory-focus dashboard uses for its own summary card, so the lead card reads the same whichever focus is on top.",
                    "The kind donut only counts today's tasks -- for lifetime totals, see Productivity Stats on Task Tracker."
                )
            )
        ),
        whyItMatters = "Both cards read straight off the same task list the Task Tracker itself uses, so " +
            "there's no separate \"dashboard\" copy of your time that could drift from what the tracker " +
            "actually shows.",
        related = listOf("today-now-card"),
        keywords = listOf("chart", "donut", "breakdown", "24 hour", "overview")
    ),

    HelpArticle(
        id = "today-focus-order",
        title = "Focus: what leads the dashboard",
        summary = "One Settings choice reorders both the nav bar and this screen's cards.",
        whatItIs = "Focus is a single Settings choice -- Task Tracker, Todos, or Inventory -- for what " +
            "you mostly use the app for. It moves that tab next to Today in the nav bar, and reorders " +
            "Today's cards: Task Tracker focus puts the timeline and kind donut before the todo list, " +
            "Todos focus puts the todo list first, and Inventory focus adds its own value/count summary " +
            "card ahead of everything else -- including the Now card.",
        blocks = listOf(
            HelpBlock.Callout(
                CalloutKind.Tip,
                "Asked once on first launch, and changeable any time after in Settings. Every tab stays " +
                    "reachable regardless of which one is chosen -- focus only changes emphasis, never access."
            )
        ),
        whyItMatters = "The nudge banner always leads no matter what -- a deadline about to be missed " +
            "outranks any dashboard card. Beneath it, the Now card, quick capture and Up Next always stay " +
            "adjacent and in that order relative to each other; only Inventory focus's summary card, and " +
            "where the timeline/todo list pair falls, move around that fixed trio.",
        related = listOf("today-now-card", "today-timeline-kinds"),
        keywords = listOf("focus area", "reorder", "dashboard layout", "settings")
    )
)
