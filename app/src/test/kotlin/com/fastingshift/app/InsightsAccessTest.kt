package com.fastingshift.app

import com.fastingshift.app.ui.plus.InsightsState
import com.fastingshift.app.ui.plus.insightStates
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class InsightsAccessTest {
    @Test fun installedVariantGivesItsExpectedAccessToAFreeTester() = runBlocking {
        val internalReviewBuild = BuildConfig.BUILD_TYPE == "playInternalReview"
        assertEquals(internalReviewBuild, BuildConfig.INTERNAL_REVIEW_INSIGHTS)
        if (internalReviewBuild) {
            assertEquals("", BuildConfig.REVENUECAT_API_KEY)
            assertEquals(false, BuildConfig.ADS_ENABLED)
        }
        val result = withTimeout(10_000) {
            insightStates(
                entitlements = flowOf(Entitlement.FREE),
                settings = flowOf(AppSettings(hideWeight = true)),
                fasts = { flowOf(emptyList()) },
                weights = { error("Hidden weight should never be queried") },
                clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                internalReview = BuildConfig.INTERNAL_REVIEW_INSIGHTS,
            ).first()
        }
        assertEquals(internalReviewBuild, result is InsightsState.Personal)
        assertEquals(!internalReviewBuild, result is InsightsState.Example)
    }

    @Test fun freePreviewNeverSubscribesToPersonalRecordsAndHideWeightSkipsTheWeightQuery() = runBlocking {
        withTimeout(10_000) {
            val access = MutableStateFlow(Entitlement.FREE)
            val settings = MutableStateFlow(AppSettings(hideWeight = true))
            var personalReads = 0
            var weightReads = 0
            val time = Instant.parse("2026-11-24T12:00:00Z")
            val states = Channel<InsightsState>(Channel.UNLIMITED)
            val collect = launch {
                insightStates(access, settings,
                    fasts = { flow { personalReads++; emit(emptyList<Fast>()) } },
                    weights = { flow { weightReads++; emit(listOf(WeightLog(at = time, value = 75.0, unit = WeightUnit.KG))) } },
                    clock = Clock.fixed(time, ZoneOffset.UTC)).collect { states.send(it) }
            }
            assertEquals(InsightsState.Example, states.receive())
            assertEquals(0, personalReads)
            assertEquals(0, weightReads)
            access.value = Entitlement(PlanType.ANNUAL, active = true)
            val personal = states.receive() as InsightsState.Personal
            assertEquals(4, personal.review.size)
            assertNull(personal.weight)
            assertEquals(0, weightReads)
            settings.value = settings.value.copy(hideWeight = false)
            val shown = states.receive() as InsightsState.Personal
            assertEquals(75.0, shown.weight!!.single().averageWeightKg)
            access.value = Entitlement(PlanType.ANNUAL, active = false)
            assertEquals(InsightsState.Example, states.receive())
            assertEquals(1, weightReads)
            collect.cancelAndJoin()
        }
    }
}
