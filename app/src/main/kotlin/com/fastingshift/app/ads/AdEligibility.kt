package com.fastingshift.app.ads

import com.fastingshift.domain.billing.Entitlement

/** The order matters: consent can contact Google and must only run for verified free users. */
internal suspend fun canRequestSettingsAds(
    loadedEntitlement: Entitlement?,
    confirmFree: suspend () -> Boolean,
    gatherConsent: suspend () -> Boolean,
): Boolean = loadedEntitlement == Entitlement.FREE && confirmFree() && gatherConsent()
