package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.ui.screens.help.model.*

/** Task Tracking guides, part one: getting a timer running and controlling it. */
internal val taskRunningArticles = listOf(

    HelpArticle(
        id = "tasks-start",
        title = "Start tracking a task",
        summary = "Begin timing something with the + button on the Track tab.",
        whatItIs = "A task is a stopwatch with a name. Starting one begins counting up immediately — " +
            "you name it while it runs rather than before, so nothing delays the start.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Open the Track tab and press the + button.",
                        DiagramSpec(listOf(tasksTopBar(), addTaskFab()))
                    ),
                    HelpStep(
                        "The task appears at the top, already running and named \"Task 1\", \"Task 2\" and so on.",
                        DiagramSpec(listOf(runningCard(name = "Task 7", type = null, kind = TaskKind.GRAPHITE)))
                    ),
                    HelpStep("Type over the highlighted name to call it something meaningful.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "You can run up to five tasks at once. At five the + button disappears until you stop one."
            )
        ),
        whyItMatters = "The timer starts before you have decided what to call the thing, because the moment " +
            "you spend naming it is time the task has already been running. The placeholder name and the " +
            "pre-selected text field exist so that naming is an edit, not a prerequisite.",
        related = listOf("tasks-name", "tasks-kind", "tasks-flow-mode"),
        keywords = listOf("stopwatch", "clock in", "begin", "new task", "timer")
    ),

    HelpArticle(
        id = "tasks-name",
        title = "Name a running task",
        summary = "The title on a running card is editable in place.",
        whatItIs = "The bold title on a running task is a text field, not a label. Tap it and type.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the task's title on its card.",
                        DiagramSpec(listOf(runningCard(name = "Task 7", type = null, kind = TaskKind.GRAPHITE, highlight = true)))
                    ),
                    HelpStep("Type the new name. A freshly started task has its name pre-selected, so typing replaces it."),
                    HelpStep("Tap Done on the keyboard, or tap elsewhere. The name saves when the field loses focus.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "Naming a task the same thing you have used before is what lets Inventoria group your sittings " +
                    "together and learn how you usually score it."
            )
        ),
        whyItMatters = "The name is not just a label: it is half of what identifies an activity. Two sittings " +
            "with the same name and the same type are treated as the same activity throughout the app.",
        related = listOf("tasks-autocomplete", "tasks-activity-grouping"),
        keywords = listOf("rename", "title", "edit name", "label")
    ),

    HelpArticle(
        id = "tasks-autocomplete",
        title = "Autocomplete a task name",
        summary = "Suggestions fill in the name, its usual Kind and its type.",
        whatItIs = "Typing into a task's name field offers two kinds of suggestion: your Task Types, and names " +
            "you have used before. They behave differently on purpose.",
        blocks = listOf(
            HelpBlock.Definitions(
                listOf(
                    HelpTerm(
                        "Type suggestions",
                        "Your Task Types, listed first. Picking one stamps the type and keeps the keyboard up, " +
                            "because a type is the broad activity and you are expected to keep typing something specific."
                    ),
                    HelpTerm(
                        "Recent names",
                        "Names you have used before. Picking one fills the whole name, closes the keyboard, and " +
                            "brings that name's usual Kind and type with it."
                    )
                )
            ),
            HelpBlock.Steps(
                listOf(
                    HelpStep("Start typing in the name field of a running task."),
                    HelpStep(
                        "Pick a suggestion from the list that drops down.",
                        DiagramSpec(
                            listOf(
                                DiagramElement.Row(title = "Eating", subtitle = "Task Type", chips = listOf(DiagramChip("Blueberry", DiagramAccent.Kind(TaskKind.BLUEBERRY), leadingDot = true))),
                                DiagramElement.Row(title = "Eating with V", subtitle = "◈ Eating", highlight = true)
                            )
                        )
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "The Kind a name suggests is the one you have used most often for it, not the one you used last. " +
                    "Retagging a single session does not change what the name suggests next time."
            )
        ),
        whyItMatters = "The suggestion carries three things — wording, Kind and type — so that repeating an " +
            "activity takes one tap instead of three. Using the most common Kind rather than the most recent " +
            "means one experiment cannot redefine what a name means.",
        related = listOf("tasks-name", "tasks-type", "tasks-kind"),
        keywords = listOf("autofill", "suggestion", "dropdown", "predictive")
    ),

    HelpArticle(
        id = "tasks-kind",
        title = "Set a task's Kind",
        summary = "The coloured chip is the button — it sets what the task is worth.",
        whatItIs = "A Kind is the point category: what this activity is worth per minute, and whether it counts " +
            "as Personal, Social or Neutral. The coloured chip on a card is itself the picker.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the coloured Kind chip on the task's card.",
                        DiagramSpec(
                            listOf(
                                DiagramElement.Row(
                                    title = "Writing",
                                    meta = "00:18:42",
                                    leadingBar = DiagramAccent.Kind(TaskKind.PEACOCK),
                                    chips = listOf(DiagramChip("Peak Performance", DiagramAccent.Kind(TaskKind.PEACOCK), leadingDot = true, highlight = true))
                                )
                            )
                        )
                    ),
                    HelpStep("Choose a Kind from the list. They are grouped Personal, Social and Neutral, with each one's points shown on the right."),
                )
            ),
            HelpBlock.Bullets(
                listOf(
                    "Positive Kinds (Peacock +3, Lavender +2, Blueberry +1, Basil +1, Sage +2) add points per minute.",
                    "Negative Kinds (Tomato −2, Tangerine −1, Banana −2, Flamingo −1) subtract them.",
                    "Neutral Kinds (Graphite, Grape) score zero and never affect your day."
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Changing the Kind of a finished task recalculates its score. If the task belongs to an activity " +
                    "with several sittings, you will be asked whether to change all of them."
            )
        ),
        whyItMatters = "Points are the Kind's value multiplied by the minutes you spent, so the Kind is the single " +
            "biggest lever on your score. It is attached to the individual task rather than the activity, because " +
            "the same activity can be worth different things on different days.",
        related = listOf("tasks-type", "tasks-scope-prompts", "tasks-momentum"),
        keywords = listOf("point category", "colour", "score", "peacock", "value", "productivity")
    ),

    HelpArticle(
        id = "tasks-type",
        title = "Set a task's Type",
        summary = "The small label above the name groups differently-named tasks together.",
        whatItIs = "A Task Type is the activity one level up from the name. \"Eating with V\" and \"Eating out\" " +
            "are different names, different Kinds — and both are the type \"Eating\".",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the small label above the task's name. It reads \"Set type\" when nothing is set yet.",
                        DiagramSpec(listOf(runningCard(type = "Set type", highlight = true)))
                    ),
                    HelpStep("Pick a type from the list, or choose \"No type\" to clear it.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "You can manage the list itself in Settings → Task Types: add, rename or delete. Renaming a type " +
                    "updates it everywhere, including your history."
            )
        ),
        whyItMatters = "Kinds tell you what something was worth; types tell you what it was. Without them there is " +
            "no way to ask how much time went into eating this week when every meal has a different name and score.",
        related = listOf("tasks-kind", "tasks-activity-grouping"),
        keywords = listOf("category", "activity", "grouping", "task type")
    ),

    HelpArticle(
        id = "tasks-pause-resume",
        title = "Pause and resume",
        summary = "Pausing splits a session into segments without ending it.",
        whatItIs = "Pausing stops the clock but keeps the session open. Resuming starts a new segment under the " +
            "same session, so a morning-and-afternoon stretch of the same work is one session with two parts.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Press the pause button on a running card.",
                        DiagramSpec(listOf(runningCard(highlight = false, trailing = listOf(
                            DiagramIcon(Icons.Default.Pause, highlight = true),

                            DiagramIcon(Icons.Default.Stop)
                        ))))
                    ),
                    HelpStep("The card shows PAUSED and the segment freezes with its own score."),
                    HelpStep("Press play on the same card to start a new segment.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "If interruption tracking is on, pausing also starts a separate task to capture what interrupted you."
            )
        ),
        whyItMatters = "A paused segment is scored the moment it ends rather than waiting for the whole session, " +
            "so work you have already done counts towards today even if the session runs on for hours.",
        related = listOf("tasks-interruptions", "tasks-segments", "tasks-flatten"),
        keywords = listOf("break", "hold", "continue", "segment")
    ),

    HelpArticle(
        id = "tasks-stop",
        title = "Stop a task",
        summary = "Ends the session and freezes its score.",
        whatItIs = "Stopping ends the session for good. Its points are calculated at that moment and stored, " +
            "so later changes to the scoring rules never rewrite your history.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Press the stop button on the running card.",
                        DiagramSpec(listOf(runningCard(trailing = listOf(
                            DiagramIcon(Icons.Default.Pause),
                            DiagramIcon(Icons.Default.Stop, highlight = true)
                        ))))
                    ),
                    HelpStep("The session moves down into Recent Sessions with its final time and score.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "If the task was started from a todo, stopping it asks whether that todo is now finished."
            )
        ),
        whyItMatters = "Scores are frozen at the moment of stopping rather than recomputed on demand. That is why " +
            "editing a finished task's Kind explicitly recalculates it — nothing else would.",
        related = listOf("tasks-pause-resume", "todos-start-task", "tasks-momentum"),
        keywords = listOf("finish", "end", "clock out", "complete")
    ),

    HelpArticle(
        id = "tasks-flow-mode",
        title = "Flow Mode",
        summary = "Automatically starts the next task when you stop one.",
        whatItIs = "With Flow Mode on, stopping a task immediately starts a fresh one, so a day of back-to-back " +
            "work never has untracked gaps between activities.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Turn on the Flow Mode card near the top of the Track tab.",
                        DiagramSpec(listOf(DiagramElement.SettingsRow(
                            title = "Flow Mode",
                            subtitle = "Start the next task automatically",
                            control = SettingsControl.Switch,
                            highlight = true
                        )))
                    ),
                    HelpStep("The Stop button becomes \"Stop & Continue\"."),
                    HelpStep("When you stop, a banner counts down for one second and the next task starts.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Tip,
                "The one-second delay is there so you can catch it. Starting another task manually during the " +
                    "countdown cancels the automatic one."
            )
        ),
        whyItMatters = "Untracked gaps are the main way a day's record drifts from the day you actually had. " +
            "Flow Mode trades a little noise — the occasional stray task — for never losing a stretch of time.",
        related = listOf("tasks-start", "tasks-stop"),
        keywords = listOf("continuous", "auto start", "back to back", "chain")
    ),

    HelpArticle(
        id = "tasks-background-timer",
        title = "Tracking while the app is closed",
        summary = "A running task keeps timing in the background.",
        whatItIs = "While any task is running, Inventoria runs a small background service with a notification. " +
            "The clock keeps time whether the app is open, backgrounded or the screen is off.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "The notification shows while at least one task is running.",
                    "Elapsed time is measured from the start timestamp, so it stays correct even if the service is restarted.",
                    "Stopping the last running task removes the notification."
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Battery optimisation settings on some devices can kill background services. If a long task ever " +
                    "comes back short, check that Inventoria is exempt from battery optimisation."
            )
        ),
        whyItMatters = "Timing from a stored start time rather than a counting loop means the total is right even " +
            "if Android suspends the app mid-session.",
        related = listOf("tasks-start", "tasks-stop"),
        keywords = listOf("background", "notification", "battery", "screen off")
    )
)
