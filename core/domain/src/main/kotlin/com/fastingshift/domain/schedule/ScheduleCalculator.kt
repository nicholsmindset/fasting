package com.fastingshift.domain.schedule

import com.fastingshift.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Weekly schedules for 5:2, ADF and custom fasting days (PRD F2). */
object ScheduleCalculator {

    /** The next scheduled fast start strictly after [now], across all enabled schedules. */
    fun nextStart(schedules: List<Schedule>, zone: ZoneId, now: Instant): Pair<Schedule, Instant>? {
        val today = now.atZone(zone).toLocalDate()
        return schedules.asSequence()
            .filter { it.enabled && it.days.isNotEmpty() }
            .mapNotNull { s ->
                (0L..7L).asSequence()
                    .map { today.plusDays(it) }
                    .filter { it.dayOfWeek in s.days }
                    .map { it.atTime(s.startTime).atZone(zone).toInstant() }
                    .firstOrNull { it.isAfter(now) }
                    ?.let { s to it }
            }
            .minByOrNull { it.second }
    }

    /** Whether [day] is a planned fasting day. With no enabled schedule, every day is planned. */
    fun isPlannedDay(schedules: List<Schedule>, day: LocalDate): Boolean {
        val enabled = schedules.filter { it.enabled && it.days.isNotEmpty() }
        return enabled.isEmpty() || enabled.any { day.dayOfWeek in it.days }
    }
}
