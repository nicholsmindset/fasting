package com.fastingshift.domain.billing

import java.time.Duration
import java.time.Instant

enum class PlanType { FREE, MONTHLY, ANNUAL, LIFETIME }

data class Entitlement(
    val plan: PlanType,
    val active: Boolean,
    val inTrial: Boolean = false,
    /** Next renewal or trial end; null for free and lifetime. */
    val expiresAt: Instant? = null,
    val willRenew: Boolean = false,
    /** Unlocked by a book code rather than a purchase. */
    val fromBookCode: Boolean = false,
) {
    val isShiftPlus: Boolean get() = active && plan != PlanType.FREE

    companion object {
        val FREE = Entitlement(PlanType.FREE, active = true)
    }
}

enum class BillingReminderKind { TRIAL_ENDING, ANNUAL_RENEWAL }

data class BillingReminder(val kind: BillingReminderKind, val at: Instant)

/** Paywall rule 3: remind 3 days before a trial ends and 7 days before an annual renewal. */
object BillingReminders {
    val TRIAL_NOTICE: Duration = Duration.ofDays(3)
    val ANNUAL_NOTICE: Duration = Duration.ofDays(7)

    fun plan(entitlement: Entitlement, now: Instant): BillingReminder? {
        val expires = entitlement.expiresAt ?: return null
        if (!entitlement.active || !entitlement.willRenew) return null
        val reminder = when {
            entitlement.inTrial -> BillingReminder(BillingReminderKind.TRIAL_ENDING, expires.minus(TRIAL_NOTICE))
            entitlement.plan == PlanType.ANNUAL -> BillingReminder(BillingReminderKind.ANNUAL_RENEWAL, expires.minus(ANNUAL_NOTICE))
            else -> return null
        }
        // If we are already inside the notice window, remind now rather than never.
        return if (reminder.at.isAfter(now)) reminder else if (expires.isAfter(now)) reminder.copy(at = now) else null
    }
}

/** Features and whether they are ever paywalled. Paywall rule 5. */
enum class Feature(val requiresShiftPlus: Boolean) {
    TIMER(false), ALL_PROTOCOLS(false), WIDGETS(false), HISTORY(false), EXPORT(false),
    SAFETY_LAYER(false), LOGGING(false), HEALTH_CONNECT(false), BODY_TIMELINE(false),
    GUIDED_PROGRAM(true), INSIGHTS(true),
    ;

    fun isAvailable(entitlement: Entitlement): Boolean = !requiresShiftPlus || entitlement.isShiftPlus
}
