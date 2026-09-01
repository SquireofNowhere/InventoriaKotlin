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
                versionCode = 95,
                versionName = "2.14",
                changes = listOf(
                    "Todos now has a Schedule view: a week strip and a day timeline where you block out what each hour is for, next to what your tracked tasks actually used it for. Todos due that day show up there too.",
                    "Todo alarms. Give a todo a deadline and it rings -- at the due time, or 10 minutes, an hour or a day before. Done and Snooze right from the notification. Pick Alarm or Notification style in Settings.",
                    "In the todo dialog the time picker is always there. Pick a time first and the date fills in as today.",
                    "New icon: a clock whose hands make a check mark. The app now opens on the Task Tracker, and Task Tracker is the default focus for new installs.",
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
