package com.fastingshift.domain.ramadan

import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** One optional evening reminder even when multiple selected observances fall on the same day. */
object SunnahReminderPlanner {
    private val evening = LocalTime.of(20, 0)

    fun next(
        selected: Set<SunnahType>,
        offsetDays: Int,
        zone: ZoneId,
        now: Instant,
        onBreak: (LocalDate) -> Boolean = { false },
    ): Reminder? {
        if (selected.isEmpty()) return null
        val today = now.atZone(zone).toLocalDate()
        // Two lunar years also cover an annual observance skipped during a planned break.
        for (ahead in 0L..710L) {
            val eveningDate = today.plusDays(ahead)
            val at = eveningDate.atTime(evening).atZone(zone).toInstant()
            val fastDay = eveningDate.plusDays(1)
            if (at.isAfter(now) && !onBreak(eveningDate) && !onBreak(fastDay) && follows(fastDay, selected, offsetDays)) {
                return Reminder(ReminderKind.SUNNAH_FAST, at, fastId = 0)
            }
        }
        return null
    }

    internal fun follows(date: LocalDate, selected: Set<SunnahType>, offsetDays: Int): Boolean {
        val lunar = try {
            HijrahDate.from(date.plusDays(offsetDays.coerceIn(-2, 2).toLong()))
        } catch (_: DateTimeException) {
            // Umm al-Qura dates outside the device's supported table cannot be verified.
            return false
        }
        val month = lunar.get(ChronoField.MONTH_OF_YEAR)
        val day = lunar.get(ChronoField.DAY_OF_MONTH)
        // Ramadan, both Eids and the days of Tashreeq are excluded for every selection.
        if (month == 9 || (month == 10 && day == 1) || (month == 12 && day in 10..13)) return false
        return selected.any { type ->
            when (type) {
                SunnahType.MON_THU -> date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY
                SunnahType.AYYAMUL_BIDH -> day in 13..15
                SunnahType.ARAFAH -> month == 12 && day == 9
                SunnahType.ASHURA -> month == 1 && day in 9..10
                // A single season-opening reminder. The user may choose their six days throughout Shawwal.
                SunnahType.SHAWWAL -> month == 10 && day == 2
            }
        }
    }
}
