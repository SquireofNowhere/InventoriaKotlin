package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/** Task Tracking guides, part three: reviewing what was tracked, and getting it out of the app. */
internal val taskReviewArticles = listOf(

    HelpArticle(
        id = "tasks-bulk-select",
        title = "Select several tasks at once",
        summary = "Press and hold a row to start selecting.",
        whatItIs = "Selection mode lets you act on many tasks together — saving them to your calendar, or " +
            "deleting them in one go.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Press and hold any task or segment row.",
                        DiagramSpec(listOf(DiagramElement.Row(
                            title = "Writing",
                            meta = "1h 12m",
                            leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK),
                            gesture = DiagramGesture.LongPress,
                            highlight = true
                        )))
                    ),
                    HelpStep(
                        "The top bar becomes a count, with save and delete actions.",
                        DiagramSpec(listOf(DiagramElement.TopBar(
                            title = "3 Selected",
                            style = TopBarStyle.Contextual,
                            actions = listOf(DiagramIcon(Icons.Default.Save), DiagramIcon(Icons.Default.Delete))
                        )))
                    ),
                    HelpStep("While selecting, a normal tap toggles selection instead of opening the task.")
                )
            )
        ),
        whyItMatters = "Tap changes meaning inside selection mode, which is why entering it takes a deliberate " +
            "long press rather than a single tap.",
        related = listOf("tasks-delete", "tasks-calendar-save"),
        keywords = listOf("multi select", "long press", "batch", "bulk")
    ),

    HelpArticle(
        id = "tasks-recent-sessions",
        title = "Recent Sessions",
        summary = "The last 24 hours, grouped by activity or laid out by time.",
        whatItIs = "Below your running tasks, Recent Sessions shows the last day's finished work, with a toggle " +
            "between two ways of reading it.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Use the list icon beside the Recent Sessions heading to switch views.",
                        DiagramSpec(listOf(DiagramElement.TopBar(
                            title = "Recent Sessions",
                            style = TopBarStyle.Centered,
                            actions = listOf(DiagramIcon(Icons.AutoMirrored.Filled.List, highlight = true))
                        )))
                    )
                )
            ),
            HelpBlock.Definitions(
                listOf(
                    HelpTerm("Grouped", "One card per activity, with a sittings count when it covers more than one."),
                    HelpTerm("Flat", "Every segment in the order it happened, with a clock time down the left.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Your choice is remembered, and kept separately from the same toggle on Task History."
            )
        ),
        whyItMatters = "The two views answer different questions — what did I spend today on, versus what " +
            "happened and in what order — so neither is a better default than the other.",
        related = listOf("tasks-activity-grouping", "tasks-history"),
        keywords = listOf("today", "recent", "toggle", "list view", "24 hours")
    ),

    HelpArticle(
        id = "tasks-history",
        title = "Task History",
        summary = "Every day as a timeline, with the same grouped and flat views.",
        whatItIs = "The history icon in the Tasks top bar opens your whole record, split into days.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the history icon in the Tasks top bar.",
                        DiagramSpec(listOf(tasksTopBar(highlight = 3)))
                    ),
                    HelpStep("Each day header shows the total tracked and a 24-hour bar with a coloured block per task."),
                    HelpStep("Use the same list icon to switch between grouped and flat.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "In the flat view the clock time on the left is hidden when it would repeat the row above, so a " +
                    "burst of activity in one minute reads as a block rather than the same time over and over."
            )
        ),
        whyItMatters = "The mini timeline is drawn from the individual segments rather than session totals, " +
            "because a session can span midnight and only its parts know which day they happened on.",
        related = listOf("tasks-recent-sessions", "tasks-activity-grouping"),
        keywords = listOf("history", "past", "timeline", "days", "calendar view")
    ),

    HelpArticle(
        id = "tasks-momentum",
        title = "How points are calculated",
        summary = "Kind value, times minutes, times your momentum streak.",
        whatItIs = "A task's score is its Kind's value per minute, multiplied by how long it ran, multiplied by a " +
            "momentum bonus from consecutive sessions of the same Kind.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "Each same-Kind session in a row raises the multiplier — 10% per session for positive Kinds, 15% for draining ones.",
                    "The multiplier is capped at 2.5×.",
                    "Completing a session of a different Kind resets the streak.",
                    "Interruptions are left out of the streak unless you opt them in."
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Scores are frozen when a segment ends. A running task shows a live estimate instead, recalculated " +
                    "every second against your current streak."
            )
        ),
        whyItMatters = "Draining Kinds compound faster than productive ones on purpose: it should take less " +
            "repetition for a bad pattern to show up in your score than for a good one.",
        related = listOf("tasks-kind", "tasks-dampening", "tasks-stop"),
        keywords = listOf("score", "points", "streak", "multiplier", "momentum", "maths")
    ),

    HelpArticle(
        id = "tasks-dampening",
        title = "Why today's score is smaller than the sum",
        summary = "A day's tracked points are squashed toward a ceiling of 5 per category.",
        whatItIs = "Your Personal and Social scores for today are not the raw sum of the day's tasks. Each " +
            "category's total is passed through a curve that approaches 5 without ever reaching it.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Open Productivity Stats from the Tasks top bar."),
                    HelpStep("Choose the Today tab."),
                    HelpStep("The curve is drawn with your own totals marked on it, and every term of the day's arithmetic is listed below.")
                )
            ),
            HelpBlock.Bullets(
                listOf(
                    "Completed todos bypass the curve and add their full value.",
                    "Overdue and procrastination penalties are subtracted afterwards.",
                    "Lifetime totals are the plain historical sum — only a single day is ever dampened."
                )
            )
        ),
        whyItMatters = "Without the curve, one very long session would decide the whole day: a four-hour Peacock " +
            "stretch scores over a thousand raw points, which no amount of anything else could balance. Compressing " +
            "the total keeps effort visible while stopping a single block from drowning out the day.",
        related = listOf("tasks-momentum", "tasks-kind"),
        keywords = listOf("dampening", "curve", "diminishing", "score too low", "ceiling")
    ),

    HelpArticle(
        id = "tasks-calendar-save",
        title = "Save a task to your calendar",
        summary = "Push a finished task into Google Calendar as an event.",
        whatItIs = "Any finished task can be written to your device calendar, coloured to match its Kind.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the calendar icon on a task row, or use the button in its detail dialog.",
                        DiagramSpec(listOf(DiagramElement.Row(
                            title = "Writing",
                            meta = "1h 12m",
                            leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK),
                            trailing = listOf(DiagramIcon(Icons.Default.CalendarToday, highlight = true))
                        )))
                    ),
                    HelpStep("Your calendar app opens with the event pre-filled. Confirm to save it."),
                    HelpStep("Saved tasks show a filled calendar icon.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Select several tasks first with a long press to save them all at once."
            )
        ),
        whyItMatters = "The event carries a description tagging it as an Inventoria task, which is what lets the " +
            "app recognise its own events when reading the calendar back.",
        related = listOf("tasks-calendar-import", "tasks-bulk-select"),
        keywords = listOf("google calendar", "export", "event", "sync calendar")
    ),

    HelpArticle(
        id = "tasks-calendar-import",
        title = "Show calendar events as tasks",
        summary = "Read Inventoria-tagged events back from your device calendar.",
        whatItIs = "The sync icon in the Tasks top bar reads your calendar and shows tagged events alongside your " +
            "tracked tasks.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the sync icon in the Tasks top bar and grant calendar access when asked.",
                        DiagramSpec(listOf(tasksTopBar(highlight = 1)))
                    ),
                    HelpStep("Tagged events appear as task cards marked as coming from your calendar.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Imported events are read-only: their name, Kind, type and duration cannot be edited here. Change " +
                    "them in your calendar app instead."
            )
        ),
        whyItMatters = "A calendar event is owned by the calendar. Making its copy editable here would produce two " +
            "versions of the same thing that could disagree.",
        related = listOf("tasks-calendar-save"),
        keywords = listOf("import", "read calendar", "events", "permission")
    ),

    HelpArticle(
        id = "tasks-timers",
        title = "Timers and alarms",
        summary = "Start a countdown or set an alarm from inside Inventoria.",
        whatItIs = "The alarm icon in the Tasks top bar opens a screen that drives your device's clock app: " +
            "preset and custom timers, an alarm for a todo that has a time, and the next alarm on the device.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the alarm icon in the Tasks top bar.",
                        DiagramSpec(listOf(tasksTopBar(highlight = 0)))
                    ),
                    HelpStep("Pick a preset length, or type your own and press Start."),
                    HelpStep("The timer is labelled with whatever task you are currently tracking.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Android does not let one app read or cancel another's alarms and timers, so editing and stopping " +
                    "them happens in the clock app. The buttons there take you straight to it."
            )
        ),
        whyItMatters = "The timer is the system's, not Inventoria's, so it keeps running and rings normally even " +
            "if this app is closed — but it also knows nothing about your session and will not pause when you do.",
        related = listOf("tasks-start", "todos-deadline-time"),
        keywords = listOf("timer", "alarm", "countdown", "pomodoro", "clock")
    )
)
