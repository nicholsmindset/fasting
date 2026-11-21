package com.fastingshift.domain.reminders

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.FastPolicy
import java.time.Duration
import java.time.Instant

enum class ReminderKind {
    FAST_START,
    FAST_END,
    MILESTONE,
    FORGOT_TO_END,
    SYMPTOM_CHECK_IN,

    // Appended (never reorder: request codes derive from the ordinal).
    /** One hour before the goal (opt-in). */
    NEAR_GOAL,

    /** Drink-water nudges every few hours during fasts of 24h or more. */
    HYDRATION,

    /** "Your eating window closes in 30 minutes" (opt-in). */
    EATING_WINDOW_CLOSING,

    /** First-week coaching tips (index = tip ordinal). */
    COACHING,

    /** Sunday evening weekly recap. */
    WEEKLY_RECAP,

    /** Ramadan: suhoor ends soon (before Fajr). */
    SUHOOR,

    /** Ramadan: time for iftar (Maghrib). */
    IFTAR,
}

/**
 * One alarm to schedule. [requestCode] is stable for a given kind and index, so rescheduling
 * replaces rather than duplicates alarms. Only one fast is active at a time.
 */
data class Reminder(
    val kind: ReminderKind,
    val triggerAt: Instant,
    val fastId: Long,
    val index: Int = 0,
) {
    val requestCode: Int get() = kind.ordinal * MAX_PER_KIND + index

    companion object {
        const val MAX_PER_KIND = 100
    }
}

data class ReminderSettings(
    val fastEndEnabled: Boolean = true,
    /** Milestones measured from the start of a fast, e.g. 12h, 16h. */
    val milestones: List<Duration> = emptyList(),
    /** "Forgot to end your fast?" nudge, measured past the planned end. Null disables it. */
    val forgotToEndAfter: Duration? = Duration.ofHours(4),
    val fastStartEnabled: Boolean = true,
    /** "1 hour to your goal". */
    val nearGoalEnabled: Boolean = false,
    /** "Eating window closes in 30 minutes". */
    val windowClosingEnabled: Boolean = false,
    /** Water nudges every [HYDRATION_INTERVAL] during fasts of 24h or more. */
    val hydrationEnabled: Boolean = true,
)

/**
 * Pure planning of reminders (PRD F3, F7). The Android layer only executes the plan, which
 * keeps the logic testable and lets every reschedule trigger (boot, time change, time-zone
 * change, permission change) call the same code.
 */
object ReminderPlanner {
    /** How many future check-ins to keep scheduled beyond the planned end or now. */
    private const val CHECK_IN_LOOKAHEAD = 3L
    val NEAR_GOAL_BEFORE: Duration = Duration.ofHours(1)
    val HYDRATION_INTERVAL: Duration = Duration.ofHours(3)
    val WINDOW_CLOSING_BEFORE: Duration = Duration.ofMinutes(30)

    /** Reminders still in the future for an active fast. Returns nothing for an ended fast. */
    fun planForActiveFast(fast: Fast, settings: ReminderSettings, now: Instant): List<Reminder> {
        if (!fast.isActive) return emptyList()
        val planned = mutableListOf<Reminder>()

        if (settings.fastEndEnabled) {
            planned += Reminder(ReminderKind.FAST_END, fast.targetEndAt, fast.id)
        }

        settings.milestones
            .filter { it > Duration.ZERO && it < fast.plannedDuration }
            .distinct()
            .sorted()
            .take(Reminder.MAX_PER_KIND)
            .forEachIndexed { i, offset ->
                planned += Reminder(ReminderKind.MILESTONE, fast.startAt.plus(offset), fast.id, i)
            }

        if (settings.nearGoalEnabled && fast.plannedDuration > NEAR_GOAL_BEFORE.multipliedBy(2)) {
            planned += Reminder(ReminderKind.NEAR_GOAL, fast.targetEndAt.minus(NEAR_GOAL_BEFORE), fast.id)
        }

        // Hydration matters most on long fasts (F7 checklist): gentle nudges up to the goal.
        if (settings.hydrationEnabled && FastPolicy.requiresCheckIns(fast.plannedDuration)) {
            var k = 1L
            while (k < Reminder.MAX_PER_KIND) {
                val at = fast.startAt.plus(HYDRATION_INTERVAL.multipliedBy(k))
                if (!at.isBefore(fast.targetEndAt)) break
                planned += Reminder(ReminderKind.HYDRATION, at, fast.id, (k - 1).toInt())
                k++
            }
        }

        settings.forgotToEndAfter?.let {
            planned += Reminder(ReminderKind.FORGOT_TO_END, fast.targetEndAt.plus(it), fast.id)
        }

        // Safety check-ins are not a setting. They run every 8h during any fast of 24h or more:
        // from the start for planned extended fasts, and from the 24h mark for shorter fasts
        // that keep going. They continue past the planned end for as long as the fast runs;
        // the plan always holds the next few, and is rebuilt each time one fires.
        val interval = FastPolicy.CHECK_IN_INTERVAL
        val firstAt = if (FastPolicy.requiresCheckIns(fast.plannedDuration)) interval else FastPolicy.EXTENDED_THRESHOLD
        val horizon = maxOf(fast.targetEndAt.plus(settings.forgotToEndAfter ?: Duration.ZERO), now)
            .plus(interval.multipliedBy(CHECK_IN_LOOKAHEAD))
        var k = firstAt.dividedBy(interval)
        while (k < Reminder.MAX_PER_KIND) {
            val at = fast.startAt.plus(interval.multipliedBy(k))
            if (at.isAfter(horizon)) break
            planned += Reminder(ReminderKind.SYMPTOM_CHECK_IN, at, fast.id, (k - 1).toInt())
            k++
        }

        return planned.filter { it.triggerAt.isAfter(now) }.sortedBy { it.triggerAt }
    }

    /**
     * After a window-protocol fast ends, the next fast is due when the eating window closes.
     */
    /** "Your eating window closes in 30 minutes", if enabled and still ahead. */
    fun planWindowClosing(lastFast: Fast, eatHours: Int?, settings: ReminderSettings, now: Instant): Reminder? {
        if (!settings.windowClosingEnabled) return null
        val start = planNextStart(lastFast, eatHours, settings.copy(fastStartEnabled = true), now) ?: return null
        val at = start.triggerAt.minus(WINDOW_CLOSING_BEFORE)
        return if (at.isAfter(now)) Reminder(ReminderKind.EATING_WINDOW_CLOSING, at, lastFast.id) else null
    }

    fun planNextStart(lastFast: Fast, eatHours: Int?, settings: ReminderSettings, now: Instant): Reminder? {
        val ended = lastFast.endedAt ?: return null
        if (!settings.fastStartEnabled || eatHours == null || eatHours <= 0) return null
        val at = ended.plus(Duration.ofHours(eatHours.toLong()))
        return if (at.isAfter(now)) Reminder(ReminderKind.FAST_START, at, lastFast.id) else null
    }
}
