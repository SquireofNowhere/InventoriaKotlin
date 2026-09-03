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
