package com.inventoria.app.ui.main.changelog

/** One release worth of user-facing changes, keyed by the versionCode that shipped them. */
data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    val changes: List<String>
)

/**
 * The in-app update log behind the What's New dialog. Prepend an entry here for each release
 * whose changes users should hear about, keyed to that release's versionCode from
 * app/build.gradle.kts -- entries the user's stored last-seen code already covers are filtered
 * out, so a stale entry is never re-shown.
 *
 * Lazy for the same reason HelpCatalog is: this is a pile of strings nothing needs at launch
 * unless the dialog is actually about to show.
 */
object ChangelogCatalog {
    val entries: List<ChangelogEntry> by lazy {
        listOf(
            ChangelogEntry(
                versionCode = 113,
                versionName = "2.32",
                changes = listOf(
                    "Todos due close together on the Schedule day view no longer overlap. When several land within a few minutes of each other, they now cascade downward one at a time instead of stacking their labels illegibly on top of each other."
                )
            ),
            ChangelogEntry(
                versionCode = 112,
                versionName = "2.31",
                changes = listOf(
                    "The Schedule day view now scrolls straight into the next or previous day instead of stopping at midnight -- scroll up or down and the timeline just keeps going. Tapping a day in the week strip or hitting Today jumps you there directly."
                )
            ),
            ChangelogEntry(
                versionCode = 111,
                versionName = "2.30",
                changes = listOf(
                    "A new sub-todo now starts with its parent's kind, type and priority already filled in, instead of blank defaults. Moving an existing todo to a different parent still leaves its own kind/type/priority alone.",
                    "The \"What's interrupting you?\" dialog now defaults the task type to a new \"Interruption\" type instead of leaving it blank -- change it in the dialog if this one was really something else.",
                    "The Schedule screen now marks overdue todos and ones whose time has already passed in red, matching the Todos list -- both the all-day strip and the timeline hairlines."
                )
            ),
            ChangelogEntry(
                versionCode = 110,
                versionName = "2.29",
                changes = listOf(
                    "You can zoom into the time scale. On Task History (flat view) and the Schedule day view, pinch to stretch the hours -- or use the zoom buttons at the bottom left, and tap the percentage to go back to 100%. Zoom in and the gridlines get finer, down to five minutes, so you can see exactly when things started and ended. Your zoom is remembered."
                )
            ),
            ChangelogEntry(
                versionCode = 109,
                versionName = "2.28",
                changes = listOf(
                    "Overlapping tasks on Task History and the Schedule day view are now laid out by the time they actually ran. Cards are exactly as tall as the task was long, so a short task no longer spills into the next one's time. Tasks that ran at the same time sit side by side, and one that ran entirely inside another is tucked into a notch in it. A task too short to fit its name is shown as a thin bar; tap it for details."
                )
            ),
            ChangelogEntry(
                versionCode = 107,
                versionName = "2.26",
                changes = listOf(
                    "Overlapping tasks now cascade instead of splitting the width. On Task History and the Schedule day view, a task that starts while another is still running steps in a little and sits on top of it, so the earlier task's name stays readable and a sub-task looks nested inside its parent."
                )
            ),
            ChangelogEntry(
                versionCode = 106,
                versionName = "2.25",
                changes = listOf(
                    "The \"What's interrupting you?\" dialog has a new \"Not an interruption\" button. If you only paused the task, tap it and the interruption that started timing is discarded, leaving your task paused."
                )
            ),
            ChangelogEntry(
                versionCode = 105,
                versionName = "2.24",
                changes = listOf(
                    "Points now make sense across the app. Tracked time scores the Kind's value per hour, so an hour of a +3 Kind is worth one +3 todo, and the daily ceiling of about 5 is gone: a long focused day now counts for more than a short one. Lifetime uses the same terms as today (tracked time, completed todos and procrastination penalties), and a task that crosses midnight is split between the two days. Productivity Stats has a new Scoring tab that shows every term, for today and for your lifetime."
                )
            ),
            ChangelogEntry(
                versionCode = 104,
                versionName = "2.23",
                changes = listOf(
                    "Task History (flat view) is now a real timeline on a fixed scale: each task sits at its start time and its card is as tall as the task was long. Overlapping tasks sit side by side, and long empty stretches fold away. Tap a card for details, long-press to select."
                )
            ),
            ChangelogEntry(
                versionCode = 103,
                versionName = "2.22",
                changes = listOf(
                    "Todos are no longer grey. Every row is tinted by its Kind's category -- slate for Neutral, blue for Personal, purple for Social -- and so are todo markers on Schedule and Up Next on Today. Priority now shows as a red/orange/green outline on the row."
                )
            ),
            ChangelogEntry(
                versionCode = 102,
                versionName = "2.21",
                changes = listOf(
                    "Starting a sub-todo while its parent todo is being tracked now runs it as a child of the parent's task: the parent pauses, and stopping the sub-todo resumes it. Sub-todos of one parent can run together."
                )
            ),
            ChangelogEntry(
                versionCode = 101,
                versionName = "2.20",
                changes = listOf(
                    "Swipe sideways on the Todos tab to move between the todo list and the Schedule. The Todos / Schedule buttons still work and follow the swipe."
                )
            ),
            ChangelogEntry(
                versionCode = 100,
                versionName = "2.19",
                changes = listOf(
                    "The + button on Task Tracker no longer opens Session Details. It just starts the timer with the name field focused and the keyboard up, so you can type the name straight away."
                )
            ),
            ChangelogEntry(
                versionCode = 99,
                versionName = "2.18",
                changes = listOf(
                    "Todos can repeat. Give a todo a deadline, switch on Repeating, and pick Daily, Weekly or Monthly: it starts over each cycle. The row keeps count of how many cycles ended completed and how many ended missed."
                )
            ),
            ChangelogEntry(
                versionCode = 98,
                versionName = "2.17",
                changes = listOf(
                    "Schedule blocks can now repeat daily as well as weekly. The block dialog's Repeat switch is now a Never / Daily / Weekly choice."
                )
            ),
            ChangelogEntry(
                versionCode = 97,
                versionName = "2.16",
                changes = listOf(
                    "Schedule's timeline is split again: blocks on the left, tracked tasks on the right past a divider line. Tapping a task opens its edit screen on Task Tracker.",
                    "The pencil next to a running task's name now opens that task's own edit screen directly, instead of routing through Session Details every time.",
                    "A collection's icon and colour circle now opens a picker instead of doing nothing when tapped.",
                    "Enable Notifications in Settings now actually silences todo alarms when off, instead of being ignored.",
                    "A task you save to your calendar now really does auto-delete from local history 24 hours later, matching the countdown the app already showed.",
                    "The sync indicator in the top bar now lights up for every kind of sync, not just inventory items.",
                    "The in-app How To manual now covers Todos in full, including the Schedule segment -- tap the ? on the Todos tab.",
                    "The in-app How To manual now covers Today in full too -- tap the ? on the Today tab.",
                    "The in-app How To manual now covers Inventory & Items in full -- tap the ? on the Inventory tab.",
                    "The in-app How To manual now covers Settings too -- all five tabs are fully documented.",
                    "The in-app How To manual now covers Collections & Readiness. The Inventory tab's \"?\" also now follows whichever segment you're on -- Items, Collections and Map each open their own section instead of always landing on Items."
                )
            ),
            ChangelogEntry(
                versionCode = 96,
                versionName = "2.15",
                changes = listOf(
                    "Todos have a description. Unpack the title in the todo dialog; the row shows the first couple of lines.",
                    "Starting a todo now says so: a pop-up confirms tracking has begun, with a button straight to the Task Tracker.",
                    "Flatten a session with choice: tick which segments to merge and keep the rest. All are ticked to begin with, so the old one-tap flatten is still there.",
                    "Schedule blocks can carry a task type. A session started from the Now card lands under that type, so planned hours and tracked hours count as the same activity."
                )
            ),
            ChangelogEntry(
                versionCode = 95,
                versionName = "2.14",
                changes = listOf(
                    "Todos now has a Schedule view: a week strip and a day timeline where you block out what each hour is for. Blocks sit flat on the calendar, your tracked tasks sit on top, and a strip down the edge always shows the plan underneath. Todos due that day show up there too.",
                    "Todo alarms. Give a todo a deadline and it rings -- at the due time, or 10 minutes, an hour or a day before. Done and Snooze right from the notification. Pick Alarm or Notification style in Settings.",
                    "In the todo dialog the time picker is always there. Pick a time first and the date fills in as today.",
                    "New look: a hand taking hold of a clock, and a tagline to match -- take a hold of your life. Task Tracker is now the default focus for new installs.",
                    "Today leads with a Now card: what's running, or what your schedule says this hour is for with a one-tap Start. Then Up Next with countdowns, and today's tracked time broken down by kind.",
                    "A red banner tops Today whenever a todo is overdue, past its time, or due within the hour -- so a deadline is on the home screen before the alarm rings.",
                    "Quick capture on Today: type a thought, hit Enter to make it a todo due today, or the play button to start tracking it on the spot.",
                    "Settings > About has a Version History with every update note."
                )
            ),
            ChangelogEntry(
                versionCode = 94,
                versionName = "2.13",
                changes = listOf(
                    "Choose your focus: Inventory, Tasks, or Todos. Your focus tab moves next to Today and the dashboard leads with it. Change it any time in Settings.",
                    "Todos are now color coded: rows are tinted by priority tier (A red, B orange, C green) and each task type gets its own colored chip.",
                    "This update log now appears once after each update, so you always know what's new."
                )
            )
        )
    }

    /** Entries newer than [versionCode], newest first. */
    fun entriesSince(versionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > versionCode }.sortedByDescending { it.versionCode }
}
