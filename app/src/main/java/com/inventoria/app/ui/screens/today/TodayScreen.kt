package com.inventoria.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.model.ScheduleBlock
import com.inventoria.app.data.model.Task
import com.inventoria.app.ui.components.InventoriaTopBar
import com.inventoria.app.ui.components.KindBreakdownDonut
import com.inventoria.app.ui.components.LinearProductivityChart
import com.inventoria.app.ui.main.Screen
import com.inventoria.app.ui.screens.task.TaskKindChip
import com.inventoria.app.ui.screens.task.TaskTypeLabel
import com.inventoria.app.ui.screens.task.taskTypeColor
import com.inventoria.app.ui.screens.task.todoPriorityTierColor
import com.inventoria.app.ui.screens.todo.TodoDayHeader
import com.inventoria.app.ui.screens.todo.TodoRow
import com.inventoria.app.ui.screens.todo.TodoViewModel
import com.inventoria.app.util.currentMinuteOfDay
import com.inventoria.app.util.formatMinuteOfDay
import com.inventoria.app.util.getDayLabel
import com.inventoria.app.util.getStartOfDay
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/**
 * The app's home: what's on today, and how the day has actually gone so far.
 *
 * Todo rows here are the same TodoRow the Todos screen draws, minus the drag handle and delete
 * button -- Today can check things off and start tracking them, but reordering/parenting/deleting
 * belong to the screen that owns the list.
 *
 * Row taps go to Todos rather than opening the edit dialog: this screen's TodoViewModel is a
 * different instance from that screen's (hiltViewModel resolves against the nav entry), so
 * startEditingTodo would set pendingEditTodo on an instance nothing renders a dialog for. Same
 * reason Today doesn't use tap-to-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    todayViewModel: TodayViewModel,
    todoViewModel: TodoViewModel,
    onNavigateToHelp: () -> Unit,
    onNavigateToTodos: () -> Unit,
    onNavigateToTasks: () -> Unit
) {
    val tasks by todayViewModel.tasks.collectAsState()
    val nowState by todayViewModel.nowState.collectAsState()
    val upNext by todayViewModel.upNext.collectAsState()
    val nudge by todayViewModel.nudge.collectAsState()
    val focusArea by todayViewModel.focusArea.collectAsState()
    val todoSections by todoViewModel.todoSections.collectAsState()
    val taskTypeNames by todoViewModel.taskTypeNamesById.collectAsState()
    val todoIdsWithActiveSession by todoViewModel.todoIdsWithActiveSession.collectAsState()

    val todayStart = remember { getStartOfDay(System.currentTimeMillis()) }
    // Overdue-and-incomplete todos (and any deadline-less children trailing them) already resolve
    // into the today section upstream, in TodoViewModel.effectiveSectionDay -- so "today's list"
    // really is just the section keyed to today, with no extra filtering here.
    val todaySection = todoSections.firstOrNull { it.dayStart == todayStart }

    // Wall-clock minute, re-read once a minute, so a due time flips to its "past due" styling on
    // its own instead of waiting for some unrelated recomposition. Same ticker TodoScreen runs.
    val nowMinuteOfDay by produceState(currentMinuteOfDay()) {
        while (true) {
            delay(60_000)
            value = currentMinuteOfDay()
        }
    }


    Scaffold(
        topBar = {
            InventoriaTopBar(
                // From the tab definition, not a literal -- the nav label and the bar showing two
                // different names for one screen is exactly what this pass was fixing.
                title = Screen.Today.title,
                onNavigateToHelp = onNavigateToHelp,
                // The overflow that used to sit here held exactly two entries, Settings and
                // How To. Settings is a tab again and How To is the "?" the bar now draws on
                // every screen, so the menu had nothing left in it.
                actions = {
                    IconButton(onClick = { todayViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        // The two existing sections plus the inventory card, as reorderable blocks. Keys are the
        // same in every arrangement ("inventory_summary", "today_header", todo ids, "timeline"),
        // so changing focus is a reorder to LazyColumn, not a teardown.
        fun LazyListScope.todoListItems() {
            if (todaySection == null) {
                item { NothingDueToday(onNavigateToTodos) }
            } else {
                item(key = "today_header") {
                    TodoDayHeader(
                        todaySection.dayStart,
                        todaySection.totalDueCount,
                        todaySection.completedDueCount
                    )
                }
                items(todaySection.visibleTodos, key = { it.todo.id }) { entry ->
                    TodoRow(
                        entry = entry,
                        todayStart = todayStart,
                        nowMinuteOfDay = nowMinuteOfDay,
                        taskTypeNames = taskTypeNames,
                        hasActiveSession = entry.todo.id in todoIdsWithActiveSession,
                        onToggleCompleted = { todoViewModel.toggleComplete(entry.todo) },
                        // Editing lives on the Todos screen -- see the class KDoc.
                        onClick = onNavigateToTodos,
                        onStart = { todoViewModel.startTaskFromTodo(entry.todo) },
                        onViewTask = onNavigateToTasks,
                        showDragHandle = false,
                        showDelete = false,
                        // Folding is Todos-screen view state and this screen reads the unfolded
                        // sections, so a chevron here would be a control with nothing behind it.
                        showCollapseToggle = false
                    )
                }
            }
        }

        // The extra Spacer only when the timeline trails the todo list, preserving the wider gap
        // it has always had there; as the lead card the arrangement spacing is enough.
        fun LazyListScope.timelineItem(afterTodos: Boolean) {
            item(key = "timeline") {
                if (afterTodos) Spacer(Modifier.height(8.dp))
                TodayTimelineCard(tasks)
            }
        }

        // What is happening right now -- always near the top, whatever the focus, because it is
        // the one thing on this screen that changes minute to minute.
        fun LazyListScope.nowItem() {
            item(key = "now") {
                NowCard(
                    state = nowState,
                    taskTypeNames = taskTypeNames,
                    onStartBlock = { todayViewModel.startTaskFromBlock(it) },
                    onOpenTracker = onNavigateToTasks,
                    onOpenSchedule = onNavigateToTodos
                )
            }
        }

        fun LazyListScope.kindsItem() {
            item(key = "kinds") { KindBreakdownCard(tasks) }
        }

        // One field to get a thought down without leaving the home screen: a todo due today, or
        // a session started on the spot.
        fun LazyListScope.quickCaptureItem() {
            item(key = "quick_capture") {
                QuickCaptureCard(
                    onAddTodo = { todayViewModel.addQuickTodo(it) },
                    onStartTask = { todayViewModel.startQuickTask(it) }
                )
            }
        }

        // Only present when there is something to show; an empty "Up next" would just repeat what
        // the Idle Now card already says.
        fun LazyListScope.upNextItem() {
            if (upNext.isEmpty()) return
            item(key = "up_next") {
                UpNextCard(
                    items = upNext,
                    nowMinuteOfDay = nowMinuteOfDay,
                    onOpenSchedule = onNavigateToTodos,
                    onOpenTodos = onNavigateToTodos
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Above everything, whatever the focus: a deadline about to be missed outranks any
            // dashboard card.
            nudge?.let { n ->
                item(key = "nudge") { NudgeBanner(n, nowMinuteOfDay, onClick = onNavigateToTodos) }
            }
            when (focusArea) {
                FocusArea.INVENTORY -> {
                    item(key = "inventory_summary") {
                        // Collected here rather than at the screen root so the COUNT/SUM queries
                        // only run while an Inventory-focused dashboard is actually showing.
                        val totalValue by todayViewModel.totalValue.collectAsState()
                        val showValue by todayViewModel.showTotalValue.collectAsState()
                        val itemCount by todayViewModel.totalItems.collectAsState()
                        val collectionCount by todayViewModel.collectionCount.collectAsState()
                        InventoryFocusCard(totalValue, showValue, itemCount, collectionCount)
                    }
                    nowItem()
                    quickCaptureItem()
                    upNextItem()
                    todoListItems()
                    timelineItem(afterTodos = true)
                    kindsItem()
                }
                FocusArea.TASKS -> {
                    nowItem()
                    quickCaptureItem()
                    upNextItem()
                    timelineItem(afterTodos = false)
                    kindsItem()
                    todoListItems()
                }
                FocusArea.TODOS -> {
                    nowItem()
                    quickCaptureItem()
                    upNextItem()
                    todoListItems()
                    timelineItem(afterTodos = true)
                    kindsItem()
                }
            }
        }
    }
}

/**
 * The 24-hour timeline, on the gradient the old dashboard header used.
 *
 * The gradient isn't decoration: LinearProductivityChart draws its track, elapsed shading, now-line
 * and hour labels in white/black alphas, which need a saturated backdrop to read at all. Don't drop
 * this onto a plain surface without parameterising the chart's colours first.
 */
@Composable
private fun TodayTimelineCard(tasks: List<Task>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    getDayLabel(getStartOfDay(System.currentTimeMillis())),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "How your day has gone so far.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                LinearProductivityChart(
                    tasks = tasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }
    }
}

/**
 * The Inventory-focus lead card: the same gradient treatment as [TodayTimelineCard] so the
 * dashboard's headline card reads the same whichever focus is on top. Value honours the
 * "Show Total Value" setting exactly like the Inventory hub's stat card; with it off the
 * counts step up into the headline slot.
 */
@Composable
private fun InventoryFocusCard(
    totalValue: Double,
    showValue: Boolean,
    itemCount: Int,
    collectionCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                val counts = "$itemCount ${if (itemCount == 1) "item" else "items"} · " +
                    "$collectionCount ${if (collectionCount == 1) "collection" else "collections"}"
                if (showValue) {
                    Text(
                        NumberFormat.getCurrencyInstance().format(totalValue),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        counts,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        counts,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your inventory at a glance.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * The Now card: what is running, what is paused, what the schedule says this hour is for, or
 * nothing -- see [NowState]. It can start a session (a plain insert, like a todo's Start button)
 * but never pauses or stops one; those run the tracker's interruption/flow-mode/check-in logic,
 * so a running or paused session taps through to the tracker instead.
 *
 * "Schedule" lands on the Todos hub rather than its Schedule segment: the segment is local
 * rememberSaveable state, not a route, so there is nothing to deep-link to. One extra tap.
 */
@Composable
private fun NowCard(
    state: NowState,
    taskTypeNames: Map<String, String>,
    onStartBlock: (ScheduleBlock) -> Unit,
    onOpenTracker: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    val tapsThrough = state is NowState.Running || state is NowState.Paused
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tapsThrough) Modifier.clickable(onClick = onOpenTracker) else Modifier),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                is NowState.Running -> {
                    NowOverline("Now", "Pause, stop and details on the Task Tracker", onOpenTracker)
                    Spacer(Modifier.height(8.dp))
                    state.sessions.forEach { task -> LiveSessionRow(task, running = true) }
                }
                is NowState.Paused -> {
                    NowOverline("Paused", "Resume or stop on the Task Tracker", onOpenTracker)
                    Spacer(Modifier.height(8.dp))
                    state.sessions.forEach { task -> LiveSessionRow(task, running = false) }
                }
                is NowState.Planned -> {
                    val block = state.block
                    NowOverline(
                        "Now · ${formatMinuteOfDay(block.startMinuteOfDay)} – ${formatMinuteOfDay(block.endMinuteOfDay)}",
                        "What your schedule set this hour aside for",
                        null
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(block.kind.colorValue))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            block.taskTypeId?.let { typeId ->
                                taskTypeNames[typeId]?.let { TaskTypeLabel(it, color = taskTypeColor(typeId)) }
                            }
                            Text(
                                block.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (block.notes.isNotBlank()) {
                                Text(
                                    block.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        TaskKindChip(kind = block.kind, modifier = Modifier.scale(0.85f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onStartBlock(block) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start this")
                        }
                        TextButton(onClick = onOpenSchedule) {
                            Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Schedule")
                        }
                    }
                }
                is NowState.Idle -> {
                    NowOverline("Now", null, null)
                    Spacer(Modifier.height(4.dp))
                    Text("Nothing running.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val next = state.nextBlock
                    Text(
                        if (next != null) "Next: ${next.title} at ${formatMinuteOfDay(next.startMinuteOfDay)}"
                        else "Nothing planned for the rest of today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onOpenTracker) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open tracker")
                        }
                        TextButton(onClick = onOpenSchedule) {
                            Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Plan the day")
                        }
                    }
                }
            }
        }
    }
}

/** Small caps-style header line for the Now card, with an optional trailing arrow when the card
 * as a whole leads somewhere. */
@Composable
private fun NowOverline(title: String, subtitle: String?, onArrow: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onArrow != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open the Task Tracker",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** One running or paused segment: kind bar, name, chip, and a live elapsed clock. Elapsed is the
 * same sum TaskTimerService's notification shows -- the segment's stored duration plus the time
 * since its start -- re-read every second while running, frozen while paused. */
@Composable
private fun LiveSessionRow(task: Task, running: Boolean) {
    val now by produceState(System.currentTimeMillis(), running) {
        while (running) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val elapsedMs = if (running) task.duration + (now - task.startTime) else task.duration
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(task.kind.colorValue).copy(alpha = if (running) 1f else 0.5f))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (running) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formatElapsed(elapsedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TaskKindChip(kind = task.kind, modifier = Modifier.scale(0.85f))
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

/**
 * Quick capture. Type, then either add it as a todo due today (Enter, or the plus) or start
 * tracking it this second (the play button). The field clears itself; the result shows up in the
 * list below or in the Now card above, which is the confirmation.
 */
@Composable
private fun QuickCaptureCard(onAddTodo: (String) -> Unit, onStartTask: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val canSubmit = text.isNotBlank()

    fun submit(action: (String) -> Unit) {
        if (!canSubmit) return
        action(text)
        text = ""
        focusManager.clearFocus()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Add a todo for today, or start a task…") },
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        trailingIcon = {
            Row {
                IconButton(onClick = { submit(onAddTodo) }, enabled = canSubmit) {
                    Icon(Icons.Default.Checklist, contentDescription = "Add as a todo due today")
                }
                IconButton(onClick = { submit(onStartTask) }, enabled = canSubmit) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start tracking this now",
                        tint = if (canSubmit) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit(onAddTodo) }),
        shape = MaterialTheme.shapes.medium
    )
}

/** "in 5 min", "in 2 h 15 min", or "now" once the minute has arrived. */
private fun countdownLabel(minuteOfDay: Int, nowMinuteOfDay: Int): String {
    val diff = minuteOfDay - nowMinuteOfDay
    if (diff <= 0) return "now"
    val h = diff / 60
    val m = diff % 60
    return when {
        h == 0 -> "in $m min"
        m == 0 -> "in $h h"
        else -> "in $h h $m min"
    }
}

/**
 * The red banner: overdue todos, todos already past their time today, and anything due or
 * ringing within the hour. One line of counts, one line naming the soonest thing. Tapping it goes
 * to the Todos list, where all of this can be dealt with.
 */
@Composable
private fun NudgeBanner(nudge: Nudge, nowMinuteOfDay: Int, onClick: () -> Unit) {
    val counts = buildList {
        if (nudge.overdueCount > 0) add("${nudge.overdueCount} overdue")
        if (nudge.lateTodayCount > 0) add("${nudge.lateTodayCount} past due today")
        if (nudge.dueSoon.isNotEmpty()) add("${nudge.dueSoon.size} due within the hour")
    }.joinToString(" · ")
    val soonest = nudge.dueSoon.firstOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NotificationImportant, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(counts, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (soonest != null) {
                    val time = soonest.deadlineMinuteOfDay
                    val detail = buildString {
                        append(soonest.title)
                        if (time != null) append(" · ${formatMinuteOfDay(time)}, ${countdownLabel(time, nowMinuteOfDay)}")
                        if (soonest.reminderOffsetMinutes != null) append(" · alarm set")
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open Todos")
        }
    }
}

/** The next few things on today's clock -- blocks starting later and todos due at a time -- each
 * with its clock time and a countdown. Blocks open the Todos hub (Schedule segment is one tap
 * further, see NowCard), todos open the list. */
@Composable
private fun UpNextCard(
    items: List<UpNextItem>,
    nowMinuteOfDay: Int,
    onOpenSchedule: () -> Unit,
    onOpenTodos: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Up next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                when (item) {
                    is UpNextItem.Block -> UpNextRow(
                        time = item.minuteOfDay,
                        nowMinuteOfDay = nowMinuteOfDay,
                        title = item.block.title,
                        caption = "Schedule · until ${formatMinuteOfDay(item.block.endMinuteOfDay)}",
                        accent = Color(item.block.kind.colorValue),
                        hasAlarm = false,
                        onClick = onOpenSchedule
                    )
                    is UpNextItem.Due -> UpNextRow(
                        time = item.minuteOfDay,
                        nowMinuteOfDay = nowMinuteOfDay,
                        title = item.todo.title,
                        caption = "Todo due",
                        accent = todoPriorityTierColor(item.todo.priority),
                        hasAlarm = item.todo.reminderOffsetMinutes != null,
                        onClick = onOpenTodos
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextRow(
    time: Int,
    nowMinuteOfDay: Int,
    title: String,
    caption: String,
    accent: Color,
    hasAlarm: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(64.dp)) {
            Text(formatMinuteOfDay(time), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                countdownLabel(time, nowMinuteOfDay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasAlarm) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = "Alarm set",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Today's tracked minutes by kind -- see KindBreakdownDonut. */
@Composable
private fun KindBreakdownCard(tasks: List<Task>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Today by kind",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            KindBreakdownDonut(tasks = tasks, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NothingDueToday(onNavigateToTodos: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Nothing due today.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Anything you give a deadline of today shows up here, ready to check off or start tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        FilledTonalButton(onClick = onNavigateToTodos) {
            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Plan something")
        }
    }
}
