package com.inventoria.app.ui.screens.chat

import com.inventoria.app.data.ChatSummary
import com.inventoria.app.data.ContextLevel
import com.inventoria.app.data.ModelCatalog
import java.util.Locale

/** A token count the way a person reads it: 850, 84k, 1.2M. */
fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1f", tokens / 1_000_000.0).removeSuffix(".0") + "M"
    tokens >= 1_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}

/** "Context 42% · 84k of 200k", or what there is to say before Claude has reported any. */
fun contextLine(chat: ChatSummary?): String {
    if (chat == null || chat.contextTokens <= 0) return "Context empty"
    val window = if (chat.contextWindow > 0) " of ${formatTokens(chat.contextWindow)}" else ""
    return "Context ${chat.contextPercent}% · ${formatTokens(chat.contextTokens)}$window"
}

/** "54 min", rounded up so the last minute never reads as zero. */
private fun minutesLabel(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
    return if (minutes >= 120) "${minutes / 60} h" else "$minutes min"
}

/**
 * What the app can say about this chat's session on the PC: open (and when it will close if nobody
 * writes), or asleep. [serverNow] is server time, the clock [ChatSummary.lastActiveAt] is on.
 */
fun sessionLine(chat: ChatSummary?, serverNow: Long): String {
    if (chat == null || chat.state == null) {
        return "Claude starts when you send the first message."
    }
    if (chat.isLive) {
        val closesIn = chat.closesInMillis(serverNow)
        return when {
            closesIn == null -> "Session open"
            closesIn <= 0 -> "Session closing"
            else -> "Session open · closes in ${minutesLabel(closesIn)} if idle"
        }
    }
    return "Session asleep · your next message wakes it where you left off"
}

/** The nudge to start a fresh chat, or null while there is plenty of room. */
fun contextAdvice(level: ContextLevel): String? = when (level) {
    ContextLevel.FINE -> null
    ContextLevel.GETTING_FULL ->
        "This chat is getting full. For the sharpest answers, start a new chat when you move on to something else."
    ContextLevel.FULL ->
        "This chat is almost full. Start a new chat: answers here will get slower and less reliable."
}

/** The effort levels the chosen model takes, as Claude reported them; none for a model with no effort setting. */
fun effortOptions(catalog: ModelCatalog, model: String?): List<String> = catalog.option(model)?.efforts.orEmpty()

/** An effort carried over to a newly chosen model: kept if that model takes it, else back to its default. */
fun effortFor(catalog: ModelCatalog, model: String?, effort: String?): String? =
    effort?.takeIf { it in effortOptions(catalog, model) }

/** "high" as "High". */
fun effortLabel(effort: String): String = effort.replaceFirstChar { it.uppercase() }

/**
 * What a chat's model and effort choice reads as: "Opus 5.5 · High effort", or "Default model" when
 * nothing was picked. Names come from [catalog]; a model it no longer lists shows as its raw value.
 */
fun choiceLabel(catalog: ModelCatalog, model: String?, effort: String?): String {
    val option = catalog.option(model)
    val name = when {
        model == null || option?.isDefault == true -> "Default model"
        else -> option?.name ?: model.orEmpty()
    }
    return if (effort == null) name else "$name · ${effortLabel(effort)} effort"
}
