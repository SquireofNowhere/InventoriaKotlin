package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/** Task Tracking guides, part two: interruptions, and correcting what was recorded. */
internal val taskOrganisingArticles = listOf(

    HelpArticle(
        id = "tasks-interruptions",
        title = "Track interruptions",
        summary = "Capture what pulled you away, instead of losing it to a pause.",
        whatItIs = "With interruption tracking on, pausing a task immediately starts a second task to record " +
            "whatever interrupted you. The interruption is a real tracked task with its own name, Kind and score.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Pause any running task. The first time you do, Inventoria offers to enable interruption tracking."),
                    HelpStep("Choose Enable. (You can change this later in Settings → Track Interruptions.)"),
                    HelpStep(
                        "A dialog appears asking what's interrupting you — and it is already timing.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "What's interrupting you?",
                            body = "Tracking: 00:00:14",
                            fields = listOf(DiagramField("e.g. Get Water", kind = FieldKind.Text, highlight = true)),
                            confirmLabel = "Save"
                        )))
                    ),
                    HelpStep("Name it and save. Stopping the interruption automatically resumes what you were doing.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Interruptions do not break your momentum streak by default. Turn on \"Count as a streak break\" " +
                    "on the interruption's card if a particular one should."
            )
        ),
        whyItMatters = "The naming dialog appears after the interruption has already started timing, because the " +
            "interruption is happening whether or not you have described it. Time first, label second.",
        related = listOf("tasks-pause-resume", "tasks-nested-interruptions", "tasks-momentum"),
        keywords = listOf("inner task", "distraction", "interrupted", "break")
    ),

    HelpArticle(
        id = "tasks-nested-interruptions",
        title = "Interruptions within interruptions",
        summary = "Chains are shown as an indented tree.",
        whatItIs = "An interruption can itself be interrupted. Each level is indented under the one it interrupted, " +
            "with a caption naming its parent.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "A two-deep interruption chain on the Track tab",
                spec = DiagramSpec(
                    elements = listOf(
                        runningCard(name = "Writing", type = null, kind = TaskKind.PEACOCK, trailing = emptyList()),
                        DiagramElement.Row(title = "Interrupting Writing", subtitle = "Get Water", indent = 1, leadingBar = DiagramAccent.Kind(TaskKind.BLUEBERRY)),
                        DiagramElement.Row(title = "Interrupting Get Water", subtitle = "Phone call", indent = 2, leadingBar = DiagramAccent.Kind(TaskKind.SAGE))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "Stopping something in the middle of a chain stops everything above it too, then resumes the task " +
                    "underneath — you never have to unwind the stack by hand."
            )
        ),
        whyItMatters = "Real interruptions nest, and flattening them would misattribute the time: the phone call " +
            "interrupted getting water, not the writing.",
        related = listOf("tasks-interruptions"),
        keywords = listOf("nested", "chain", "tree", "stack")
    ),

    HelpArticle(
        id = "tasks-segments",
        title = "See a session's segments",
        summary = "Expand a card to see each stretch of work separately.",
        whatItIs = "A session paused and resumed several times is made of segments. The card shows the total; " +
            "expanding it shows the parts.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Tap the chevron at the right of a session card."),
                    HelpStep(
                        "Each segment is listed with its own duration, percentage of the day and score.",
                        DiagramSpec(listOf(
                            completedCard(name = "Writing", meta = "1h 12m • 5.0% of Today"),
                            DiagramElement.Row(title = "Writing", meta = "42m • +126 pts", indent = 1, leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK)),
                            DiagramElement.Row(title = "Writing", meta = "30m • +90 pts", indent = 1, leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK))
                        ))
                    )
                )
            )
        ),
        whyItMatters = "Each segment is scored when it ends, so the session total is the sum of parts scored at " +
            "different moments — which is why segments can carry different Kinds and different values.",
        related = listOf("tasks-pause-resume", "tasks-flatten", "tasks-split"),
        keywords = listOf("parts", "expand", "breakdown", "chevron")
    ),

    HelpArticle(
        id = "tasks-session-detail",
        title = "Edit a whole session",
        summary = "Change the name, Kind or type of every segment at once.",
        whatItIs = "The session detail dialog edits the session as a unit — useful when you named it wrong or " +
            "picked the wrong Kind from the start.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Tap a completed session card, or the ⋮ button on a running one."),
                    HelpStep(
                        "Edit the session name, its Kind, or its Type. Each applies to every segment.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Session Details",
                            fields = listOf(
                                DiagramField("Session Name", "Writing", FieldKind.Text),
                                DiagramField("Session Category", "Peak Performance", FieldKind.Dropdown),
                                DiagramField("Session Type", "Work", FieldKind.Dropdown)
                            ),
                            confirmLabel = "Close"
                        )))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "If this session is one of several sittings of the same activity, you will be asked whether the " +
                    "change applies to all of them."
            )
        ),
        whyItMatters = "Editing a Kind here rescores every finished segment under the new value, rather than " +
            "leaving stored scores that no longer match the Kind beside them.",
        related = listOf("tasks-scope-prompts", "tasks-task-detail", "tasks-flatten"),
        keywords = listOf("session", "edit", "rename", "dialog")
    ),

    HelpArticle(
        id = "tasks-task-detail",
        title = "Edit a single segment",
        summary = "Change one part's name, Kind, type, duration or times.",
        whatItIs = "Tapping an individual task or segment row opens its own dialog. Edits here affect only that " +
            "one piece unless you say otherwise.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Tap a single task row, or a segment inside an expanded session."),
                    HelpStep("Edit the name, Kind or Type at the top."),
                    HelpStep(
                        "Adjust the length with the Days / Hrs / Min / Sec boxes, or tap Started or Stopped to pick exact times.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Task Details",
                            fields = listOf(
                                DiagramField("Days / Hrs / Min / Sec", "0 / 1 / 12 / 00", FieldKind.Stepper, highlight = true),
                                DiagramField("Started", "14:05:00", FieldKind.Text),
                                DiagramField("Stopped", "15:17:00", FieldKind.Text)
                            ),
                            confirmLabel = "Save"
                        )))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Duration cannot be edited while the task is running, and tasks imported from your calendar are read-only."
            )
        ),
        whyItMatters = "Changing a time or a Kind recomputes that segment's frozen score, so a corrected record " +
            "and its points never disagree.",
        related = listOf("tasks-split", "tasks-session-detail", "tasks-momentum"),
        keywords = listOf("duration", "fix", "adjust", "wrong time")
    ),

    HelpArticle(
        id = "tasks-split",
        title = "Split a segment in two",
        summary = "Cut one stretch into two tasks at a chosen moment.",
        whatItIs = "Splitting is for when one tracked block was really two things — you fell asleep with a task " +
            "running, or switched activities without stopping.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Open the segment's detail dialog and choose \"Split This Segment\"."),
                    HelpStep("Set the point to cut at. A two-colour bar shows the proportions."),
                    HelpStep("Name the second half and give it a Kind. The first half keeps the original name.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "On a task that is still running, the split offset ticks along with the clock until you type in " +
                    "any field — then it holds still so your number does not move under you."
            )
        ),
        whyItMatters = "Splitting produces two independently scored tasks rather than one averaged one, so the " +
            "sleep and the work either side of it are valued separately.",
        related = listOf("tasks-task-detail", "tasks-flatten"),
        keywords = listOf("divide", "cut", "separate", "fell asleep")
    ),

    HelpArticle(
        id = "tasks-flatten",
        title = "Flatten a session",
        summary = "Merge some or all of a session's segments into one continuous block.",
        whatItIs = "Flattening collapses chosen segments of a session into a single stretch, discarding the pauses between them.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Open the session detail dialog."),
                    HelpStep("Choose \"Flatten segments…\" at the bottom."),
                    HelpStep("Every segment starts ticked. Untick any you want to keep separate — a break that was real, say."),
                    HelpStep("Tap Flatten. The action cannot be undone.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "Only segments that follow each other can be merged: ticking the first and third but not the second is refused, because the result would overlap the one you skipped."
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "This is irreversible, and the option only appears on sessions with more than one segment."
            )
        ),
        whyItMatters = "Some sessions are fragmented by pauses that did not mean anything — a phone check, a " +
            "misfire. Flattening exists so the record can be tidied to match what actually happened.",
        related = listOf("tasks-segments", "tasks-split"),
        keywords = listOf("merge", "combine", "collapse", "join")
    ),

    HelpArticle(
        id = "tasks-activity-grouping",
        title = "Activities and sittings",
        summary = "Sessions with the same name and type share one card.",
        whatItIs = "An activity is everything with the same name and the same Task Type. Each separate stretch of " +
            "it is a sitting. In the grouped view, all the sittings of one activity share a card.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "One card standing for three sittings of the same activity",
                spec = DiagramSpec(
                    elements = listOf(completedCard(name = "Eating with V", sittings = 3, kind = TaskKind.BLUEBERRY, meta = "2h 05m")),
                    callouts = listOf("The sittings line only appears when a card covers more than one.")
                )
            ),
            HelpBlock.Bullets(
                listOf(
                    "The Kind is deliberately not part of the identity — lunch at home and lunch out are one activity scored differently.",
                    "Renaming a single sitting splits it out of the group, because the name is half the identity.",
                    "Each sitting stays a separate session underneath: your streaks and totals still count them individually."
                )
            )
        ),
        whyItMatters = "Grouping happens when the list is drawn rather than in the stored data, so the card can " +
            "show you an activity without the app losing track of the individual sittings that make it up.",
        related = listOf("tasks-scope-prompts", "tasks-recent-sessions", "tasks-type"),
        keywords = listOf("grouping", "sittings", "same name", "merge", "activity")
    ),

    HelpArticle(
        id = "tasks-scope-prompts",
        title = "Change all, or just this one",
        summary = "Edits that could reach several sittings ask first.",
        whatItIs = "When you rename, retag or delete something that stands for more than one sitting, Inventoria " +
            "asks how far the change should reach.",
        blocks = listOf(
            HelpBlock.Diagram(
                caption = "The scope prompt, shown when an edit could reach other sittings",
                spec = DiagramSpec(listOf(scopePrompt()))
            ),
            HelpBlock.Definitions(
                listOf(
                    HelpTerm("Change all", "Applies to every sitting of that activity, across every day."),
                    HelpTerm("Just this one", "Applies only to the session or segment you opened."),
                    HelpTerm("Cancel", "Nothing changes.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "You will not be asked when there is nothing to disambiguate — an activity with a single sitting, " +
                    "or a task that is still running, just applies the change."
            )
        ),
        whyItMatters = "Renaming is the case that most needs the choice: the name identifies the activity, so " +
            "renaming one sitting is exactly how you split it out — which is a reasonable thing to want, but only " +
            "on purpose.",
        related = listOf("tasks-activity-grouping", "tasks-delete"),
        keywords = listOf("scope", "prompt", "all sittings", "confirm")
    ),

    HelpArticle(
        id = "tasks-delete",
        title = "Delete tasks and sessions",
        summary = "Remove a segment, a session, or every sitting of an activity.",
        whatItIs = "Deletion is available at every level, from a single segment up to an entire activity.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Use the bin icon on a row, card or dialog.",
                        DiagramSpec(
                            listOf(
                                DiagramElement.Row(
                                    title = "Writing",
                                    meta = "1h 12m",
                                    leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK),
                                    trailing = listOf(DiagramIcon(Icons.Default.Delete, highlight = true))
                                )
                            )
                        )
                    ),
                    HelpStep("If the card covers several sittings, choose whether to delete all of them or only the most recent.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Deleting a session removes every segment inside it. Deletions sync to your other devices."
            )
        ),
        whyItMatters = "Deleted records are marked rather than erased immediately, so the deletion itself syncs " +
            "and does not come back from another device that had not heard about it.",
        related = listOf("tasks-scope-prompts", "tasks-bulk-select"),
        keywords = listOf("remove", "bin", "trash", "erase")
    )
)
