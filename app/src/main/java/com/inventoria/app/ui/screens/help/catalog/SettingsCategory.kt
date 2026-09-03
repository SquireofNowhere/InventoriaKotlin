package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.inventoria.app.ui.screens.help.model.HelpCategory

/**
 * Settings, one article file since the screen itself is one flat scroll rather than segments.
 *
 * Two sections on the real screen deliberately get no deep coverage here: Task Types (a pointer
 * article only -- the manager is its own screen and its own not-yet-written category) and
 * Account & Sync (skipped entirely -- Google sign-in, invite codes and connected devices are
 * substantial enough to be their own not-yet-written category, Sync & Accounts, rather than
 * folded into general Settings just because the rows happen to live on this screen).
 */
internal val settingsCategory = HelpCategory(
    id = "settings",
    title = "Settings",
    summary = "Appearance, currency, notifications and penalties",
    icon = Icons.Default.Settings,
    articles = settingsArticles
)
