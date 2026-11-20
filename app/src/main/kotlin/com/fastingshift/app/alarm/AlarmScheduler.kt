package com.fastingshift.app.alarm

import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Duration

/**
 * Applies a reminder plan: schedules every planned reminder and cancels every other alarm
 * this app could have set. Idempotent, so it is safe to call on every trigger.
 */
class AlarmScheduler(private val gateway: AlarmGateway) {

    /** Returns true if exact alarms were used. */
    fun apply(plan: List<Reminder>): Boolean {
        val wanted = plan.associateBy { it.requestCode }
        for (kind in ReminderKind.entries) {
            for (index in 0 until Reminder.MAX_PER_KIND) {
                val code = kind.ordinal * Reminder.MAX_PER_KIND + index
                if (code !in wanted) gateway.cancel(code)
            }
        }
        val exact = gateway.canScheduleExact()
        for (reminder in wanted.values) {
            if (exact) gateway.setExact(reminder) else gateway.setWindowed(reminder, FALLBACK_WINDOW)
        }
        return exact
    }

    companion object {
        /** PRD F3: without exact alarms, alerts must arrive within 15 minutes. */
        val FALLBACK_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
