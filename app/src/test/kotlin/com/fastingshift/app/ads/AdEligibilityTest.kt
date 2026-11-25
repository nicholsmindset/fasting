package com.fastingshift.app.ads

import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdEligibilityTest {
    @Test fun unknownAndPaidUsersNeverContactAds() = runTest {
        val paid = listOf(PlanType.MONTHLY, PlanType.ANNUAL, PlanType.LIFETIME).map {
            Entitlement(it, active = true)
        } + Entitlement(PlanType.ANNUAL, active = true, inTrial = true)
        for (entitlement in paid + listOf(null)) {
            assertFalse(canRequestSettingsAds(entitlement, { error("Unexpected lookup") }, { error("Unexpected consent") }))
        }
    }

    @Test fun cachedFreeWithoutFreshConfirmationDoesNotStartConsent() = runTest {
        assertFalse(canRequestSettingsAds(Entitlement.FREE, { false }, { error("Unexpected consent") }))
    }

    @Test fun onlyConfirmedFreeWithConsentCanRequestAds() = runTest {
        assertFalse(canRequestSettingsAds(Entitlement.FREE, { true }, { false }))
        assertTrue(canRequestSettingsAds(Entitlement.FREE, { true }, { true }))
    }
}
