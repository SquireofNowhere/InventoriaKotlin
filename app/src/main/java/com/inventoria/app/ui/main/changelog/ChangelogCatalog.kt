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
                versionCode = 94,
                versionName = "2.13",
                changes = listOf(
                    "Choose your focus: Inventory, Tasks, or Todos. Your focus tab moves next to Today and the dashboard leads with it. Change it any time in Settings.",
                    "This update log now appears once after each update, so you always know what's new."
                )
            )
        )
    }

    /** Entries newer than [versionCode], newest first. */
    fun entriesSince(versionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > versionCode }.sortedByDescending { it.versionCode }
}
