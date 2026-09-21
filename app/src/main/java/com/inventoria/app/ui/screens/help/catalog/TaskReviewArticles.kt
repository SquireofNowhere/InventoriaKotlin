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
                "The flat view is drawn to a fixed time scale: each card sits at the clock time the task " +
                    "started and is as tall as the task was long, so a two-hour task is visibly four times a " +
                    "half-hour one. Overlapping tasks cascade -- one that starts while another is still " +
                    "running steps in and sits on top of it -- and a quiet stretch of two hours or " +
                    "more folds into a single \"nothing tracked\" line. Very short tasks are drawn at a " +
                    "minimum height so they stay tappable. Tap a card for its details, long-press to select."
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "The grouped view keeps its list of cards: one card can stand for several sittings, so it " +
                    "has no single position or length to draw."
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
        summary = "Kind value, times hours, times your momentum streak.",
        whatItIs = "A task's points are its Kind's value per hour, multiplied by how long it ran, multiplied by a " +
            "momentum bonus from consecutive sessions of the same Kind. One hour of a +3 Kind is worth +3, " +
            "the same as ticking off one +3 todo.",
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
        related = listOf("tasks-kind", "tasks-scoring", "tasks-stop"),
        keywords = listOf("score", "points", "streak", "multiplier", "momentum", "maths")
    ),

    HelpArticle(
        id = "tasks-scoring",
        title = "How today's score adds up",
        summary = "Tracked hours, todos and penalties, all on one scale.",
        whatItIs = "Your Personal and Social scores are the sum of a few terms: the points from tracked time, the " +
            "value of todos you completed, minus penalties. Today's score and your lifetime score use the same " +
            "terms, so they always agree with each other.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Open Productivity Stats from the Tasks top bar."),
                    HelpStep("Choose the Scoring tab."),
                    HelpStep("Every term is listed for today and for your lifetime, one card per category.")
                )
            ),
            HelpBlock.Bullets(
                listOf(
                    "Tracked time scores the Kind's value per hour, with your momentum bonus. Two hours of a +3 Kind is +6.",
                    "A completed todo adds its Kind's value once, whatever its size.",
                    "A task that crosses midnight is split between the two days by how much of it fell on each.",
                    "Overdue todos cost up to 5 points each per day while they stay late. That charge only applies to today: it can't be replayed for days that are over, so lifetime leaves it out.",
                    "Procrastination penalties follow your current settings, so changing them re-scores your history."
                )
            )
        ),
        whyItMatters = "Tracked time and todos are counted in the same units, so neither one drowns out the other, " +
            "and a long day of focused work is worth more than a short one. Using the same rules for today and " +
            "for your lifetime total means the two numbers can be compared directly.",
        related = listOf("tasks-momentum", "tasks-kind"),
        keywords = listOf("score", "points", "today", "lifetime", "penalty", "overdue", "score too low")
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
        related = listOf("tasks-start", "todos-alarm"),
        keywords = listOf("timer", "alarm", "countdown", "pomodoro", "clock")
    )
)
