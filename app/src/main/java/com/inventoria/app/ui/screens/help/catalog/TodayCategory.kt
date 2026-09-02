package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import com.inventoria.app.ui.screens.help.model.HelpCategory

/** Today, the app's home screen -- listed first in the index because it's where the app opens.
 * A single article file: unlike Task Tracking and Todos, Today has no natural second axis to
 * split by (it's one screen, not two, and every card on it is already documented top-to-bottom
 * in TodayArticles.kt's own ordering). */
internal val todayCategory = HelpCategory(
    id = "today",
    title = "Today",
    summary = "Your day at a glance: what's due, and the 24-hour timeline",
    icon = Icons.Default.Today,
    articles = todayArticles
)
