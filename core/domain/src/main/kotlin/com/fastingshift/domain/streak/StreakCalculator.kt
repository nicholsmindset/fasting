package com.fastingshift.domain.streak

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import java.time.LocalDate
import java.time.ZoneId

data class StreakResult(val current: Int, val longest: Int)

/**
 * Consistency streaks (PRD F5, SR-3, SR-6).
 *
 * A streak counts days on which the user completed their planned fast. It never looks at
 * fast length, so a 16h day and a 72h day count the same. A fast counts for the local date
 * on which it **started**, which is the day it was planned for: a Monday 5:2 fast that ends
 * on Tuesday counts for Monday, and a 48h fast counts once.
 *
 * - Days that are not planned fasting days (e.g. non-fast days in 5:2) neither add to nor
 *   break a streak.
 * - A frozen day bridges a gap without adding to the count. One freeze in any 7 days, and
 *   only for a recently missed day, so old gaps can't be backfilled.
 * - Today not being done yet, or a fast still in progress, never breaks the current streak.
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

        val inProgress = fasts.filter { it.isActive }.map { it.startAt.atZone(zone).toLocalDate() }.toSet()
        fun bridges(day: LocalDate) = day in frozenDays || !isPlannedDay(day) || day in inProgress

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
            .map { it.startAt.atZone(zone).toLocalDate() }
            .toSet()

    /** How far back a missed day can still be frozen. */
    const val FREEZE_WINDOW_DAYS = 2L

    /**
     * Whether a freeze can be applied to [day]: it must be one of the last [FREEZE_WINDOW_DAYS]
     * days (not today), not already frozen, and no other freeze used in the last 7 days.
     */
    fun canFreeze(day: LocalDate, frozenDays: Set<LocalDate>, today: LocalDate): Boolean {
        if (day in frozenDays) return false
        if (!day.isBefore(today) || day.isBefore(today.minusDays(FREEZE_WINDOW_DAYS))) return false
        val recent = frozenDays.count { !it.isBefore(today.minusDays(6)) }
        return recent < FREEZES_PER_WEEK
    }
}
