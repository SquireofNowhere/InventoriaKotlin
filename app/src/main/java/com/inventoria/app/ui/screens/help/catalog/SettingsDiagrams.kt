package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import com.inventoria.app.ui.screens.help.model.*

/** Diagram building blocks for the Settings guides. Almost every row on this screen is one of two
 * shapes -- a toggle or a row that opens something else -- so each helper here is just a
 * SettingsRow with that section's own icon, title and subtitle pinned down. */

internal fun darkModeRow(highlight: Boolean = true) = DiagramElement.SettingsRow(
    title = "Dark Mode",
    subtitle = "Use dark theme across the app",
    icon = Icons.Default.Brightness4,
    control = SettingsControl.Switch,
    highlight = highlight
)

internal fun focusRow() = DiagramElement.SettingsRow(
    title = "App Focus",
    subtitle = "Task Tracker -- sits next to Today and leads the dashboard",
    icon = Icons.Default.CenterFocusStrong,
    control = SettingsControl.Chevron,
    highlight = true
)

/** The three-way focus choice, the same dialog the launch prompt and this Settings row both open. */
internal fun focusChoicePopup(selected: String = "Task Tracker") = DiagramElement.Popup(
    title = "App Focus",
    fields = listOf(
        DiagramField("Task Tracker", "Time and track what you work on", highlight = selected == "Task Tracker"),
        DiagramField("Todos", "Plan and check off your day", highlight = selected == "Todos"),
        DiagramField("Inventory", "Track belongings and collections", highlight = selected == "Inventory")
    )
)

internal fun currencyToggleRow() = DiagramElement.SettingsRow(
    title = "Auto Currency",
    subtitle = "Pick currency based on location",
    icon = Icons.Default.Language,
    control = SettingsControl.Switch,
    highlight = true
)

internal fun currencyPickerField(code: String = "USD") = DiagramElement.Row(
    title = "Selected Currency",
    meta = code,
    trailing = listOf(DiagramIcon(Icons.Default.ArrowDropDown)),
    highlight = true
)

internal fun showValueRow(highlight: Boolean = true) = DiagramElement.SettingsRow(
    title = "Show Total Value",
    subtitle = "Display total inventory value in the Inventory tab and the Today summary card",
    icon = Icons.Default.AccountBalanceWallet,
    control = SettingsControl.Switch,
    highlight = highlight
)

internal fun taskTypesRow() = DiagramElement.SettingsRow(
    title = "Task Types",
    subtitle = "Group tasks by activity -- \"Eating with V\" and \"Eating out\" can share the type \"Eating\"",
    icon = Icons.Default.Category,
    control = SettingsControl.Chevron
)

internal fun interruptionsRow(highlight: Boolean = true) = DiagramElement.SettingsRow(
    title = "Track Interruptions (Inner Tasks)",
    subtitle = "When pausing a task, start a linked inner task",
    icon = Icons.Default.NotificationImportant,
    control = SettingsControl.Switch,
    highlight = highlight
)

internal fun procrastinationTodoRow(enabled: Boolean = true) = DiagramElement.SettingsRow(
    title = "Penalize Non-Priority Todos",
    subtitle = "Subtract points when you complete a Todo at or below this cutoff, or with none set",
    icon = Icons.Default.Flag,
    control = SettingsControl.Switch,
    highlight = enabled
)

internal fun procrastinationTaskRow(enabled: Boolean = true) = DiagramElement.SettingsRow(
    title = "Penalize Procrastination Task Kinds",
    subtitle = "Subtract points whenever a tracked task of a flagged Kind is completed",
    icon = Icons.Default.Warning,
    control = SettingsControl.Switch,
    highlight = enabled
)

internal fun penaltyAmountField(points: String = "2") = DiagramField("Penalty Points", points, FieldKind.Text, highlight = true)

internal fun enableNotificationsRow(highlight: Boolean = true) = DiagramElement.SettingsRow(
    title = "Enable Notifications",
    subtitle = "Ring todo alarms. Off silences them even if the system allows notifications.",
    icon = Icons.Default.Notifications,
    control = SettingsControl.Switch,
    highlight = highlight
)

internal fun helpIndexRow() = DiagramElement.SettingsRow(
    title = "How To",
    subtitle = "Step-by-step guides for every feature, with diagrams",
    icon = Icons.Default.HelpOutline,
    control = SettingsControl.Chevron
)

internal fun versionHistoryRow() = DiagramElement.SettingsRow(
    title = "Version History",
    subtitle = "What changed in each update, from 2.13 on",
    icon = Icons.Default.History,
    control = SettingsControl.Chevron
)
