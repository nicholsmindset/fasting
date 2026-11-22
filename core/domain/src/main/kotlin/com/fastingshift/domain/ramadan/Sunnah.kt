package com.fastingshift.domain.ramadan

import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Days on which voluntary fasts are not suggested. */
enum class NoFastReason {
    /** The whole of Ramadan: the obligatory fast, so no voluntary fasts are suggested. */
    RAMADAN,

    /** 1 Shawwal. */
    EID_AL_FITR,

    /** 10 Dhul Hijjah. */
    EID_AL_ADHA,

    /** 11–13 Dhul Hijjah (days of tashreeq). */
    TASHREEQ,
}

/** A day with at least one Sunnah fast, for the calendar. [types] is never empty. */
data class SunnahDay(val date: LocalDate, val hijri: HijriDate, val types: Set<SunnahType>) {
    /** The most specific type, used for the headline and the reminder text. */
    val primary: SunnahType get() = Sunnah.primary(types)
}

/**
 * Which voluntary (Sunnah) fasts fall on a date, from the Hijri calendar with the user's
 * offset. A calendar aid only: dates may differ from local announcements.
 */
object Sunnah {
    /** Default time of the evening-before reminder, in the phone's zone. */
    val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)
    const val REMINDER_DAYS_AHEAD = 7

    /** Reminder index = slot × [SLOT] + type ordinal; slot ≤ 7 keeps it under 100. */
    private const val SLOT = 10

    /** Most specific first: Arafah, then Tasu'a/Ashura, white days, Shawwal, Monday/Thursday. */
    private val PRIORITY = listOf(SunnahType.ARAFAH, SunnahType.ASHURA, SunnahType.AYYAMUL_BIDH, SunnahType.SHAWWAL, SunnahType.MON_THU)

    fun primary(types: Set<SunnahType>): SunnahType = PRIORITY.first { it in types }

    /** Why no voluntary fast is suggested on this Hijri day, or null if one may be. */
    fun noFastReason(h: HijriDate): NoFastReason? = when {
        h.month == HijriMonth.RAMADAN -> NoFastReason.RAMADAN
        h.month == HijriMonth.SHAWWAL && h.day == 1 -> NoFastReason.EID_AL_FITR
        h.month == HijriMonth.DHUL_HIJJAH && h.day == 10 -> NoFastReason.EID_AL_ADHA
        h.month == HijriMonth.DHUL_HIJJAH && h.day in 11..13 -> NoFastReason.TASHREEQ
        else -> null
    }

    fun noFastReason(date: LocalDate, offsetDays: Int = 0): NoFastReason? = Hijri.of(date, offsetDays)?.let(::noFastReason)

    /** Every Sunnah fast type that applies to [date]; empty on excluded days or out of range. */
    fun typesFor(date: LocalDate, offsetDays: Int = 0): Set<SunnahType> {
        val h = Hijri.of(date, offsetDays) ?: return emptySet()
        if (noFastReason(h) != null) return emptySet()
        val types = mutableSetOf<SunnahType>()
        if (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) types += SunnahType.MON_THU
        if (h.day in 13..15) types += SunnahType.AYYAMUL_BIDH
        if (h.month == HijriMonth.DHUL_HIJJAH && h.day == 9) types += SunnahType.ARAFAH
        if (h.month == HijriMonth.MUHARRAM && h.day in 9..10) types += SunnahType.ASHURA
        // Six days of Shawwal, any days after Eid: every remaining day is a suggestion.
        if (h.month == HijriMonth.SHAWWAL && h.day >= 2) types += SunnahType.SHAWWAL
        return types
    }

    /**
     * Sunnah days from [from] (inclusive) for [days] days, limited to [enabled] types.
     */
    fun upcoming(from: LocalDate, days: Int, offsetDays: Int = 0, enabled: Set<SunnahType> = SunnahType.entries.toSet()): List<SunnahDay> =
        (0 until days.coerceAtLeast(0)).mapNotNull { i ->
            val date = from.plusDays(i.toLong())
            val types = typesFor(date, offsetDays) intersect enabled
            val h = Hijri.of(date, offsetDays)
            if (types.isEmpty() || h == null) null else SunnahDay(date, h, types)
        }

    /**
     * Evening-before reminders (at [at], local time in [zone]) for Sunnah days in the next
     * [REMINDER_DAYS_AHEAD] days whose types are [enabled]. One reminder per day; its
     * index encodes the day's [SunnahDay.primary] type (see [typeOf]).
     */
    fun reminders(
        now: Instant,
        zone: ZoneId,
        offsetDays: Int,
        enabled: Set<SunnahType>,
        at: LocalTime = DEFAULT_REMINDER_TIME,
    ): List<Reminder> {
        if (enabled.isEmpty()) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        return upcoming(today.plusDays(1), REMINDER_DAYS_AHEAD, offsetDays, enabled).mapNotNull { day ->
            val trigger = day.date.minusDays(1).atTime(at).atZone(zone).toInstant()
            if (!trigger.isAfter(now)) return@mapNotNull null
            val slot = ChronoUnit.DAYS.between(today, day.date).toInt()
            Reminder(ReminderKind.SUNNAH_FAST, trigger, fastId = 0, index = slot * SLOT + day.primary.ordinal)
        }
    }

    /** The Sunnah type a [ReminderKind.SUNNAH_FAST] reminder is for. */
    fun typeOf(reminder: Reminder): SunnahType? =
        if (reminder.kind == ReminderKind.SUNNAH_FAST) SunnahType.entries.getOrNull(reminder.index % SLOT) else null
}
