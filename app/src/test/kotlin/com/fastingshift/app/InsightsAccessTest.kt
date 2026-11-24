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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class InsightsAccessTest {
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
