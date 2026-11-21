package com.fastingshift.domain.coaching

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Duration
import java.time.Instant

/**
 * First-week coaching tips. Gentle, practical, never about pushing longer: the goal is a
 * comfortable first few fasts (PRD activation goal). Each tip has a moment in the fast.
 */
enum class CoachingTip {
    /** Early on: hunger comes in waves and usually passes. */
    HUNGER_WAVES,

    /** Drink water; tea and black coffee are fine. */
    HYDRATE,

    /** Feeling flat or headachy: water and a pinch of salt often help; stop if unwell. */
    ELECTROLYTES,

    /** Close to the goal: plan a calm first meal. */
    BREAK_GENTLY,
}

object CoachingPlanner {
    /** Coaching runs for the user's first few fasts within the first two weeks after onboarding. */
    const val COACHED_FASTS = 3
    val COACHING_PERIOD: Duration = Duration.ofDays(14)

    fun isCoaching(fastsStartedBefore: Int, onboardedAt: Instant?, now: Instant): Boolean =
        fastsStartedBefore < COACHED_FASTS && (onboardedAt == null || Duration.between(onboardedAt, now) <= COACHING_PERIOD)

    /** When each tip is due during [fast], clipped to the fast's planned length. */
    fun schedule(fast: Fast): List<Pair<CoachingTip, Instant>> {
        val planned = fast.plannedDuration
        val moments = listOf(
            CoachingTip.HUNGER_WAVES to Duration.ofHours(3),
            CoachingTip.HYDRATE to Duration.ofHours(6),
            CoachingTip.ELECTROLYTES to Duration.ofHours(10),
            CoachingTip.BREAK_GENTLY to planned.minus(Duration.ofMinutes(45)),
        )
        return moments.filter { (_, offset) -> offset > Duration.ZERO && offset < planned }
            .map { (tip, offset) -> tip to fast.startAt.plus(offset) }
            .sortedBy { it.second }
    }

    /** The tip to show now under the timer: the latest one whose moment has passed. */
    fun currentTip(fast: Fast, now: Instant): CoachingTip? =
        schedule(fast).lastOrNull { !it.second.isAfter(now) }?.first

    /** Notification reminders for tips still ahead. */
    fun reminders(fast: Fast, now: Instant): List<Reminder> =
        schedule(fast).filter { it.second.isAfter(now) }
            .map { (tip, at) -> Reminder(ReminderKind.COACHING, at, fast.id, tip.ordinal) }
}
