package com.fastingshift.domain.schedule

import com.fastingshift.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Weekly schedules for 5:2, ADF and custom fasting days (PRD F2). */
object ScheduleCalculator {

    /** The next scheduled fast start strictly after [now], across all enabled schedules. */
    fun nextStart(
        schedules: List<Schedule>,
        zone: ZoneId,
        now: Instant,
        breakWeeks: Set<LocalDate> = emptySet(),
    ): Pair<Schedule, Instant>? {
        val today = now.atZone(zone).toLocalDate()
        return schedules.asSequence()
            .filter { it.enabled && it.days.isNotEmpty() }
            .mapNotNull { s ->
                s.days.minOf { weekday ->
                    var day = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(weekday))
                    if (!day.atTime(s.startTime).atZone(zone).toInstant().isAfter(now)) day = day.plusWeeks(1)
                    while (com.fastingshift.domain.history.History.weekStart(day) in breakWeeks) day = day.plusWeeks(1)
                    day.atTime(s.startTime).atZone(zone).toInstant()
                }.let { s to it }
            }
            .minByOrNull { it.second }
    }

    /** Whether [day] is a planned fasting day. With no enabled schedule, every day is planned. */
    fun isPlannedDay(schedules: List<Schedule>, day: LocalDate): Boolean {
        val enabled = schedules.filter { it.enabled && it.days.isNotEmpty() }
        return enabled.isEmpty() || enabled.any { day.dayOfWeek in it.days }
    }
}
