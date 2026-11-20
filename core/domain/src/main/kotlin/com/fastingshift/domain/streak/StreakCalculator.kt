package com.fastingshift.domain.streak

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

data class StreakResult(val current: Int, val longest: Int)

/**
 * Consistency streaks (PRD F5, SR-3, SR-6).
 *
 * A streak counts days on which the user completed their planned fast. It never looks at
 * fast length, so a 16h day and a 72h day count the same. A day is attributed to the local
 * date on which the fast ended.
 *
 * - Days that are not planned fasting days (e.g. non-fast days in 5:2) neither add to nor
 *   break a streak.
 * - A frozen day bridges a gap without adding to the count. One freeze per ISO week.
 * - Today not being done yet never breaks the current streak.
 */
object StreakCalculator {
    const val FREEZES_PER_WEEK = 1

    fun calculate(
        fasts: List<Fast>,
        zone: ZoneId,
        today: LocalDate,
        frozenDays: Set<LocalDate> = emptySet(),
        isPlannedDay: (LocalDate) -> Boolean = { true },
    ): StreakResult {
        val completedDays = completedDays(fasts, zone)
        if (completedDays.isEmpty()) return StreakResult(0, 0)

        fun bridges(day: LocalDate) = day in frozenDays || !isPlannedDay(day)

        // Current streak: walk back from today. An unfinished today is skipped, not a break.
        var current = 0
        var day = today
        if (day !in completedDays) day = day.minusDays(1)
        val earliest = completedDays.min()
        while (!day.isBefore(earliest)) {
            when {
                day in completedDays -> current++
                bridges(day) -> Unit
                else -> break
            }
            day = day.minusDays(1)
        }

        // Longest streak: walk forward over the whole history.
        var longest = 0
        var run = 0
        day = earliest
        val last = maxOf(completedDays.max(), today)
        while (!day.isAfter(last)) {
            when {
                day in completedDays -> { run++; longest = maxOf(longest, run) }
                bridges(day) || day == today -> Unit
                else -> run = 0
            }
            day = day.plusDays(1)
        }
        return StreakResult(current, maxOf(longest, current))
    }

    fun completedDays(fasts: List<Fast>, zone: ZoneId): Set<LocalDate> =
        fasts.asSequence()
            .filter { it.status == FastStatus.COMPLETED }
            .mapNotNull { it.endedAt?.atZone(zone)?.toLocalDate() }
            .toSet()

    /** Whether a freeze can be applied to [day] given freezes already used. */
    fun canFreeze(day: LocalDate, frozenDays: Set<LocalDate>): Boolean {
        if (day in frozenDays) return false
        val usedThisWeek = frozenDays.count { it.isoWeek() == day.isoWeek() }
        return usedThisWeek < FREEZES_PER_WEEK
    }

    private fun LocalDate.isoWeek(): Pair<Int, Int> =
        get(IsoFields.WEEK_BASED_YEAR) to get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}
