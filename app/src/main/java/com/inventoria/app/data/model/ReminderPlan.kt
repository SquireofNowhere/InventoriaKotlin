package com.inventoria.app.data.model

import java.util.Calendar

/** The unit of a [ReminderSpan]. MONTH is the one that is not a fixed number of minutes, which is
 * why it can repeat a reminder but cannot be a lead time. */
enum class ReminderUnit(val token: String, val minutes: Int) {
    MINUTE("m", 1),
    HOUR("h", 60),
    DAY("d", 24 * 60),
    WEEK("w", 7 * 24 * 60),
    MONTH("mo", 0)
}

/** "2 hours", "3 days": a length of time as the user typed it. */
data class ReminderSpan(val amount: Int, val unit: ReminderUnit) {
    /** Exact length, or null for MONTH. */
    val fixedMinutes: Long? get() = unit.minutes.takeIf { it > 0 }?.let { it.toLong() * amount }

    val token: String get() = "$amount${unit.token}"

    companion object {
        private val TOKEN = Regex("""^(\d{1,6})(mo|m|h|d|w)$""")

        fun parse(raw: String): ReminderSpan? {
            val match = TOKEN.matchEntire(raw.trim().lowercase()) ?: return null
            val unit = ReminderUnit.entries.first { it.token == match.groupValues[2] }
            return ReminderSpan(match.groupValues[1].toInt(), unit)
        }
    }
}

/**
 * When a todo should remind its owner, as two independent rules that can be combined:
 *
 *  - [leadMinutes]: one alarm per entry, that many minutes before the due moment ("4, 5 and 6 hours
 *    before"). 0 is the due moment itself.
 *  - [every]: a reminder every so often, counted back from the due moment, up to and including it
 *    ("every 2 hours until the deadline", "every week until the deadline").
 *
 * Stored on the todo as text ([encode]/[parse]) so it syncs as one field and a build that does not
 * know a future rule simply ignores the part it cannot read. Example: `before=0m,4h,1d every=2h`.
 */
data class ReminderPlan(
    val leadMinutes: List<Int> = emptyList(),
    val every: ReminderSpan? = null
) {
    val isEmpty: Boolean get() = leadMinutes.isEmpty() && every == null

    fun encode(): String {
        val parts = mutableListOf<String>()
        if (leadMinutes.isNotEmpty()) parts += "before=" + leadMinutes.joinToString(",") { leadToken(it) }
        every?.let { parts += "every=${it.token}" }
        return parts.joinToString(" ")
    }

    /** One line for the dialog and the row: "At due time, 4 hr before · every 2 hours until due". */
    fun describe(): String {
        if (isEmpty) return "No reminders"
        val parts = mutableListOf<String>()
        val timed = leadMinutes.filter { it != 0 }.sortedDescending()
        val head = buildList {
            if (0 in leadMinutes) add("At due time")
            if (timed.isNotEmpty()) add(timed.joinToString(", ") { leadLabel(it) } + " before")
        }
        if (head.isNotEmpty()) parts += head.joinToString(", ")
        every?.let { parts += "every ${spanWords(it)} until due" }
        return parts.joinToString(" · ")
    }

    companion object {
        val NONE = ReminderPlan()
        val AT_DUE = ReminderPlan(leadMinutes = listOf(0))

        /** A year's notice is the most a lead time is allowed; the app is not a calendar. */
        const val MAX_LEAD_MINUTES = 366 * 24 * 60

        /** The shortest gap a repeating reminder may have. Anything tighter is spam, not a reminder. */
        const val MIN_EVERY_MINUTES = 5L

        const val MAX_EVERY_AMOUNT = 999

        /** Normalised: leads de-duplicated, in range, earliest first; the repeat clamped to sane. */
        fun of(leads: Collection<Int>, every: ReminderSpan?): ReminderPlan = ReminderPlan(
            leadMinutes = leads.filter { it in 0..MAX_LEAD_MINUTES }.distinct().sortedDescending(),
            every = every?.let(::clampEvery)
        )

        private fun clampEvery(span: ReminderSpan): ReminderSpan? {
            if (span.amount < 1 || span.amount > MAX_EVERY_AMOUNT) return null
            val minutes = span.fixedMinutes ?: return span
            return if (minutes < MIN_EVERY_MINUTES) ReminderSpan(MIN_EVERY_MINUTES.toInt(), ReminderUnit.MINUTE) else span
        }

        /** Reads [encode]'s output. Anything unreadable is dropped rather than failing, so a row
         * written by a newer build still yields the rules this build understands. */
        fun parse(raw: String): ReminderPlan {
            var leads = emptyList<Int>()
            var every: ReminderSpan? = null
            raw.trim().split(Regex("\\s+")).forEach { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                when (key) {
                    "before" -> leads = value.split(',').mapNotNull { leadMinutesOf(it) }
                    "every" -> every = ReminderSpan.parse(value)
                }
            }
            return of(leads, every)
        }

        /** A lead-time token ("4h", "30m", "0m") as minutes; null for months and anything else. */
        fun leadMinutesOf(token: String): Int? {
            val span = ReminderSpan.parse(token) ?: return null
            return span.fixedMinutes?.takeIf { it <= MAX_LEAD_MINUTES }?.toInt()
        }

        fun leadToken(minutes: Int): String = when {
            minutes != 0 && minutes % ReminderUnit.WEEK.minutes == 0 -> "${minutes / ReminderUnit.WEEK.minutes}w"
            minutes != 0 && minutes % ReminderUnit.DAY.minutes == 0 -> "${minutes / ReminderUnit.DAY.minutes}d"
            minutes != 0 && minutes % ReminderUnit.HOUR.minutes == 0 -> "${minutes / ReminderUnit.HOUR.minutes}h"
            else -> "${minutes}m"
        }

        fun leadLabel(minutes: Int): String = when {
            minutes == 0 -> "At due time"
            minutes % ReminderUnit.WEEK.minutes == 0 -> plural(minutes / ReminderUnit.WEEK.minutes, "week")
            minutes % ReminderUnit.DAY.minutes == 0 -> plural(minutes / ReminderUnit.DAY.minutes, "day")
            minutes % ReminderUnit.HOUR.minutes == 0 -> "${minutes / ReminderUnit.HOUR.minutes} hr"
            else -> "$minutes min"
        }

        fun spanWords(span: ReminderSpan): String {
            val noun = when (span.unit) {
                ReminderUnit.MINUTE -> "minute"
                ReminderUnit.HOUR -> "hour"
                ReminderUnit.DAY -> "day"
                ReminderUnit.WEEK -> "week"
                ReminderUnit.MONTH -> "month"
            }
            return if (span.amount == 1) noun else plural(span.amount, noun)
        }

        private fun plural(n: Int, noun: String) = if (n == 1) "$n $noun" else "$n ${noun}s"

        /** "in 3 hr", "in 2 days": how far off the due moment is, for the notification text. */
        fun remainingLabel(millis: Long): String {
            val minutes = millis / 60_000L
            return when {
                minutes < 1 -> "now"
                minutes < 60 -> "in $minutes min"
                minutes < 24 * 60 -> "in ${minutes / 60} hr" + (minutes % 60).let { if (it >= 1) " $it min" else "" }
                else -> "in " + plural((minutes / (24 * 60)).toInt(), "day")
            }
        }
    }
}

/** The plan this todo actually follows. A todo written before plans existed has only the single
 * [Todo.reminderOffsetMinutes], which reads as a plan with that one lead time. */
fun Todo.reminders(): ReminderPlan {
    val stored = ReminderPlan.parse(reminderPlan)
    if (!stored.isEmpty) return stored
    return reminderOffsetMinutes?.let { ReminderPlan.of(listOf(it), null) } ?: ReminderPlan.NONE
}

val Todo.hasReminder: Boolean get() = !reminders().isEmpty

/** This todo with [plan] set. [Todo.reminderOffsetMinutes] is kept as the lead time closest to the
 * deadline so a device still on an older build rings for it, and cleared when there is no
 * deadline, because a reminder with nothing to count from means nothing. */
fun Todo.withReminders(plan: ReminderPlan): Todo {
    val effective = if (deadline == null) ReminderPlan.NONE else plan
    return copy(
        reminderPlan = effective.encode(),
        reminderOffsetMinutes = effective.leadMinutes.minOrNull()
    )
}

/** The moment this todo is due: its deadline day plus its time of day, or 09:00 for an all-day one. */
fun Todo.dueMillis(): Long? {
    val day = deadline ?: return null
    return day + (deadlineMinuteOfDay ?: ALL_DAY_REMINDER_MINUTE_OF_DAY) * 60_000L
}

/**
 * The next instant after [now] this todo should remind, or null when nothing is left to ring:
 * no deadline, no plan, finished, deleted, or every reminder already behind us.
 *
 * Only the *next* reminder is ever asked for. The scheduler arms that one alarm and asks again
 * when it fires, so a todo reminding every two hours for a month never has hundreds of alarms
 * outstanding -- and anything the device missed while it was off is skipped, not replayed.
 *
 * [includeRepeats] false asks only about the lead-time alarms, for callers that want "is something
 * about to go off for this deadline" and would otherwise be answered "yes, always" by a todo that
 * reminds every two hours.
 */
fun Todo.nextReminderAfter(now: Long, includeRepeats: Boolean = true): Long? {
    if (isDeleted || state == TodoState.COMPLETE) return null
    val due = dueMillis() ?: return null
    val plan = reminders()
    if (plan.isEmpty) return null

    val candidates = plan.leadMinutes.map { due - it * 60_000L }.filter { it > now } +
        listOfNotNull(plan.every?.takeIf { includeRepeats }?.let { repeatTickAfter(due, it, now) })
    return candidates.minOrNull()
}

/** The first tick of "every [span] counted back from [due]" that falls after [now]. */
private fun repeatTickAfter(due: Long, span: ReminderSpan, now: Long): Long? {
    if (due <= now) return null
    val fixed = span.fixedMinutes?.times(60_000L)
    if (fixed != null) {
        // The ticks are due, due - fixed, due - 2*fixed ...; the wanted one is the last that is
        // still after now, which is straight arithmetic.
        return due - (due - now - 1) / fixed * fixed
    }
    return calendarTickAfter(due, span, now)
}

/** Days, weeks and months are calendar steps rather than fixed lengths, so a daylight-saving
 * change cannot drag a 17:00 reminder to 16:00 and 31 March - 1 month lands on 28 February. Every
 * tick is computed from [due], not from the previous tick, so a clamped month does not drift. */
private fun calendarTickAfter(due: Long, span: ReminderSpan, now: Long): Long {
    fun tick(k: Int): Long = Calendar.getInstance().apply {
        timeInMillis = due
        when (span.unit) {
            ReminderUnit.WEEK -> add(Calendar.DAY_OF_YEAR, -7 * span.amount * k)
            ReminderUnit.MONTH -> add(Calendar.MONTH, -span.amount * k)
            else -> add(Calendar.DAY_OF_YEAR, -span.amount * k)
        }
    }.timeInMillis

    // Jump close, then walk the last few steps exactly. The jump divides by the *longest* a step
    // can be (a month is at most 31 days) and backs off one more step, which covers the hour or
    // two a daylight-saving change adds, so k starts at or before the answer -- never past it.
    val stepLongest = when (span.unit) {
        ReminderUnit.WEEK -> 7 * 24 * 3_600_000L
        ReminderUnit.MONTH -> 31 * 24 * 3_600_000L
        else -> 24 * 3_600_000L
    } * span.amount
    var k = (((due - now) / stepLongest) - 1).coerceAtLeast(0).toInt()
    while (k > 0 && tick(k) <= now) k--
    while (tick(k + 1) > now) k++
    return tick(k)
}
