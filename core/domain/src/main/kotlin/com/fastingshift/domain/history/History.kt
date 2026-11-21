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
     * Fasting time per week (weeks start Monday), with fasts clipped at week edges so totals
     * match the History summary. Fasts and completions are counted in the week they started.
     * Active fasts count up to [now]. Ordered newest week first.
     */
    fun weeklyTotals(fasts: List<Fast>, zone: ZoneId, now: Instant): List<WeekTotal> {
        val weeks = fasts.flatMap { f ->
            val first = weekStart(f.startAt.atZone(zone).toLocalDate())
            val last = weekStart((f.endedAt ?: now).atZone(zone).toLocalDate())
            generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
        }.toSet()
        return weeks.map { week ->
            val range = DateRange(week, week.plusWeeks(1))
            val started = fasts.filter { weekStart(it.startAt.atZone(zone).toLocalDate()) == week }
            WeekTotal(
                weekStart = week,
                fastingTime = HistoryStats.clippedTotal(fasts, zone, range, now),
                fasts = started.size,
                completed = started.count { it.status == com.fastingshift.domain.model.FastStatus.COMPLETED },
            )
        }.sortedByDescending { it.weekStart }
    }

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

}
