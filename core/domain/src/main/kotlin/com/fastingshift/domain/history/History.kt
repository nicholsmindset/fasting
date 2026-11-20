package com.fastingshift.domain.history

import com.fastingshift.domain.model.Fast
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeekTotal(val weekStart: LocalDate, val fastingTime: Duration, val fasts: Int, val completed: Int)

/** History views (PRD F5). Fasts belong to the local date on which they started. */
object History {

    fun byDay(fasts: List<Fast>, zone: ZoneId, month: YearMonth): Map<LocalDate, List<Fast>> =
        fasts.filter { YearMonth.from(it.startAt.atZone(zone)) == month }
            .groupBy { it.startAt.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.sortedBy(Fast::startAt) }

    /**
     * Fasting time per week (weeks start Monday). Active fasts count up to [now].
     * Ordered newest week first.
     */
    fun weeklyTotals(fasts: List<Fast>, zone: ZoneId, now: Instant): List<WeekTotal> =
        fasts.groupBy { weekStart(it.startAt.atZone(zone).toLocalDate()) }
            .map { (week, list) ->
                WeekTotal(
                    weekStart = week,
                    fastingTime = list.fold(Duration.ZERO) { acc, f -> acc + Duration.between(f.startAt, f.endedAt ?: now).coerceNonNegative() },
                    fasts = list.size,
                    completed = list.count { it.status == com.fastingshift.domain.model.FastStatus.COMPLETED },
                )
            }
            .sortedByDescending { it.weekStart }

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun Duration.coerceNonNegative() = if (isNegative) Duration.ZERO else this
}
