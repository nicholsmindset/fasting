package com.fastingshift.domain.history

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.streak.StreakCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.math.roundToInt

data class AnnualRecap(
    val year: Int, val through: LocalDate, val completed: Int, val longestSession: Duration,
    val topWindow: String?, val personalPlan: Boolean, val repeatedWindow: Boolean, val bestPlanDays: Int, val novemberTimerPercent: Int,
    val recordedDays: Int,
)

data class SundayRecap(
    val weekStart: LocalDate, val completed: Int, val average: Duration?,
    val previousAverage: Duration?,
)

object FastingWrapped {
    /** On December 1 the new year-to-date story becomes available. */
    fun latestYear(today: LocalDate) = if (today.month == Month.DECEMBER) today.year else today.year - 1

    private fun endedBy(fast: Fast, now: Instant) = fast.endedAt?.let { it <= now && it > fast.startAt } == true

    fun hasStory(fasts: List<Fast>, zone: ZoneId, now: Instant): Boolean {
        val year = latestYear(now.atZone(zone).toLocalDate())
        val cutoff = minOf(now, LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant())
        return fasts.any { endedBy(it, cutoff) && it.startAt.atZone(zone).year == year }
    }

    /** Sessions and streaks belong to their local start date, matching History. */
    fun annual(fasts: List<Fast>, zone: ZoneId, now: Instant, frozen: Set<LocalDate>, planned: (LocalDate) -> Boolean): AnnualRecap? {
        val today = now.atZone(zone).toLocalDate()
        val year = latestYear(today)
        val through = minOf(today, LocalDate.of(year, 12, 31))
        val cutoff = minOf(now, LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant())
        val inYear = fasts.filter { it.startAt.atZone(zone).year == year && it.startAt <= cutoff }
        val ended = inYear.filter { endedBy(it, cutoff) }
        if (ended.isEmpty()) return null
        val dates = ended.map { it.startAt.atZone(zone).toLocalDate() }.toSet()
        // Collapse custom / religious records into an unnamed personal-plan category.
        val personal = "private"
        val plans = ended.map { fast ->
            Presets.all.firstOrNull { fast.kind == FastKind.REGULAR && it.id == fast.protocolId }?.id ?: personal
        }.groupingBy { it }.eachCount()
        val top = plans.entries.filter { it.value == plans.values.maxOrNull() }.singleOrNull()
        val preset = Presets.all.firstOrNull { it.id == top?.key }
        val nov = LocalDate.of(year, 11, 1)
        val from = nov.atStartOfDay(zone).toInstant()
        val until = nov.plusMonths(1).atStartOfDay(zone).toInstant()
        // Clip and union intervals before dividing. Imported overlaps can never add twice.
        val intervals = fasts.mapNotNull { fast ->
            val start = maxOf(from, fast.startAt)
            val end = minOf(until, now, fast.endedAt ?: now)
            if (end > start) start to end else null
        }.sortedBy { it.first }
        var recorded = Duration.ZERO
        var cursor = from
        for ((start, end) in intervals) {
            if (end > cursor) {
                recorded = recorded.plus(Duration.between(maxOf(start, cursor), end))
                cursor = end
            }
        }
        val percent = (100.0 * recorded.toMillis() / Duration.between(from, until).toMillis()).roundToInt()
        val streakRows = inYear.filter { it.isActive || endedBy(it, cutoff) }
        return AnnualRecap(year, through, ended.count { it.status == FastStatus.COMPLETED },
            ended.maxOf { requireNotNull(it.actualDuration) }, preset?.name, top?.key == personal,
            preset?.type == com.fastingshift.domain.model.ProtocolType.WINDOW && (top?.value ?: 0) >= 2,
            StreakCalculator.calculate(streakRows, zone, through, frozen, planned).longest,
            percent, dates.size)
    }

    fun sunday(fasts: List<Fast>, zone: ZoneId, now: Instant): SundayRecap? {
        val today = now.atZone(zone).toLocalDate()
        if (today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return null
        val start = History.weekStart(today)
        val ended = fasts.filter { endedBy(it, now) }
        fun sessions(week: LocalDate) = ended.filter {
            val date = it.startAt.atZone(zone).toLocalDate()
            date >= week && date < week.plusWeeks(1)
        }
        fun average(rows: List<Fast>): Duration? = if (rows.isEmpty()) null else
            Duration.ofMillis(rows.map { requireNotNull(it.actualDuration).toMillis() }.average().toLong())
        val current = sessions(start)
        return SundayRecap(start, current.count { it.status == FastStatus.COMPLETED }, average(current),
            average(sessions(start.minusWeeks(1))))
    }
}
