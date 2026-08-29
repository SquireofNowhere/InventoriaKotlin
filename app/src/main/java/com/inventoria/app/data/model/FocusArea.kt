package com.inventoria.app.data.model

/**
 * What the user told us they mostly use the app for. Chosen once in the launch prompt (or left at
 * the INVENTORY default), changeable any time from Settings.
 *
 * Two things read it: the nav bar puts the focus tab right after Today, and the Today dashboard
 * leads with a card for the focus area. All tabs stay present regardless -- focus is emphasis,
 * never access.
 *
 * [title] and [description] are shared by the launch prompt and the Settings picker so the two
 * surfaces can't drift apart on wording.
 */
enum class FocusArea(val title: String, val description: String) {
    INVENTORY("Inventory", "Track belongings and collections"),
    TASKS("Task Tracker", "Time and track what you work on"),
    TODOS("Todos", "Plan and check off your day");

    companion object {
        /** Stored-string parse with the same silent fallback the other enum prefs use. */
        fun fromName(name: String): FocusArea =
            try { valueOf(name) } catch (e: IllegalArgumentException) { INVENTORY }
    }
}
