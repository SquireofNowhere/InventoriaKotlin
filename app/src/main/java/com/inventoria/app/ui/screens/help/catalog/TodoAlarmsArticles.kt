package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.ui.screens.help.model.*

/** Todos guides, part two: giving a todo an alarm, and starting real tracked work from one. Split
 * out from TodoListArticles.kt because both ideas are substantial enough to want their own titles,
 * even though they are still todo-list features rather than Schedule ones. */
internal val todoAlarmsArticles = listOf(

    HelpArticle(
        id = "todos-alarm",
        title = "Give a todo an alarm",
        summary = "Ring at the due moment, before it, every so often until it, or on a calendar schedule.",
        whatItIs = "A todo can carry as many reminders as it needs, in three flavours that combine freely. " +
            "Lead times ring once each, that long before the due moment -- \"4, 5 and 6 hours before\". A " +
            "repeat keeps ringing until the deadline -- \"every 2 hours\", \"every day\", \"every week\", " +
            "\"every month\". Both count from the deadline, and an all-day deadline (no time of its own) " +
            "counts from 09:00. The third flavour, a schedule, counts from the calendar instead and needs " +
            "no deadline at all -- see \"Alarms on a schedule\".",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Give the todo a deadline for the first two -- they switch off inside the dialog until one exists."),
                    HelpStep(
                        "Tap the Reminders row (it reads \"Add reminders\" until you have some). Tick any of the quick lead times, or type your own amount and pick minutes, hours, days or weeks. Switch on Keep reminding me for a repeat.",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Edit Todo",
                            fields = listOf(DiagramField("Reminders", "6 hr, 4 hr before · every 2 hours until due", FieldKind.Dropdown, highlight = true))
                        )))
                    ),
                    HelpStep(
                        "An alarm bell shows on the row next to its due time.",
                        DiagramSpec(listOf(todoRow("Take medication", due = "Due 08:00", priority = null, hasAlarm = true)))
                    )
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A new todo with a deadline defaults to ringing right at the due moment -- the whole point " +
                    "of an alarm is the deadline nobody looked at, so it starts on rather than off."
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A repeat is counted back from the due moment and ends with it, so \"every day\" on a 17:00 " +
                    "deadline rings at 17:00 each day, and \"every 2 hours\" rings on the due time's own " +
                    "two-hour beat -- including overnight. The shortest repeat is 5 minutes. Each ring says " +
                    "how long is left."
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "On some phones (Android 12+, or certain manufacturers) the system can silently delay " +
                    "alarms unless exact alarms are allowed. Settings shows an \"Allow Exact Alarms\" row " +
                    "whenever this app doesn't currently have that permission."
            )
        ),
        whyItMatters = "Clearing the deadline clears the reminders that count from it -- one with nothing " +
            "to ring for is never left dangling, waiting for a date that might come back. Schedules are " +
            "kept, because they never needed the date. Only the next reminder is " +
            "ever handed to the system's alarm clock; when it rings, the one after it is lined up. That is " +
            "why a todo that reminds every 2 hours for a month costs one alarm, not hundreds, and why " +
            "anything the phone slept through while it was off is skipped rather than replayed all at once.",
        related = listOf("todos-alarm-schedule", "todos-deadline", "todos-alarm-style", "todos-alarm-actions"),
        keywords = listOf("reminder", "notification", "ring", "lead time", "bell")
    ),

    HelpArticle(
        id = "todos-alarm-schedule",
        title = "Alarms on a schedule",
        summary = "Every day, on chosen weekdays, on a day of the month, or once -- with or without a deadline.",
        whatItIs = "A schedule is a reminder that comes from the calendar rather than from the deadline. " +
            "\"Every day at 08:00\", \"Mondays and Fridays at 08:00\", \"the 15th of every month at 09:00\", " +
            "\"on 3 November at 10:00\". A todo can have several, next to its lead times and repeat, and a " +
            "schedule works on a todo that has no deadline at all.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the Reminders row in the todo's dialog, then Add under \"On a schedule\".",
                        DiagramSpec(listOf(DiagramElement.Popup(
                            title = "Edit Todo",
                            fields = listOf(DiagramField("Reminders", "Monthly on the 15th at 09:00", FieldKind.Dropdown, highlight = true))
                        )))
                    ),
                    HelpStep("Pick Daily, Weekly (and the days), Monthly (and the day of the month) or Once (and the date), then the time."),
                    HelpStep("Switch on Ring again and again to ring several times that day: every so many minutes (at least 5) until a later time. The line under the fields counts the rings, so \"every 5 minutes from 09:00 to 17:00\" says 97."),
                    HelpStep("Tap a schedule in the list to change it, or the cross to remove it.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A month without the day you chose rings on its last day, so \"the 31st\" is also the 30th, " +
                    "the 28th and the 29th when those are the last day."
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "A schedule keeps ringing past the todo's deadline, until you tick the todo off or delete " +
                    "it. A one-off ring is gone once it has rung."
            ),
            HelpBlock.Callout(
                CalloutKind.Caution,
                "Every ring of a todo replaces the last notification for that todo rather than stacking, so " +
                    "a schedule that rings every 5 minutes leaves one notification, not ninety-seven."
            )
        ),
        whyItMatters = "Lead times and repeats answer \"remind me before this is due\". A schedule answers a " +
            "different question -- \"remind me about this on the 15th\", or \"every morning\" -- which has " +
            "nothing to do with a due date, so it does not wait for one. Only the next ring is ever worked " +
            "out and handed to the system's alarm clock, so a schedule that rings 200 times in a day is " +
            "still a single alarm waiting at a time.",
        related = listOf("todos-alarm", "todos-alarm-actions"),
        keywords = listOf("recurring", "repeat", "daily", "weekly", "monthly", "every day", "day of month", "many reminders")
    ),

    HelpArticle(
        id = "todos-alarm-style",
        title = "Alarm or Notification style",
        summary = "Settings → Alarm vs Notification decides how every todo alarm arrives.",
        whatItIs = "One app-wide choice, not per-todo: Alarm style rings loud with vibration and shows on " +
            "the lock screen, hard to miss. Notification style is a normal, quieter notification with the " +
            "default sound.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Open Settings and find the Alarm / Notification segmented control under Notifications.",
                        DiagramSpec(listOf(DiagramElement.ChipRow(listOf(
                            DiagramChip("Alarm", DiagramAccent.Primary, highlight = true),
                            DiagramChip("Notification", DiagramAccent.Neutral)
                        ))))
                    ),
                    HelpStep("Pick the style. It applies to every todo alarm from then on.")
                )
            ),
            HelpBlock.Callout(
                CalloutKind.Note,
                "The Enable Notifications toggle, further up the same screen, is a separate on/off switch " +
                    "that silences todo alarms entirely regardless of which style is chosen."
            )
        ),
        whyItMatters = "The two styles use two different Android notification channels under the hood, " +
            "because a channel's loudness can't be changed once created -- only chosen between. Either " +
            "channel can still be fine-tuned further in the system's own notification settings, and the " +
            "app never recreates or overrides that.",
        related = listOf("todos-alarm", "todos-alarm-actions"),
        keywords = listOf("loud", "quiet", "sound", "vibration", "lock screen", "channel")
    ),

    HelpArticle(
        id = "todos-alarm-actions",
        title = "Done and Snooze from the alarm",
        summary = "Two buttons on the notification itself, no need to open the app.",
        whatItIs = "A todo alarm's notification carries two actions: Done, which completes the todo on the " +
            "spot, and Snooze, which rings again in an hour.",
        blocks = listOf(
            HelpBlock.Bullets(
                listOf(
                    "Done marks the todo complete, exactly like tapping its checkbox -- including the same cascade to its sub-todos.",
                    "Snooze re-arms the alarm one hour later and dismisses the current notification; the todo itself is untouched.",
                    "If the todo was already completed or deleted by the time the alarm fires (from another device, say), it stays silent rather than ringing for something that no longer needs it."
                )
            )
        ),
        whyItMatters = "The alarm re-reads the todo the instant it fires rather than trusting whatever it " +
            "knew when it was scheduled, which is what makes it safe to ignore instead of always having to " +
            "act on it -- a todo finished from another device an hour ago never rings here.",
        related = listOf("todos-alarm", "todos-complete"),
        keywords = listOf("snooze", "done button", "dismiss", "notification actions")
    ),

    HelpArticle(
        id = "todos-start-task",
        title = "Start tracking a todo",
        summary = "The play button turns a todo into a real running timer on Task Tracker.",
        whatItIs = "Tapping a todo's play button starts a tracked task on the Task Tracker tab, seeded " +
            "with the todo's title, Kind and Task Type. The timer itself runs there -- the todo's row just " +
            "changes to show it has one running.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep(
                        "Tap the play icon on a todo's row.",
                        DiagramSpec(listOf(todoRow("Write the proposal", due = null, priority = TodoPriority.A2, trailing = startTrailing())))
                    ),
                    HelpStep(
                        "A pop-up confirms tracking has begun, with a button straight to the Task Tracker.",
                        DiagramSpec(listOf(trackingStartedPopup("Write the proposal")))
                    ),
                    HelpStep(
                        "The row's play button becomes an arrow while a session is running for it -- tap the arrow to jump straight to Task Tracker.",
                        DiagramSpec(listOf(todoRow("Write the proposal", due = null, priority = TodoPriority.A2, trailing = viewOnTasksTrailing())))
                    )
                )
            )
        ),
        whyItMatters = "The play button used to change nothing visible but a small icon, with the timer " +
            "itself running on a different tab entirely -- easy to tap and then wonder if anything " +
            "happened. The pop-up exists purely to say \"yes, that worked\", and offers the tracker for " +
            "anyone who wants to watch it run rather than take it on faith.",
        related = listOf("todos-completion-checkin", "tasks-start"),
        keywords = listOf("play button", "clock in", "begin tracking", "start")
    ),

    HelpArticle(
        id = "todos-completion-checkin",
        title = "\"Is this todo done?\" when you stop tracking",
        summary = "Stopping a task you started from a todo asks whether the todo itself is finished.",
        whatItIs = "When a tracked session that was started from a todo gets stopped, a check-in asks " +
            "whether that todo is now complete or still ongoing -- since finishing a work session and " +
            "finishing the underlying todo aren't always the same moment.",
        blocks = listOf(
            HelpBlock.Steps(
                listOf(
                    HelpStep("Stop the task on Task Tracker as usual."),
                    HelpStep(
                        "Answer the check-in: Complete ticks the todo off (with its usual cascade); Still Ongoing leaves it exactly as it was.",
                        DiagramSpec(listOf(completionCheckInPopup("Write the proposal")))
                    )
                )
            )
        ),
        whyItMatters = "A session ending doesn't always mean the task is finished -- you might have simply " +
            "stopped for the day. The check-in only ever appears for a session that actually started from " +
            "a todo (via its play button), so stopping an unrelated task on the tracker never asks anything.",
        related = listOf("todos-start-task", "tasks-stop"),
        keywords = listOf("finish", "complete", "check in", "prompt")
    )
)
