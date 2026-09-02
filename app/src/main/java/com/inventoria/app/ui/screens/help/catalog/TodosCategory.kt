package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import com.inventoria.app.ui.screens.help.model.HelpCategory

/**
 * Todos, assembled from its three article files.
 *
 * Split the same way Task Tracking is -- by what the reader is trying to do -- except here that
 * lines up with the tab's own two segments almost exactly: the todo list itself (creating,
 * prioritising, nesting, organising), alarms and the hand-off to real tracked time (substantial
 * enough ideas to want their own titles even though they're still list features), and the
 * Schedule segment, a different screen with a different vocabulary entirely.
 */
internal val todosCategory = HelpCategory(
    id = "todos",
    title = "Todos",
    summary = "Deadlines, priorities, sub-todos, alarms, and the Schedule timeline",
    icon = Icons.Default.Checklist,
    articles = todoListArticles + todoAlarmsArticles + scheduleArticles
)
