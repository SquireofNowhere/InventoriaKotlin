package com.inventoria.shared.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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

/** How often a [ReminderSchedule] comes round. */
enum class ScheduleKind { ONCE, DAILY, WEEKLY, MONTHLY }

/**
 * A reminder that comes from the calendar rather than from the deadline, so it works with no
 * deadline at all: "every day at 08:00", "Mondays and Fridays at 08:00", "the 15th of every month",
 * "on 3 November at 10:00". Any of them can also [ring again and again][burstEveryMinutes] on the
 * day it falls on -- every 5 minutes from [minuteOfDay] until [burstUntilMinute].
 *
 * The Android app keeps an identical copy of this logic and the text form is the contract between
 * them: `d@0800`, `w15@0800` (ISO weekdays, 1 = Monday), `m15@0900`, `o20261103@1000`, and
 * `~5-1700` on the end for the repeat. Build one through [normalised].
 */
data class ReminderSchedule(
    val kind: ScheduleKind,
    /** When the first ring of the day happens, in minutes since midnight. */
    val minuteOfDay: Int,
    /** ONCE: the day, as yyyyMMdd. */
    val date: Int = 0,
    /** WEEKLY: ISO weekdays, 1 = Monday ... 7 = Sunday. */
    val weekdays: Set<Int> = emptySet(),
    /** MONTHLY: 1-31. A month too short for it rings on its last day. */
    val dayOfMonth: Int = 1,
    /** Minutes between rings on the day, or 0 for a single ring. */
    val burstEveryMinutes: Int = 0,
    /** The last minute a repeat may ring at. Ignored without [burstEveryMinutes]. */
    val burstUntilMinute: Int = minuteOfDay
) {
    /** How many times this rings on a day it falls on. */
    val ringsPerDay: Int
        get() = if (burstEveryMinutes <= 0) 1 else (burstUntilMinute - minuteOfDay) / burstEveryMinutes + 1

    /** This schedule with every field in range and the unused ones zeroed, or null if it cannot ring. */
    fun normalised(): ReminderSchedule? {
        if (minuteOfDay !in 0 until MINUTES_PER_DAY) return null
        val repeating = burstEveryMinutes > 0
        val every = if (repeating) burstEveryMinutes.coerceIn(MIN_BURST_EVERY_MINUTES, MINUTES_PER_DAY) else 0
        val until = burstUntilMinute.coerceIn(minuteOfDay, MINUTES_PER_DAY - 1)
        val burst = repeating && until > minuteOfDay
        val base = copy(
            date = 0, weekdays = emptySet(), dayOfMonth = 1,
            burstEveryMinutes = if (burst) every else 0,
            burstUntilMinute = if (burst) until else minuteOfDay
        )
        return when (kind) {
            ScheduleKind.DAILY -> base
            ScheduleKind.WEEKLY -> weekdays.filter { it in 1..7 }.toSet().takeIf { it.isNotEmpty() }
                ?.let { base.copy(weekdays = it) }
            ScheduleKind.MONTHLY -> dayOfMonth.takeIf { it in 1..31 }?.let { base.copy(dayOfMonth = it) }
            ScheduleKind.ONCE -> date.takeIf { isValidDate(it) }?.let { base.copy(date = it) }
        }
    }

    val token: String
        get() {
            val head = when (kind) {
                ScheduleKind.DAILY -> "d"
                ScheduleKind.WEEKLY -> "w" + weekdays.sorted().joinToString("")
                ScheduleKind.MONTHLY -> "m$dayOfMonth"
                ScheduleKind.ONCE -> "o$date"
            }
            val burst = if (burstEveryMinutes > 0) "~$burstEveryMinutes-${clock(burstUntilMinute, "")}" else ""
            return "$head@${clock(minuteOfDay, "")}$burst"
        }

    /** "Every Mon, Wed at 08:00" / "Monthly on the 15th at 09:00, then every 5 min until 17:00". */
    fun describe(): String {
        val days = when (kind) {
            ScheduleKind.DAILY -> "Every day"
            ScheduleKind.WEEKLY -> when (weekdays) {
                setOf(1, 2, 3, 4, 5) -> "Weekdays"
                setOf(6, 7) -> "Weekends"
                else -> if (weekdays.size == 7) "Every day" else "Every " + weekdays.sorted().joinToString(", ") { WEEKDAY_NAMES[it - 1] }
            }
            ScheduleKind.MONTHLY -> "Monthly on the ${ordinal(dayOfMonth)}"
            ScheduleKind.ONCE -> dateLabel(date)
        }
        val burst = if (burstEveryMinutes > 0) ", then every ${everyLabel(burstEveryMinutes)} until ${clock(burstUntilMinute)}" else ""
        return "$days at ${clock(minuteOfDay)}$burst"
    }

    /**
     * The first ring strictly after [now], or null when there is none (a one-off already behind
     * us). Days are stepped through on the calendar and the time of day is placed on the calendar
     * day, so a daylight-saving change never moves an 08:00 reminder.
     */
    fun nextAfter(now: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long? {
        if (kind == ScheduleKind.ONCE) {
            return firstRingOn(LocalDate(date / 10_000, date / 100 % 100, date % 100), now, zone)
        }
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        // A month is the longest gap between two matching days (the 31st clamps to a short month's last day).
        repeat(SEARCH_DAYS) { i ->
            val day = today.plus(DatePeriod(days = i))
            if (fallsOn(day)) firstRingOn(day, now, zone)?.let { return it }
        }
        return null
    }

    private fun fallsOn(day: LocalDate): Boolean = when (kind) {
        ScheduleKind.DAILY -> true
        ScheduleKind.WEEKLY -> day.dayOfWeek.ordinal + 1 in weekdays
        ScheduleKind.MONTHLY -> {
            val (year, month, dom) = ymd(day)
            minOf(dayOfMonth, daysInMonth(year, month)) == dom
        }
        ScheduleKind.ONCE -> false
    }

    /** The earliest ring on [day] that is after [now]. */
    private fun firstRingOn(day: LocalDate, now: Long, zone: TimeZone): Long? {
        val rings = ringsPerDay
        val dayStart = day.atStartOfDayIn(zone).toEpochMilliseconds()
        // Skip straight to the rings that could still be ahead rather than testing all 288. Elapsed
        // time is not wall-clock time on a daylight-saving day (an hour out), so back off that much.
        var k = if (burstEveryMinutes > 0 && now > dayStart) {
            (((now - dayStart) / 60_000L).toInt() - minuteOfDay) / burstEveryMinutes - 1 - 60 / burstEveryMinutes
        } else 0
        k = k.coerceIn(0, rings - 1)
        while (k < rings) {
            val minute = minuteOfDay + k * burstEveryMinutes
            val at = LocalDateTime(day, LocalTime(minute / 60, minute % 60)).toInstant(zone).toEpochMilliseconds()
            if (at > now) return at
            k++
        }
        return null
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /** Rings closer together than this are spam, the same floor as [ReminderPlan.MIN_EVERY_MINUTES]. */
        const val MIN_BURST_EVERY_MINUTES = 5

        private const val SEARCH_DAYS = 40

        private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        private val TOKEN = Regex("""^(?:(d)|w([1-7]{1,7})|m(\d{1,2})|o(\d{8}))@(\d{2})(\d{2})(?:~(\d{1,4})-(\d{2})(\d{2}))?$""")

        /** Reads [token]'s output; null for anything that is not a schedule that can ring. */
        fun parse(raw: String): ReminderSchedule? {
            val m = TOKEN.matchEntire(raw.trim().lowercase()) ?: return null
            val g = m.groupValues
            val kind = when {
                g[1] == "d" -> ScheduleKind.DAILY
                g[2].isNotEmpty() -> ScheduleKind.WEEKLY
                g[3].isNotEmpty() -> ScheduleKind.MONTHLY
                else -> ScheduleKind.ONCE
            }
            if (g[5].toInt() > 23 || g[6].toInt() > 59) return null
            val start = g[5].toInt() * 60 + g[6].toInt()
            val every = g[7].toIntOrNull() ?: 0
            val until = if (every > 0) g[8].toInt() * 60 + g[9].toInt() else start
            return ReminderSchedule(
                kind = kind,
                minuteOfDay = start,
                date = g[4].toIntOrNull() ?: 0,
                weekdays = g[2].map { it.digitToInt() }.toSet(),
                dayOfMonth = g[3].toIntOrNull() ?: 1,
                burstEveryMinutes = every,
                burstUntilMinute = until
            ).normalised()
        }

        /** yyyyMMdd for the given calendar day (month 1-12). */
        fun dateOf(year: Int, month: Int, day: Int): Int = year * 10_000 + month * 100 + day

        private fun isValidDate(date: Int): Boolean {
            val year = date / 10_000
            val month = date / 100 % 100
            return year in 2000..2999 && month in 1..12 && date % 100 in 1..daysInMonth(year, month)
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        /** A day's year, month and day of month, read off its ISO text ("2026-10-24") so this does not
         * depend on which names a kotlinx-datetime release gives those properties. */
        private fun ymd(day: LocalDate): Triple<Int, Int, Int> {
            val (y, m, d) = day.toString().split('-').map { it.toInt() }
            return Triple(y, m, d)
        }

        /** 540 as "09:00", or "0900" with an empty [separator]. */
        fun clock(minute: Int, separator: String = ":"): String =
            (minute / 60).toString().padStart(2, '0') + separator + (minute % 60).toString().padStart(2, '0')

        private fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }

        private fun dateLabel(date: Int): String =
            "${date % 100} ${MONTH_NAMES[date / 100 % 100 - 1]} ${date / 10_000}"

        private fun everyLabel(minutes: Int): String = when {
            minutes % 60 == 0 -> "${minutes / 60} hr"
            else -> "$minutes min"
        }
    }
}

/**
 * When a todo should remind its owner: any number of lead times ("4, 5 and 6 hours before"), a
 * repeat counted back from the due moment ("every 2 hours until the deadline"), and/or calendar
 * [schedules] that need no deadline. Stored on the todo as text ([encode]/[parse]); the Android app
 * keeps an identical copy of this logic, and the format is the contract between them. Example:
 * `before=0m,4h,1d every=2h on=w15@0800,m15@0900~5-1700`.
 */
data class ReminderPlan(
    val leadMinutes: List<Int> = emptyList(),
    val every: ReminderSpan? = null,
    val schedules: List<ReminderSchedule> = emptyList()
) {
    val isEmpty: Boolean get() = leadMinutes.isEmpty() && every == null && schedules.isEmpty()

    /** True when something in the plan counts from the deadline, so it means nothing without one. */
    val needsDeadline: Boolean get() = leadMinutes.isNotEmpty() || every != null

    fun encode(): String {
        val parts = mutableListOf<String>()
        if (leadMinutes.isNotEmpty()) parts += "before=" + leadMinutes.joinToString(",") { leadToken(it) }
        every?.let { parts += "every=${it.token}" }
        if (schedules.isNotEmpty()) parts += "on=" + schedules.joinToString(",") { it.token }
        return parts.joinToString(" ")
    }

    /** One line for people: "At due time, 6 hr, 4 hr before · every 2 hours until due · Every day at 08:00". */
    fun describe(): String {
        if (isEmpty) return "No reminders"
        return (deadlineParts() + schedules.map { it.describe() }).joinToString(" · ")
    }

    private fun deadlineParts(): List<String> {
        val parts = mutableListOf<String>()
        val timed = leadMinutes.filter { it != 0 }.sortedDescending()
        val head = buildList {
            if (0 in leadMinutes) add("At due time")
            if (timed.isNotEmpty()) add(timed.joinToString(", ") { leadLabel(it) } + " before")
        }
        if (head.isNotEmpty()) parts += head.joinToString(", ")
        every?.let { parts += "every ${spanWords(it)} until due" }
        return parts
    }

    companion object {
        val NONE = ReminderPlan()
        val AT_DUE = ReminderPlan(leadMinutes = listOf(0))

        /** A year's notice is the most a lead time is allowed. */
        const val MAX_LEAD_MINUTES = 366 * 24 * 60

        /** The shortest gap a repeating reminder may have. Anything tighter is spam. */
        const val MIN_EVERY_MINUTES = 5L

        const val MAX_EVERY_AMOUNT = 999

        /** A plan holds at most this many schedules; past it the dialog is a spreadsheet. */
        const val MAX_SCHEDULES = 20

        /** Normalised: leads de-duplicated, in range, earliest first; the repeat clamped to sane;
         * schedules checked, de-duplicated and kept in the order they were added. */
        fun of(
            leads: Collection<Int>,
            every: ReminderSpan?,
            schedules: Collection<ReminderSchedule> = emptyList()
        ): ReminderPlan = ReminderPlan(
            leadMinutes = leads.filter { it in 0..MAX_LEAD_MINUTES }.distinct().sortedDescending(),
            every = every?.let(::clampEvery),
            schedules = schedules.mapNotNull { it.normalised() }.distinct().take(MAX_SCHEDULES)
        )

        private fun clampEvery(span: ReminderSpan): ReminderSpan? {
            if (span.amount < 1 || span.amount > MAX_EVERY_AMOUNT) return null
            val minutes = span.fixedMinutes ?: return span
            return if (minutes < MIN_EVERY_MINUTES) ReminderSpan(MIN_EVERY_MINUTES.toInt(), ReminderUnit.MINUTE) else span
        }

        /** Reads [encode]'s output. Anything unreadable is dropped rather than failing. */
        fun parse(raw: String): ReminderPlan {
            var leads = emptyList<Int>()
            var every: ReminderSpan? = null
            var schedules = emptyList<ReminderSchedule>()
            raw.trim().split(Regex("\\s+")).forEach { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                when (key) {
                    "before" -> leads = value.split(',').mapNotNull { leadMinutesOf(it) }
                    "every" -> every = ReminderSpan.parse(value)
                    "on" -> schedules = value.split(',').mapNotNull { ReminderSchedule.parse(it) }
                }
            }
            return of(leads, every, schedules)
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
 * deadline so a device still on an older build rings for it. With no deadline the lead times and
 * the repeat are dropped, because a reminder with nothing to count from means nothing -- but
 * schedules stay, since they count from the calendar. */
fun Todo.withReminders(plan: ReminderPlan): Todo {
    val effective = if (deadline == null) ReminderPlan.of(emptyList(), null, plan.schedules) else plan
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
 * The next instant after [now] this todo should remind, or null when nothing is left to ring: no
 * plan, finished, deleted, or every reminder already behind us. Lead times and the repeat need a
 * deadline; schedules do not, and keep ringing past it until the todo is ticked off.
 * [includeRepeats] false asks only about the one-off alarms: lead times and dated one-offs.
 */
fun Todo.nextReminderAfter(
    now: Long,
    includeRepeats: Boolean = true,
    zone: TimeZone = TimeZone.currentSystemDefault()
): Long? {
    if (isDeleted || state == TodoState.COMPLETE) return null
    val plan = reminders()
    if (plan.isEmpty) return null

    val due = dueMillis()
    val fromDeadline = if (due == null) emptyList() else {
        plan.leadMinutes.map { due - it * 60_000L }.filter { it > now } +
            listOfNotNull(plan.every?.takeIf { includeRepeats }?.let { repeatTickAfter(due, it, now, zone) })
    }
    val fromCalendar = plan.schedules
        .filter { includeRepeats || it.kind == ScheduleKind.ONCE }
        .mapNotNull { it.nextAfter(now, zone) }
    return (fromDeadline + fromCalendar).minOrNull()
}

/** The first tick of "every [span] counted back from [due]" that falls after [now]. */
private fun repeatTickAfter(due: Long, span: ReminderSpan, now: Long, zone: TimeZone): Long? {
    if (due <= now) return null
    val fixed = span.fixedMinutes?.times(60_000L)
    if (fixed != null) return due - (due - now - 1) / fixed * fixed
    return calendarTickAfter(due, span, now, zone)
}

/** Days, weeks and months are calendar steps, so a daylight-saving change cannot drag a 17:00
 * reminder to 16:00 and 31 March - 1 month lands on 28 February. Every tick is computed from
 * [due], not from the previous tick, so a clamped month does not drift. */
private fun calendarTickAfter(due: Long, span: ReminderSpan, now: Long, zone: TimeZone): Long {
    fun tick(k: Int): Long {
        val period = when (span.unit) {
            ReminderUnit.WEEK -> DatePeriod(days = -7 * span.amount * k)
            ReminderUnit.MONTH -> DatePeriod(months = -span.amount * k)
            else -> DatePeriod(days = -span.amount * k)
        }
        return plusCalendar(due, period, zone)
    }

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
