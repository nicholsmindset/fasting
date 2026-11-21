package com.fastingshift.domain.insights

import com.fastingshift.domain.history.History
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

data class WeeklyConsistency(val weekStart: LocalDate, val completedDays: Int, val plannedDays: Int) {
    val ratio: Float get() = if (plannedDays == 0) 0f else completedDays.toFloat() / plannedDays
}

data class WeightVsFasting(val weekStart: LocalDate, val averageWeightKg: Double?, val fastingHours: Double)

/**
 * Shift+ insights (PRD F10). Descriptive only: no targets, no rewards for longer fasts.
 */
object Insights {

    /** Completed planned days per week for the last [weeks] weeks, oldest first. */
    fun weeklyConsistency(
        fasts: List<Fast>,
        zone: ZoneId,
        today: LocalDate,
        weeks: Int = 8,
        isPlannedDay: (LocalDate) -> Boolean = { true },
    ): List<WeeklyConsistency> {
        val completed = com.fastingshift.domain.streak.StreakCalculator.completedDays(fasts, zone)
        val thisWeek = History.weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val days = (0L until 7L).map { start.plusDays(it) }.filter { !it.isAfter(today) }
            WeeklyConsistency(start, days.count { it in completed && isPlannedDay(it) }, days.count(isPlannedDay))
        }
    }

    /** Average completed or ended fast length by the weekday the fast started. */
    fun averageByWeekday(fasts: List<Fast>, zone: ZoneId): Map<DayOfWeek, Duration> =
        fasts.filter { !it.isActive }
            .groupBy { it.startAt.atZone(zone).dayOfWeek }
            .mapValues { (_, list) -> list.map { it.actualDuration!! }.reduce(Duration::plus).dividedBy(list.size.toLong()) }

    /** Weekly average weight (kg) next to weekly fasting hours, oldest first. */
    fun weightVsFasting(fasts: List<Fast>, weights: List<WeightLog>, zone: ZoneId, now: java.time.Instant): List<WeightVsFasting> {
        val hours = History.weeklyTotals(fasts, zone, now).associate { it.weekStart to it.fastingTime.toMinutes() / 60.0 }
        val kg = weights.groupBy { History.weekStart(it.at.atZone(zone).toLocalDate()) }
            .mapValues { (_, l) -> l.map { toKg(it.value, it.unit) }.average() }
        return (hours.keys + kg.keys).sorted().map { WeightVsFasting(it, kg[it], hours[it] ?: 0.0) }
    }

    fun toKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * 0.45359237
    }
}
