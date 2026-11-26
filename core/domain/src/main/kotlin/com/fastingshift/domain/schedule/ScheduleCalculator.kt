package com.fastingshift.domain.schedule

import com.fastingshift.domain.history.History
import com.fastingshift.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Whether a requested first start can be added without changing an existing routine. */
enum class FirstPlanOffer { AVAILABLE, COVERED, UNAVAILABLE }

/** Weekly schedules for 5:2, ADF and custom fasting days (PRD F2). */
object ScheduleCalculator {

    /** All starts on a civil date, ordered by the actual instant (including clock changes). */
    fun startsOn(
        schedules: List<Schedule>,
        date: LocalDate,
        zone: ZoneId,
        breakWeeks: Set<LocalDate> = emptySet(),
        dayOverrides: Map<LocalDate, DayOverride> = emptyMap(),
        firstEvening: Pair<Long, LocalDate>? = null,
    ): List<Pair<Schedule, Instant>> {
        if (History.weekStart(date) in breakWeeks) return emptyList()
        val eligible = schedules.filter {
            it.enabled && date.dayOfWeek in it.days && (firstEvening == null || it.id != firstEvening.first || date == firstEvening.second)
        }
        val original = eligible.map { it to date.atTime(it.startTime).atZone(zone).toInstant() }
            .sortedWith(compareBy<Pair<Schedule, Instant>> { it.second }.thenBy { it.first.id })
            .distinctBy { it.second }
        val adjustment = dayOverrides[date] ?: return original
        val time = adjustment.startTime ?: return emptyList()
        val first = original.firstOrNull() ?: return emptyList()
        val moved = first.first to date.atTime(time).atZone(zone).toInstant()
        // Moving the first start leaves every other reminder in place, including earlier reminders
        // when the new time is late. Two starts at the same instant require only one reminder.
        return (listOf(moved) + original.drop(1)).sortedBy { it.second }.distinctBy { it.second }
    }

    /** The next scheduled fast start strictly after [now], across all enabled schedules. */
    fun nextStart(
        schedules: List<Schedule>,
        zone: ZoneId,
        now: Instant,
        breakWeeks: Set<LocalDate> = emptySet(),
        dayOverrides: Map<LocalDate, DayOverride> = emptyMap(),
        firstEvening: Pair<Long, LocalDate>? = null,
    ): Pair<Schedule, Instant>? {
        val hasRecurrence = schedules.any { it.enabled && it.days.isNotEmpty() && it.id != firstEvening?.first }
        if (!hasRecurrence) {
            val date = firstEvening?.second ?: return null
            return startsOn(schedules, date, zone, breakWeeks, dayOverrides, firstEvening).firstOrNull { it.second > now }
        }
        var day = now.atZone(zone).toLocalDate()
        // A nonempty weekly routine always resumes after the finite set of pauses and one-off edits.
        while (true) {
            val next = startsOn(schedules, day, zone, breakWeeks, dayOverrides, firstEvening).firstOrNull { it.second > now }
            if (next != null) return next
            day = day.plusDays(1)
        }
    }

    /** Whether [day] is a planned fasting day. With no enabled schedule, every day is planned. */
    fun isPlannedDay(schedules: List<Schedule>, day: LocalDate): Boolean {
        val enabled = schedules.filter { it.enabled && it.days.isNotEmpty() }
        return enabled.isEmpty() || enabled.any { day.dayOfWeek in it.days }
    }
}
