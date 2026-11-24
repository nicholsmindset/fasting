package com.fastingshift.domain.history

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.streak.StreakCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Period { WEEK, MONTH, YEAR }

/** Local-date range, end exclusive. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    operator fun contains(day: LocalDate) = !day.isBefore(start) && day.isBefore(endExclusive)
    val days: Sequence<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(endExclusive) }
}

data class PeriodSummary(
    val range: DateRange,
    /** Fasting time inside the period; fasts crossing its edges are clipped. */
    val totalFasting: Duration,
    /** Fasts that started in the period. */
    val fasts: Int,
    val completed: Int,
    /** Average length of ended fasts that started in the period. */
    val average: Duration?,
    val longest: Duration?,
    val plannedDays: Int,
    val completedDays: Int,
) {
    /** Share of planned days completed so far: the ring shows consistency, never length. */
    val consistency: Float get() = if (plannedDays == 0) 0f else completedDays.toFloat() / plannedDays
}

data class FastBar(val fast: Fast, val length: Duration, val completed: Boolean)

data class RecentFasts(val bars: List<FastBar>, val average: Duration?)

/** One day's slice of a fast. The original record is retained for editing. */
data class DayFast(val fast: Fast, val from: Instant, val to: Instant) {
    val duration: Duration get() = Duration.between(from, to)
}

data class DayDetail(val entries: List<DayFast>) {
    val totalFasting: Duration = entries.fold(Duration.ZERO) { total, entry -> total + entry.duration }
}

/**
 * One fast drawn in one calendar week row. Positions are in days from the row's Monday
 * (0.0–7.0), at time-of-day precision. A fast crossing rows is split; only its widest piece
 * carries the hours label, so a 72h fast shows "72h" once.
 */
data class WeekSegment(
    val fast: Fast,
    val startDay: Float,
    val endDay: Float,
    /** Total length of the whole fast (elapsed so far if active). */
    val totalLength: Duration,
    val showLabel: Boolean,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** Calendar and summary statistics for the History screen (PRD F5). */
object HistoryStats {

    fun dayDetail(fasts: List<Fast>, zone: ZoneId, day: LocalDate, now: Instant): DayDetail {
        if (day > now.atZone(zone).toLocalDate()) return DayDetail(emptyList())
        val midnight = day.atStartOfDay(zone).toInstant()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val entries = fasts.mapNotNull { fast ->
            val end = minOf(fast.endedAt ?: now, now)
            val from = maxOf(fast.startAt, midnight)
            val to = minOf(end, nextMidnight)
            // Immediate stops are legal records. Keep them editable on the date they started.
            val startsHereWithNoElapsedTime = fast.startAt == end && fast.startAt >= midnight && fast.startAt < nextMidnight
            if (from < to || startsHereWithNoElapsedTime) DayFast(fast, from, to) else null
        }.sortedWith(compareBy<DayFast> { it.fast.startAt }.thenBy { it.fast.id })
        return DayDetail(entries)
    }

    fun periodRange(period: Period, today: LocalDate): DateRange = when (period) {
        Period.WEEK -> History.weekStart(today).let { DateRange(it, it.plusWeeks(1)) }
        Period.MONTH -> today.withDayOfMonth(1).let { DateRange(it, it.plusMonths(1)) }
        Period.YEAR -> today.with(TemporalAdjusters.firstDayOfYear()).let { DateRange(it, it.plusYears(1)) }
    }

    fun periodSummary(
        fasts: List<Fast>,
        zone: ZoneId,
        period: Period,
        today: LocalDate,
        now: Instant,
        isPlannedDay: (LocalDate) -> Boolean = { true },
    ): PeriodSummary {
        val range = periodRange(period, today)
        val total = clippedTotal(fasts, zone, range, now)
        val started = fasts.filter { it.startAt.atZone(zone).toLocalDate() in range }
        val ended = started.mapNotNull { it.actualDuration }
        // Only planned days count, so consistency can never exceed 100%.
        val completedDays = StreakCalculator.completedDays(fasts, zone).filter { it in range && !it.isAfter(today) && isPlannedDay(it) }
        val plannedDays = range.days.filter { !it.isAfter(today) && isPlannedDay(it) }.count()
        return PeriodSummary(
            range = range,
            totalFasting = total,
            fasts = started.size,
            completed = started.count { it.status == FastStatus.COMPLETED },
            average = ended.takeIf { it.isNotEmpty() }?.let { l -> l.reduce(Duration::plus).dividedBy(l.size.toLong()) },
            longest = ended.maxOrNull(),
            plannedDays = plannedDays,
            completedDays = completedDays.size,
        )
    }

    fun monthTotal(fasts: List<Fast>, zone: ZoneId, month: YearMonth, now: Instant): Duration =
        clippedTotal(fasts, zone, DateRange(month.atDay(1), month.plusMonths(1).atDay(1)), now)

    /** The last [limit] ended fasts, oldest first, with their average. */
    fun recentFasts(fasts: List<Fast>, limit: Int = 10): RecentFasts {
        val bars = fasts.filter { !it.isActive }
            .sortedByDescending { it.startAt }
            .take(limit)
            .reversed()
            .map { FastBar(it, it.actualDuration!!, it.status == FastStatus.COMPLETED) }
        val avg = bars.takeIf { it.isNotEmpty() }?.let { b -> b.map { it.length }.reduce(Duration::plus).dividedBy(b.size.toLong()) }
        return RecentFasts(bars, avg)
    }

    /** Segments to draw in the week row starting on [weekStart] (a Monday). */
    fun weekSegments(fasts: List<Fast>, zone: ZoneId, weekStart: LocalDate, now: Instant): List<WeekSegment> {
        val rowStart = weekStart.atStartOfDay(zone).toInstant()
        val rowEnd = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant()
        return fasts.mapNotNull { f ->
            val end = f.endedAt ?: now
            if (!end.isAfter(rowStart) || !f.startAt.isBefore(rowEnd) || !end.isAfter(f.startAt)) return@mapNotNull null
            val from = maxOf(f.startAt, rowStart)
            val to = minOf(end, rowEnd)
            val start = position(from, weekStart, zone)
            val stop = if (to == rowEnd) 7f else position(to, weekStart, zone)
            WeekSegment(
                fast = f,
                startDay = start,
                endDay = stop,
                totalLength = Duration.between(f.startAt, end),
                showLabel = widestRow(f, end, zone) == weekStart,
                continuesBefore = f.startAt.isBefore(rowStart),
                continuesAfter = end.isAfter(rowEnd),
            )
        }.sortedBy { it.startDay }
    }

    /** Position of [t] in days from [weekStart]'s local midnight, honouring DST-length days. */
    fun position(t: Instant, weekStart: LocalDate, zone: ZoneId): Float {
        val day = t.atZone(zone).toLocalDate()
        val index = ChronoUnit.DAYS.between(weekStart, day)
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayLength = Duration.between(dayStart, day.plusDays(1).atStartOfDay(zone).toInstant()).seconds.toFloat()
        return index + Duration.between(dayStart, t).seconds / dayLength
    }

    /** The Monday of the week row holding the widest piece of a fast (first wins ties). */
    private fun widestRow(f: Fast, end: Instant, zone: ZoneId): LocalDate {
        var week = History.weekStart(f.startAt.atZone(zone).toLocalDate())
        var best = week
        var bestWidth = -1f
        while (week.atStartOfDay(zone).toInstant().isBefore(end)) {
            val rowStart = week.atStartOfDay(zone).toInstant()
            val rowEnd = week.plusWeeks(1).atStartOfDay(zone).toInstant()
            val width = position(minOf(end, rowEnd), week, zone) - position(maxOf(f.startAt, rowStart), week, zone)
            if (width > bestWidth) { bestWidth = width; best = week }
            week = week.plusWeeks(1)
        }
        return best
    }

    fun clippedTotal(fasts: List<Fast>, zone: ZoneId, range: DateRange, now: Instant): Duration {
        val from = range.start.atStartOfDay(zone).toInstant()
        val to = range.endExclusive.atStartOfDay(zone).toInstant()
        return fasts.fold(Duration.ZERO) { acc, f ->
            val s = maxOf(f.startAt, from)
            val e = minOf(f.endedAt ?: now, to)
            if (e.isAfter(s)) acc + Duration.between(s, e) else acc
        }
    }
}
