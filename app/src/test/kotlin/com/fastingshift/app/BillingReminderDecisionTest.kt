package com.fastingshift.app

import com.fastingshift.app.billing.BillingReminderAction
import com.fastingshift.app.billing.BillingReminderDecision
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BillingReminderDecisionTest {
    private val now = Instant.parse("2026-11-21T10:00:00Z")
    private fun trial(expires: Instant) =
        Entitlement(PlanType.ANNUAL, active = true, inTrial = true, expiresAt = expires, willRenew = true)

    @Test fun notLoadedKeepsExistingAlarm() {
        assertEquals(BillingReminderAction.Keep, BillingReminderDecision.decide(null, null, now))
    }

    @Test fun freeCancels() {
        assertEquals(BillingReminderAction.Cancel, BillingReminderDecision.decide(Entitlement.FREE, null, now))
    }

    @Test fun schedulesThreeDaysBeforeTrialEnds() {
        val expires = now.plus(Duration.ofDays(7))
        val action = BillingReminderDecision.decide(trial(expires), null, now)
        assertEquals(BillingReminderAction.Schedule(expires.minus(Duration.ofDays(3)), true, expires.toEpochMilli()), action)
    }

    @Test fun insideWindowRemindsNowOnlyOnce() {
        val expires = now.plus(Duration.ofDays(1))
        val first = BillingReminderDecision.decide(trial(expires), null, now)
        assertEquals(BillingReminderAction.Schedule(now, true, expires.toEpochMilli()), first)
        // After the notification was shown, every later start (same expiry) cancels instead.
        assertEquals(BillingReminderAction.Cancel, BillingReminderDecision.decide(trial(expires), expires.toEpochMilli(), now.plusSeconds(60)))
    }

    @Test fun newPeriodRemindsAgain() {
        val old = now.minus(Duration.ofDays(300))
        val expires = now.plus(Duration.ofDays(30))
        val annual = Entitlement(PlanType.ANNUAL, active = true, expiresAt = expires, willRenew = true)
        assertEquals(
            BillingReminderAction.Schedule(expires.minus(Duration.ofDays(7)), false, expires.toEpochMilli()),
            BillingReminderDecision.decide(annual, old.toEpochMilli(), now),
        )
    }

    @Test fun shouldNotifyOncePerExpiry() {
        assertTrue(BillingReminderDecision.shouldNotify(1000, null))
        assertFalse(BillingReminderDecision.shouldNotify(1000, 1000))
        assertFalse(BillingReminderDecision.shouldNotify(0, null))
    }
}
