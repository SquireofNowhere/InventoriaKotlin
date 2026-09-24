package com.inventoria.shared.model

/**
 * What the user mostly uses the app for. Focus is emphasis, never access: it only decides which
 * tab sits right after Today and which card Today leads with.
 */
enum class FocusArea(val title: String, val description: String) {
    TASKS("Task Tracker", "Time and track what you work on"),
    TODOS("Todos", "Plan and check off your day"),
    INVENTORY("Inventory", "Track belongings and collections");

    companion object {
        val DEFAULT = TASKS

        fun fromName(name: String): FocusArea = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
