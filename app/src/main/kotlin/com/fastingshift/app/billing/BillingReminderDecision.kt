package com.fastingshift.app.billing

import com.fastingshift.domain.billing.BillingReminderKind
import com.fastingshift.domain.billing.BillingReminders
import com.fastingshift.domain.billing.Entitlement
import java.time.Instant

/** What to do with the single billing-reminder alarm. */
sealed interface BillingReminderAction {
    /** Nothing is known yet (no customer info loaded): leave any existing alarm alone. */
    data object Keep : BillingReminderAction

    /** No reminder is due, or it was already shown for this period. */
    data object Cancel : BillingReminderAction

    data class Schedule(val at: Instant, val trial: Boolean, val expiresAtMillis: Long) : BillingReminderAction
}

/**
 * Paywall rule 3, made idempotent: a reminder is shown once per expiry date, no matter how
 * often the app restarts or the entitlement is re-published.
 */
object BillingReminderDecision {
    /**
     * @param entitlement the entitlement from real customer info, or null if none has loaded
     *   yet (the FREE placeholder must never cancel or re-arm anything).
     * @param remindedFor the expiry date (epoch millis) a reminder was already shown for.
     */
    fun decide(entitlement: Entitlement?, remindedFor: Long?, now: Instant): BillingReminderAction {
        if (entitlement == null) return BillingReminderAction.Keep
        val reminder = BillingReminders.plan(entitlement, now) ?: return BillingReminderAction.Cancel
        val expires = entitlement.expiresAt?.toEpochMilli() ?: return BillingReminderAction.Cancel
        if (remindedFor == expires) return BillingReminderAction.Cancel
        return BillingReminderAction.Schedule(reminder.at, reminder.kind == BillingReminderKind.TRIAL_ENDING, expires)
    }

    /** Whether a fired alarm for [expiresAtMillis] should still notify. */
    fun shouldNotify(expiresAtMillis: Long, remindedFor: Long?): Boolean = expiresAtMillis > 0 && expiresAtMillis != remindedFor
}
