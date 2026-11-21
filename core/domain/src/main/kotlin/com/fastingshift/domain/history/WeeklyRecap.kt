package com.fastingshift.domain.history

import com.fastingshift.domain.hydration.WaterGoal
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.streak.StreakCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A calm look back at one week (Monday–Sunday). Free; no rewards for longer fasts. */
data class WeeklyRecap(
    val weekStart: LocalDate,
    val fastingTime: Duration,
    val fastsStarted: Int,
    val completedToPlan: Int,
    val plannedDays: Int,
    val completedDays: Int,
    /** Days the water goal was met / days with any water logged. */
    val waterGoalDays: Int,
    val waterLoggedDays: Int,
) {
    val isEmpty: Boolean get() = fastsStarted == 0 && waterLoggedDays == 0
}

object WeeklyRecaps {
    val RECAP_TIME: LocalTime = LocalTime.of(18, 0)

    fun compute(
        fasts: List<Fast>,
        water: List<WaterLog>,
        zone: ZoneId,
        weekStart: LocalDate,
        now: Instant,
        waterGoalMl: Int,
        isPlannedDay: (LocalDate) -> Boolean = { true },
    ): WeeklyRecap {
        val range = DateRange(weekStart, weekStart.plusWeeks(1))
        val today = now.atZone(zone).toLocalDate()
        val started = fasts.filter { it.startAt.atZone(zone).toLocalDate() in range }
        val days = range.days.filter { !it.isAfter(today) }.toList()
        val completed = StreakCalculator.completedDays(fasts, zone)
        val waterByDay = WaterGoal.totalsByDay(water, zone).filterKeys { it in range }
        return WeeklyRecap(
            weekStart = weekStart,
            fastingTime = HistoryStats.clippedTotal(fasts, zone, range, now),
            fastsStarted = started.size,
            completedToPlan = started.count { it.status == FastStatus.COMPLETED },
            plannedDays = days.count(isPlannedDay),
            completedDays = days.count { it in completed && isPlannedDay(it) },
            waterGoalDays = waterByDay.count { it.value >= waterGoalMl },
            waterLoggedDays = waterByDay.count { it.value > 0 },
        )
    }

    /** The next Sunday 18:00 local after [now] (the recap notification time). */
    fun nextRecapAt(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        var day = local.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        var at = day.atTime(RECAP_TIME).atZone(zone).toInstant()
        if (!at.isAfter(now)) {
            day = day.plusWeeks(1)
            at = day.atTime(RECAP_TIME).atZone(zone).toInstant()
        }
        return at
    }

    /** The week a recap shown on [day] summarises: the week containing it. */
    fun weekFor(day: LocalDate): LocalDate = History.weekStart(day)
}
