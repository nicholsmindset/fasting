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
)

/**
 * Pure planning of reminders (PRD F3, F7). The Android layer only executes the plan, which
 * keeps the logic testable and lets every reschedule trigger (boot, time change, time-zone
 * change, permission change) call the same code.
 */
object ReminderPlanner {

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

        settings.forgotToEndAfter?.let {
            planned += Reminder(ReminderKind.FORGOT_TO_END, fast.targetEndAt.plus(it), fast.id)
        }

        // Safety check-ins are not a setting: they always run for fasts of 24h or more, and
        // keep running past the planned end until the fast is ended.
        if (FastPolicy.requiresCheckIns(fast.plannedDuration)) {
            val interval = FastPolicy.CHECK_IN_INTERVAL
            val horizon = fast.targetEndAt.plus(settings.forgotToEndAfter ?: Duration.ZERO)
            var i = 1
            var at = fast.startAt.plus(interval)
            while (!at.isAfter(horizon) && i < Reminder.MAX_PER_KIND) {
                planned += Reminder(ReminderKind.SYMPTOM_CHECK_IN, at, fast.id, i - 1)
                i++
                at = fast.startAt.plus(interval.multipliedBy(i.toLong()))
            }
        }

        return planned.filter { it.triggerAt.isAfter(now) }.sortedBy { it.triggerAt }
    }

    /**
     * After a window-protocol fast ends, the next fast is due when the eating window closes.
     */
    fun planNextStart(lastFast: Fast, eatHours: Int?, settings: ReminderSettings, now: Instant): Reminder? {
        val ended = lastFast.endedAt ?: return null
        if (!settings.fastStartEnabled || eatHours == null || eatHours <= 0) return null
        val at = ended.plus(Duration.ofHours(eatHours.toLong()))
        return if (at.isAfter(now)) Reminder(ReminderKind.FAST_START, at, lastFast.id) else null
    }
}
