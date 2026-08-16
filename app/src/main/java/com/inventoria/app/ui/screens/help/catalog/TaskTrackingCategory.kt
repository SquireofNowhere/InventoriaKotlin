package com.inventoria.app.ui.screens.help.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import com.inventoria.app.ui.screens.help.model.HelpCategory

/**
 * Task Tracking, assembled from its three article files.
 *
 * Split by what the reader is trying to do rather than by screen: running a timer, correcting what
 * was recorded, and reviewing it afterwards. Which screen a control lives on is not what someone
 * has in their head when they go looking for help.
 */
internal val taskTrackingCategory = HelpCategory(
    id = "tasks",
    title = "Task Tracking",
    summary = "Timing what you do, and what it's worth",
    icon = Icons.Default.Timer,
    articles = taskRunningArticles + taskOrganisingArticles + taskReviewArticles
)
