package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.screens.help.model.*

/** Todos guides, part one: creating, prioritising, completing and organising the list itself.
 * Alarms and the hand-off to Task Tracker get their own articles below since both are big enough
 * ideas to deserve a title of their own, but they are still "the list" -- the Schedule segment's
 * guides live in ScheduleArticles.kt instead. */
internal val todoListArticles = listOf(

    HelpArticle(
        id = "todos-add",
        title = "Add a todo",
        summary = "The + button on the Todos segment opens a blank todo.",
        whatItIs = "A todo is a one-line title plus everything that makes it actionable: a longer " +
            "description, a Kind, a Task Type, a deadline, a priority and an alarm -- all optional " +
            "except the title.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Open the Todos segment and press the + button.",
                        DiagramSpec(listOf(todosTopBar(), addTodoFab()))
                    ),
                    HelpStep(
                        "Type a title. Everything else in the dialog is optional.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "New Todo",
                            fields = listOf(DiagramField("Title", "Renew passport", FieldKind.Text, highlight = true)),
                            confirmLabel = "Save",
                            dismissLabel = "Cancel"
                        )))
                    ),
                    HelpStep("Press Save. A blank deadline files it under \"No Deadline\" rather than any particular day.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "The description field is where a title's shorthand gets unpacked. The row shows its " +
                    "first couple of lines; the rest is a tap away in the edit dialog."
            )
        ),
        whyItMatters = "Nothing about a todo is mandatory beyond a title on purpose -- jotting something " +
            "down should never be blocked on deciding its priority or deadline first. Every other field can " +
            "be filled in later, including never.",
        related = listOf("todos-priority", "todos-deadline", "todos-sections"),
        keywords = listOf("new todo", "create", "add", "task list", "checklist")
    ),

    HelpArticle(
        id = "todos-priority",
        title = "Set a priority",
        summary = "Franklin-Covey style: a letter tier (A, B, C) and a number rank within it.",
        whatItIs = "A priority is a letter tier -- A most important, C least -- nested with a number " +
            "sub-rank, 1 highest within its own tier. A1 is the single highest priority a todo can " +
            "carry; C3 the lowest. Unprioritized is its own state, not a fourth tier.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Open a todo and pick a priority from the dropdown.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Edit Todo",
                            fields = listOf(DiagramField("Priority", "A1", FieldKind.Dropdown, highlight = true)),
                            confirmLabel = "Save"
                        )))
                    ),
                    HelpStep(
                        "A prioritized row is tinted by its tier -- red for A, orange for B, green for C -- so the list reads by urgency without opening a single row.",
                        DiagramSpec(listOf(
                            todoRow("File taxes", due = null, priority = TodoPriority.A2),
                            todoRow("Water the plants", due = null, priority = TodoPriority.C1)
                        ))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A completed todo drops its tint. The colour tracks what still demands attention, not " +
                    "what once did."
            )
        ),
        whyItMatters = "The nine-tier scale exists so two urgent things are never forced into a false tie: " +
            "A1 and A3 are both \"this week\", but one of them is first. It is also what the procrastination " +
            "penalty (Settings) reads to decide which unfinished todos count against your score -- an " +
            "unprioritized todo always counts, regardless of the configured cutoff tier.",
        related = listOf("todos-add", "todos-sections"),
        keywords = listOf("urgency", "ABC", "tier", "rank", "importance", "colour")
    ),

    HelpArticle(
        id = "todos-deadline",
        title = "Give a todo a deadline",
        summary = "A date, and optionally a time of day -- either can be set first.",
        whatItIs = "A deadline is a date. A time of day on top of it is optional, and the two are picked " +
            "independently: picking a time before a date fills today in for you, since \"5pm\" is a " +
            "perfectly natural thing to mean before you've thought about which day.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the date row to open the date picker, or the time row to open the time picker -- either order works.",
                        DiagramSpec(listOf(
                            DiagramElement.Row(title = "17 Sep 2026", leadingIcon = null, highlight = true),
                            DiagramElement.Row(title = "17:00", leadingIcon = null)
                        ))
                    ),
                    HelpStep("Clearing the date also clears its time -- a time with no day isn't a deadline. Clearing just the time leaves an all-day deadline.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A todo due \"today\" but already past its time still shows Due 17:00 in bold red for the " +
                    "rest of that day -- once the day turns over it becomes Overdue instead."
            )
        ),
        whyItMatters = "The deadline is also the row's filing key: which day section a todo lands under, " +
            "whether it's counted overdue, and what a red Today banner escalates. All of that comes from " +
            "one date field, so setting it is the one action that moves a todo from someday to somewhere on " +
            "the calendar.",
        related = listOf("todos-sections", "todos-alarm"),
        keywords = listOf("due date", "due time", "when", "date picker")
    ),

    HelpArticle(
        id = "todos-complete",
        title = "Complete a todo",
        summary = "Tap the checkbox. Its sub-todos follow along.",
        whatItIs = "A todo's checkbox is tri-state, not a plain tick: empty, a dash, or a check, for " +
            "Incomplete, In Progress and Complete. Tapping it toggles between only two of those -- " +
            "the third is something the app decides for you.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap a todo's checkbox to complete it (or, on an already-complete one, to undo that).",
                        DiagramSpec(listOf(todoRow("Renew passport", due = null, priority = null, gesture = DiagramGesture.Tap, highlight = true)))
                    ),
                    HelpStep("Every currently-incomplete sub-todo underneath it is pushed to In Progress -- not completed outright, just marked as touched.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Undoing a complete todo reverses the cascade too: any sub-todo that was pushed to In " +
                    "Progress by that completion reverts to Incomplete. A sub-todo you completed yourself, " +
                    "separately, is left alone either way."
            )
        ),
        whyItMatters = "In Progress is a weaker signal than Complete on purpose -- it means \"something " +
            "above or below this got touched\", not \"this itself is done\". A parent with a genuine mix " +
            "of finished and unfinished children also displays as In Progress even though nothing wrote " +
            "that to it directly, so the checkbox always tells the truth about the whole branch beneath it.",
        related = listOf("todos-sub-todos", "todos-sections"),
        keywords = listOf("check off", "finish", "tick", "tri-state", "cascade")
    ),

    HelpArticle(
        id = "todos-sub-todos",
        title = "Nest one todo under another",
        summary = "Pick a parent in the dialog, or drag one row onto another.",
        whatItIs = "Any todo can parent any other, to unlimited depth -- GitHub-Projects-style sub-todos. " +
            "There are two ways to set it: the parent picker inside the edit dialog, and dragging a row's " +
            "handle directly onto the row that should become its parent.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "In the dialog, pick a parent from the list -- or from a selected todo's dialog, tap \"Add Sub-Todo\" to open a fresh one already parented to it.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Edit Todo",
                            fields = listOf(DiagramField("Parent Todo", "Plan the trip", FieldKind.Dropdown, highlight = true))
                        )))
                    ),
                    HelpStep(
                        "Or drag the handle on the left of a row and drop it onto another row.",
                        DiagramSpec(listOf(
                            todoRow("Book flights", due = null, priority = null, trailing = dragHandleTrailing(), gesture = DiagramGesture.Drag, highlight = true),
                            todoRow("Plan the trip", due = null, priority = null, highlight = true)
                        ))
                    ),
                    HelpStep("Dropping on empty space, a day header, or the \"No Deadline\" label removes the parent instead.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A todo can't be dropped onto itself or any of its own descendants -- that would loop the " +
                    "tree, so those drop targets simply don't respond."
            )
        ),
        whyItMatters = "Dragging only ever changes the parent pointer -- a child's own deadline, priority " +
            "and Kind are untouched. Because a todo's day section is inherited from its nearest dated " +
            "ancestor, dragging a todo that already has children of its own brings that whole branch along " +
            "in one move, wherever it's dropped.",
        related = listOf("todos-sections", "todos-hide-collapse"),
        keywords = listOf("parent", "nest", "hierarchy", "drag and drop", "subtask", "checklist")
    ),

    HelpArticle(
        id = "todos-sections",
        title = "How todos are grouped by day",
        summary = "Today first (with overdue carried in), then upcoming, then a No Deadline list.",
        whatItIs = "The list is cut into day sections by deadline: Today first, soonest upcoming days " +
            "next, then past days most recent first. Anything with no deadline anywhere in its own " +
            "ancestor chain falls into a separate \"No Deadline\" list at the bottom.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "An incomplete todo whose deadline has passed doesn't get its own stale section -- it's carried into Today instead, and reads \"Overdue by N days\".",
                    "A deadline-less sub-todo inherits its section from the nearest dated ancestor, including following that ancestor into Today if it's overdue.",
                    "A completed sub-todo also defers to its parent's section even if it has its own deadline, so a finished branch doesn't scatter across the list.",
                    "Each day header's \"X% Done\" only counts todos whose own deadline is that day -- carried-over overdue rows and inherited children don't skew it."
                )
            ),
            HelpBlock.Diagram(
                caption = "Today's section can carry rows due today alongside overdue rows carried in from other days, all in one list.",
                spec = DiagramSpec(listOf(
                    todoDayHeader("Today"),
                    todoRow("Submit report", due = "Due 12:00", priority = TodoPriority.A1),
                    todoRow("Pay invoice", due = "Overdue by 3 days", priority = TodoPriority.B2)
                ))
            )
        ),
        whyItMatters = "The same grouping logic runs the Todos screen, the Today tab and the home-screen " +
            "widget, so all three always agree on what \"due today\" means -- there is exactly one " +
            "definition of it in the app, not three that could quietly drift apart.",
        related = listOf("todos-deadline", "todos-hide-collapse"),
        keywords = listOf("overdue", "today", "upcoming", "no deadline", "grouping", "carry over")
    ),

    HelpArticle(
        id = "todos-hide-collapse",
        title = "Hide completed work, or fold a branch away",
        summary = "Two view toggles in the top bar -- neither changes any data.",
        whatItIs = "Hide Completed removes finished todos from view (keeping any that still parent " +
            "unfinished work, so a branch doesn't lose its context). Collapse folds a todo's sub-todos " +
            "under it; the header button folds or unfolds every branch at once.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the eye icon to hide or show completed todos.",
                        DiagramSpec(listOf(todosTopBar(highlight = 1)))
                    ),
                    HelpStep(
                        "Tap the chevron on a row with sub-todos to fold just that branch, or the fold-all icon in the top bar for every branch at once.",
                        DiagramSpec(listOf(todosTopBar(highlight = 0)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A completed parent stays visible while it still has incomplete sub-todos underneath it, " +
                    "even with Hide Completed on -- removing it would strand the unfinished work looking " +
                    "unrelated to anything."
            )
        ),
        whyItMatters = "Both toggles only ever remove rows from view. Every count that depends on the full " +
            "picture -- a day's \"X% Done\", a parent's \"2/3 sub-todos complete\" -- is computed over the " +
            "whole list regardless of what's currently folded or hidden, so those numbers never quietly " +
            "disagree with what you'd see if you unfolded everything.",
        related = listOf("todos-sub-todos", "todos-complete"),
        keywords = listOf("hide completed", "collapse", "expand", "fold", "filter")
    ),

    HelpArticle(
        id = "todos-delete-undo",
        title = "Delete a todo",
        summary = "Immediate, with an Undo snackbar right after.",
        whatItIs = "Deleting a todo removes it straight away and offers a few seconds to undo. Its " +
            "sub-todos are not deleted with it -- with their parent gone from the list, they simply " +
            "surface at their own top level instead.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the trash icon on a row.",
                        DiagramSpec(listOf(todoRow("Old errand", due = null, priority = null, trailing = deleteTrailing(true))))
                    ),
                    HelpStep("Tap Undo on the snackbar if it was a mistake -- it only stays up for a few seconds.")
                )
            )
        ),
        whyItMatters = "A delete is a tombstone kept for a while behind the scenes rather than an instant " +
            "hard delete, which is what makes Undo possible at all -- and it's also why a sub-todo's link " +
            "to a deleted parent is never actually rewritten: only the one row you tapped is touched, so " +
            "undoing the delete puts the whole branch back exactly as it was.",
        related = listOf("todos-sub-todos"),
        keywords = listOf("remove", "trash", "undo", "delete")
    )
)
