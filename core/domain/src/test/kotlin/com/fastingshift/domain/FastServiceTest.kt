package com.fastingshift.domain

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.service.EditResult
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.StartResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FastServiceTest {
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")
    private val clock = MutableClock(t0)
    private val fasts = FakeFastRepository()
    private val screening = FakeScreeningRepository()
    private val service = FastService(fasts, screening, clock)

    private fun screened(gate: GateResult = GateResult.OK, ack: Boolean = false) {
        val answers = ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 75.0)
        screening.record = ScreeningRecord(t0, answers, gate, ack)
    }

    @Test fun `unscreened users cannot start any fast`() = runTest {
        assertEquals(BlockReason.NEEDS_SCREENING, assertIs<StartResult.Blocked>(service.start(Presets.W12_12)).decision.reason)
    }

    @Test fun `one tap starts a 16h fast and a second start is refused`() = runTest {
        screened()
        val started = assertIs<StartResult.Started>(service.start(Presets.W16_8))
        assertEquals(t0.plus(hours(16)), started.fast.targetEndAt)
        assertIs<StartResult.AlreadyActive>(service.start(Presets.W18_6))
    }

    @Test fun `started earlier is allowed but not in the future or too long ago`() = runTest {
        screened()
        assertIs<StartResult.InvalidStartTime>(service.start(Presets.W16_8, startAt = t0.plus(hours(1))))
        assertIs<StartResult.InvalidStartTime>(service.start(Presets.W16_8, startAt = t0.minus(hours(121))))
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8, startAt = t0.minus(hours(3)))).fast
        assertEquals(t0.minus(hours(3)), f.startAt)
    }

    @Test fun `extended fast needs checklist confirmation`() = runTest {
        screened()
        val needs = assertIs<StartResult.NeedsConfirmation>(service.start(Presets.D36))
        assertEquals(setOf(StartRequirement.EXTENDED_FAST_CHECKLIST, StartRequirement.MEDICAL_DISCLAIMER), needs.requirements)
        assertIs<StartResult.Started>(service.start(Presets.D36, confirmed = needs.requirements))
    }

    @Test fun `hard block is education only`() = runTest {
        screened(GateResult.BLOCK)
        assertEquals(BlockReason.EDUCATION_ONLY, assertIs<StartResult.Blocked>(service.start(Presets.W12_12)).decision.reason)
    }

    @Test fun `ending returns refeeding guidance for long fasts`() = runTest {
        screened()
        service.start(Presets.W16_8)
        clock.advance(hours(10))
        val early = service.endActive()!!
        assertEquals(FastStatus.ENDED_EARLY, early.fast.status)
        assertNull(early.refeeding)
        assertNull(service.endActive())

        service.start(Presets.D48, confirmed = setOf(StartRequirement.EXTENDED_FAST_CHECKLIST, StartRequirement.MEDICAL_DISCLAIMER))
        clock.advance(hours(50))
        val long = service.endActive()!!
        assertEquals(FastStatus.COMPLETED, long.fast.status)
        assertEquals(RefeedingTier.STANDARD, long.refeeding)
    }

    @Test fun `undo re-opens an accidentally ended fast`() = runTest {
        screened()
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        clock.advance(hours(2))
        val ended = service.endActive()!!.fast
        assertTrue(service.undoEnd(ended))
        assertEquals(f.id, fasts.getActive()!!.id)
        assertFalse(service.undoEnd(ended), "can't undo twice")
    }

    @Test fun `undo only shortly after ending and never beyond the longest fast`() = runTest {
        screened()
        assertIs<StartResult.Started>(service.start(Presets.W16_8))
        clock.advance(hours(5))
        val ended = service.endActive()!!.fast
        clock.advance(java.time.Duration.ofMinutes(16))
        assertFalse(service.undoEnd(ended), "undo window passed")
    }

    @Test fun `earlier starts and edits can't overlap recorded fasts`() = runTest {
        screened()
        val past = t0.minus(hours(20))
        assertIs<EditResult.Updated>(service.addPastFast(Presets.W16_8, past, past.plus(hours(16))))
        // Started "earlier" into the recorded fast.
        assertIs<StartResult.InvalidStartTime>(service.start(Presets.W16_8, startAt = t0.minus(hours(6))))
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8, startAt = t0.minus(hours(3)))).fast
        assertIs<EditResult.Invalid>(service.editTimes(f.id, t0.minus(hours(10)), t0.plus(hours(6)), null), "edit into the past fast")
    }

    @Test fun `active fast start can't be moved beyond the longest fast`() = runTest {
        screened()
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        assertIs<EditResult.Invalid>(service.editTimes(f.id, t0.minus(hours(130)), t0.minus(hours(114)), null))
    }

    @Test fun `editing can't lengthen past the gate`() = runTest {
        screened(GateResult.SOFT, ack = true)
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        val r = service.editTimes(f.id, f.startAt, f.startAt.plus(hours(48)), null)
        assertEquals(BlockReason.EXCEEDS_GATED_LIMIT, assertIs<EditResult.Blocked>(r).decision.reason)
    }

    @Test fun `editing an active fast into an extended one needs the same confirmations as starting`() = runTest {
        screened()
        val f = assertIs<StartResult.Started>(service.start(Presets.OMAD)).fast
        val needs = assertIs<EditResult.NeedsConfirmation>(service.editTimes(f.id, f.startAt, f.startAt.plus(hours(100)), null))
        assertEquals(
            setOf(StartRequirement.EXTENDED_FAST_CHECKLIST, StartRequirement.MEDICAL_DISCLAIMER, StartRequirement.OVER_72H_ACKNOWLEDGEMENT),
            needs.requirements,
        )
        assertIs<EditResult.Updated>(service.editTimes(f.id, f.startAt, f.startAt.plus(hours(100)), null, needs.requirements))
    }

    @Test fun `edit validation`() = runTest {
        screened()
        val f = assertIs<StartResult.Started>(service.start(Presets.W16_8)).fast
        val r = assertIs<EditResult.Updated>(service.editTimes(f.id, t0.minus(hours(2)), t0.plus(hours(14)), null))
        assertEquals(t0.minus(hours(2)), r.fast.startAt)
        assertIs<EditResult.Invalid>(service.editTimes(f.id, t0.plus(hours(1)), t0.plus(hours(17)), null), "start in the future")
        clock.advance(hours(3))
        val ended = service.endActive()!!.fast
        assertIs<EditResult.Invalid>(service.editTimes(ended.id, ended.startAt, ended.targetEndAt, null), "re-opening goes through undo")
        assertIs<EditResult.Invalid>(service.editTimes(ended.id, ended.startAt, ended.targetEndAt, clock.now.plus(hours(5))), "end in the future")
    }

    @Test fun `add a past fast`() = runTest {
        screened()
        val start = t0.minus(hours(40))
        val r = assertIs<EditResult.Updated>(service.addPastFast(Presets.W16_8, start, start.plus(hours(16))))
        assertEquals(FastStatus.COMPLETED, r.fast.status)
        assertIs<EditResult.Invalid>(service.addPastFast(Presets.W16_8, start.plus(hours(2)), start.plus(hours(10))), "overlaps")
        assertIs<EditResult.Invalid>(service.addPastFast(Presets.W16_8, t0.minus(hours(1)), t0.plus(hours(5))), "in the future")
        assertIs<EditResult.Invalid>(service.addPastFast(Presets.W16_8, t0.minus(hours(1)), t0.plusSeconds(1)), "a history record must already have ended")
    }

    @Test fun `fakes reject a second active fast`() = runTest {
        val a = fasts.insertIfNoActive(Fast(startAt = t0, targetEndAt = t0.plus(hours(16)), protocolId = "p"))
        val b = fasts.insertIfNoActive(Fast(startAt = t0, targetEndAt = t0.plus(hours(16)), protocolId = "p"))
        assertTrue(a != null && b == null)
    }
}
