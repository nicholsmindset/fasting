package com.fastingshift.domain

import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.service.EditResult
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.StartResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class FastServiceTest {
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")
    private val clock = MutableClock(t0)
    private val fasts = FakeFastRepository()
    private val screening = FakeScreeningRepository()
    private val service = FastService(fasts, screening, clock)

    private fun screened(gate: GateResult, ack: Boolean = false) {
        val answers = ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 75.0)
        screening.record = ScreeningRecord(t0, answers, gate, ack)
    }

    @Test fun `one tap starts a 16h fast and a second start is refused`() = runTest {
        val started = assertIs<StartResult.Started>(service.start(Presets.W16_8))
        assertEquals(t0.plus(hours(16)), started.fast.targetEndAt)
        assertIs<StartResult.AlreadyActive>(service.start(Presets.W18_6))
    }

    @Test fun `extended fast needs checklist confirmation`() = runTest {
        screened(GateResult.OK)
        val needs = assertIs<StartResult.NeedsConfirmation>(service.start(Presets.D36))
        assertEquals(setOf(StartRequirement.EXTENDED_FAST_CHECKLIST, StartRequirement.MEDICAL_DISCLAIMER), needs.requirements)
        assertIs<StartResult.Started>(service.start(Presets.D36, confirmed = needs.requirements))
    }

    @Test fun `unscreened users cannot start extended fasts`() = runTest {
        val r = assertIs<StartResult.Blocked>(service.start(Presets.D48))
        assertEquals(BlockReason.EXCEEDS_GATED_LIMIT, r.decision.reason)
    }

    @Test fun `hard block is education only`() = runTest {
        screened(GateResult.BLOCK)
        assertEquals(BlockReason.EDUCATION_ONLY, assertIs<StartResult.Blocked>(service.start(Presets.W12_12)).decision.reason)
    }

    @Test fun `end early and on time`() = runTest {
        service.start(Presets.W16_8)
        clock.advance(hours(10))
        assertEquals(FastStatus.ENDED_EARLY, service.endActive()!!.status)
        assertNull(service.endActive())

        service.start(Presets.W16_8)
        clock.advance(hours(16))
        assertEquals(FastStatus.COMPLETED, service.endActive()!!.status)
    }

    @Test fun `editing cannot lengthen past the gate`() = runTest {
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        val r = service.editTimes(f.id, f.startAt, f.startAt.plus(hours(48)), null)
        assertEquals(BlockReason.EXCEEDS_GATED_LIMIT, assertIs<EditResult.Blocked>(r).decision.reason)
    }

    @Test fun `editing start time of active fast`() = runTest {
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        val r = assertIs<EditResult.Updated>(service.editTimes(f.id, t0.minus(hours(2)), t0.plus(hours(14)), null))
        assertEquals(t0.minus(hours(2)), r.fast.startAt)
        assertEquals(r.fast, fasts.getActive())
        assertIs<EditResult.Invalid>(service.editTimes(f.id, t0.plus(hours(1)), t0.plus(hours(17)), null))
    }
}
