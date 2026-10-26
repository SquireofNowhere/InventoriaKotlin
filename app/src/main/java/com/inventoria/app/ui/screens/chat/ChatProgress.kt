package com.inventoria.app.ui.screens.chat

import com.inventoria.app.data.ChatMessage
import com.inventoria.app.data.ChatRole
import com.inventoria.app.data.Connection
import com.inventoria.app.data.ListenerPresence

/** WARNING means "something may be wrong, and here is what to check"; INFO means "just wait". */
enum class ProgressTone { INFO, WARNING }

/**
 * What the chat tells the user while a message has no answer yet. [busy] draws a spinner; a
 * warning without one is a dead end the user can act on (start the listener, reconnect).
 */
data class ChatProgress(
    val headline: String,
    val detail: String? = null,
    val busy: Boolean = true,
    val tone: ProgressTone = ProgressTone.INFO
)

/** The state of the link to Claude, for the line under the chat's title. */
enum class LinkState(val label: String) {
    CHECKING("Checking Claude's PC…"),
    ONLINE("Claude's PC is online"),
    BUSY("Claude is busy"),
    PC_OFFLINE("Claude's PC isn't connected"),
    PC_NOT_SEEN("No sign of Claude's PC"),
    PHONE_OFFLINE("This phone is offline");

    /** Whether replies can be expected, as opposed to a state worth a second look. */
    val isHealthy: Boolean get() = this == ONLINE || this == BUSY || this == CHECKING
}

/** How long before a still-unclaimed message starts asking whether the listener is running. */
private const val CHECKING_MILLIS = 15_000L

/** Listeners that predate heartbeats never beat, so they get this long before being doubted. */
private const val LEGACY_PATIENCE_MILLIS = 60_000L

private const val SLOW_SEND_MILLIS = 10_000L
private const val SOME_TIME_MILLIS = 15_000L
private const val LONG_RUN_MILLIS = 90_000L

fun linkState(presence: ListenerPresence?, connection: Connection, now: Long): LinkState = when {
    !connection.connected -> LinkState.PHONE_OFFLINE
    presence == null -> LinkState.CHECKING
    presence.isAlive(now + connection.serverOffset) -> if (presence.busy) LinkState.BUSY else LinkState.ONLINE
    presence.lastSeen > 0 -> LinkState.PC_OFFLINE
    else -> LinkState.PC_NOT_SEEN
}

/**
 * Everything the chat can say about the newest unanswered message, in the order a message travels:
 * on this phone (sending), at the server (sent), claimed by the listener (working, with what Claude
 * is doing), and finally the ways it can stall -- a phone with no connection, a listener that was
 * never there or has gone, a run that is just long. Null when nothing is awaited.
 *
 * [now] is the phone's clock; [presence] null means the heartbeat has not been read yet.
 */
fun chatProgress(
    waitingOn: ChatMessage?,
    presence: ListenerPresence?,
    connection: Connection,
    now: Long
): ChatProgress? {
    if (waitingOn == null) return null
    val waited = (now - waitingOn.createdAt).coerceAtLeast(0L)
    val alive = presence?.isAlive(now + connection.serverOffset) == true
    val everSeen = presence != null && presence.lastSeen > 0

    return when {
        waitingOn.sending && !connection.connected -> ChatProgress(
            "Waiting for a connection",
            "Your message is saved on this phone and will be sent as soon as it is back online.",
            busy = false, tone = ProgressTone.WARNING
        )
        waitingOn.sending -> ChatProgress(
            "Sending…",
            if (waited > SLOW_SEND_MILLIS) "The connection is slow. Still trying." else null
        )
        !connection.connected -> ChatProgress(
            "This phone is offline",
            "Your message was sent. The answer will show up when you reconnect.",
            busy = false, tone = ProgressTone.WARNING
        )
        waitingOn.status == ChatMessage.STATUS_WORKING -> working(waitingOn, waited, everSeen, alive)
        else -> unclaimed(waited, presence, everSeen, alive)
    }
}

private fun working(message: ChatMessage, waited: Long, everSeen: Boolean, alive: Boolean): ChatProgress {
    // It was working, and then stopped reporting in: a crash, a closed laptop. Without this the
    // spinner would run forever.
    if (everSeen && !alive) {
        return ChatProgress(
            "Lost contact with Claude's PC",
            "It was working on this but has stopped checking in. If it doesn't come back, ask again.",
            busy = false, tone = ProgressTone.WARNING
        )
    }
    val headline = when (message.activity) {
        // The listener says what the start is: a brand new chat, or an asleep one being reopened.
        "starting" -> message.activityText?.let { "$it…" } ?: "Claude is getting started…"
        "thinking" -> "Claude is thinking…"
        "looking" -> message.activityText?.let { "$it…" } ?: "Claude is looking things up…"
        "changing" -> message.activityText?.let { "$it…" } ?: "Claude is making changes…"
        "writing" -> "Claude is writing the reply…"
        else -> "Claude is working on it…"
    }
    val detail = when {
        waited >= LONG_RUN_MILLIS ->
            "${elapsed(waited)} so far. Requests that look things up or change several things take a while."
        waited >= SOME_TIME_MILLIS -> "${elapsed(waited)} so far"
        else -> null
    }
    return ChatProgress(headline, detail)
}

private fun unclaimed(waited: Long, presence: ListenerPresence?, everSeen: Boolean, alive: Boolean): ChatProgress = when {
    alive -> ChatProgress(
        "Sent. Waiting for Claude…",
        if (presence?.busy == true) "Claude is finishing another request first." else null
    )
    // The first moments: the heartbeat may simply not have been read yet.
    presence == null || (!everSeen && waited < CHECKING_MILLIS) ->
        ChatProgress("Sent. Checking that Claude's PC is online…")
    everSeen -> ChatProgress(
        "Claude's PC isn't connected",
        "Your message is saved. It will be answered when the agent listener is running again.",
        busy = false, tone = ProgressTone.WARNING
    )
    // Never a heartbeat: no listener, or one from before heartbeats existed.
    waited < LEGACY_PATIENCE_MILLIS -> ChatProgress("Sent. Waiting for Claude…")
    else -> ChatProgress(
        "Nothing has picked this up yet",
        "Is the agent listener running on your PC? Your message is saved and will be answered once it is.",
        busy = false, tone = ProgressTone.WARNING
    )
}

/** "45s", "2m 05s". */
fun elapsed(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${"%02d".format(seconds % 60)}s"
}

/** The caption under the user's own bubble: where that one message has got to. Null once answered. */
fun deliveryLabel(message: ChatMessage): String? = when {
    message.role != ChatRole.USER -> null
    message.status == "error" -> "Not answered"
    message.sending -> "Sending…"
    message.status == null || message.status == ChatMessage.STATUS_PENDING -> "Sent"
    message.status == ChatMessage.STATUS_WORKING -> "Claude is on it"
    else -> null
}
