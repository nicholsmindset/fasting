package com.fastingshift.domain

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.timer.FastTimer
import com.fastingshift.domain.timer.toClockString
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FastTimerTest {
    private val start = Instant.parse("2026-11-20T12:00:00Z")
    private val fast16 = Fast(startAt = start, targetEndAt = start.plus(hours(16)), protocolId = "window_16_8")

    @Test fun `elapsed and remaining mid-fast`() {
        val s = FastTimer.state(fast16, start.plus(hours(4)))
        assertEquals(hours(4), s.elapsed)
        assertEquals(hours(12), s.remaining)
        assertEquals(0.25f, s.progress)
        assertFalse(s.isTargetReached)
        assertEquals(Duration.ZERO, s.pastTarget)
    }

    @Test fun `past target caps progress and reports overrun`() {
        val s = FastTimer.state(fast16, start.plus(hours(18)))
        assertEquals(1f, s.progress)
        assertTrue(s.isTargetReached)
        assertEquals(Duration.ZERO, s.remaining)
        assertEquals(hours(2), s.pastTarget)
    }

    @Test fun `clock earlier than start clamps to zero`() {
        val s = FastTimer.state(fast16, start.minus(hours(1)))
        assertEquals(Duration.ZERO, s.elapsed)
        assertEquals(0f, s.progress)
    }

    @Test fun `ended fast stops the clock`() {
        val ended = fast16.end(start.plus(hours(10)))
        val s = FastTimer.state(ended, start.plus(hours(100)))
        assertEquals(hours(10), s.elapsed)
        assertEquals(FastStatus.ENDED_EARLY, ended.status)
    }

    @Test fun `state survives process death because it is derived from stored instants`() {
        // Simulate: fast stored, app killed, process recreated hours later from the same row.
        val stored = fast16.copy(id = 7)
        val restored = Fast(stored.id, stored.startAt, stored.targetEndAt, stored.endedAt, stored.protocolId, stored.status, stored.note)
        val now = start.plus(Duration.ofMinutes(500))
        assertEquals(FastTimer.state(stored, now), FastTimer.state(restored, now))
        assertEquals(Duration.ofMinutes(500), FastTimer.state(restored, now).elapsed)
    }

    @Test fun `time-zone change does not change elapsed`() {
        // Start in Singapore, read the clock after flying to London.
        val sgStart = ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, ZoneId.of("Asia/Singapore"))
        val f = Fast(startAt = sgStart.toInstant(), targetEndAt = sgStart.toInstant().plus(hours(16)), protocolId = "p")
        val londonNow = ZonedDateTime.of(2026, 11, 21, 4, 0, 0, 0, ZoneId.of("Europe/London"))
        // 20:00 SGT = 12:00Z; 04:00 GMT next day = 04:00Z => 16h.
        val s = FastTimer.state(f, londonNow.toInstant())
        assertEquals(hours(16), s.elapsed)
        assertTrue(s.isTargetReached)
    }

    @Test fun `DST transition counts real hours`() {
        // US DST ends 2026-11-01 02:00 local: a fast from 20:00 to 12:00 the next day is 17 real hours.
        val ny = ZoneId.of("America/New_York")
        val s0 = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, ny).toInstant()
        val now = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ny).toInstant()
        val f = Fast(startAt = s0, targetEndAt = s0.plus(hours(16)), protocolId = "p")
        assertEquals(hours(17), FastTimer.state(f, now).elapsed)
    }

    @Test fun `next milestone is first one not yet reached`() {
        val ms = listOf(hours(16), hours(12), hours(24))
        assertEquals(hours(12), FastTimer.state(fast16, start.plus(hours(1)), ms).nextMilestone)
        assertEquals(hours(16), FastTimer.state(fast16, start.plus(hours(12)), ms).nextMilestone)
        assertNull(FastTimer.state(fast16, start.plus(hours(30)), ms).nextMilestone)
    }

    @Test fun `ending exactly on target is completed`() {
        assertEquals(FastStatus.COMPLETED, fast16.end(fast16.targetEndAt).status)
    }

    @Test fun `editing an ended fast re-derives status`() {
        val early = fast16.end(start.plus(hours(15)))
        val edited = early.editTimes(startAt = start.minus(hours(2)))
        assertEquals(FastStatus.COMPLETED, edited.status)
        assertEquals(hours(16), edited.plannedDuration)
    }

    @Test fun `invalid fasts are rejected`() {
        assertFailsWith<IllegalArgumentException> { Fast(startAt = start, targetEndAt = start, protocolId = "p") }
        assertFailsWith<IllegalArgumentException> {
            Fast(startAt = start, targetEndAt = start.plus(hours(1)), endedAt = start.minusSeconds(1), protocolId = "p", status = FastStatus.COMPLETED)
        }
        assertFailsWith<IllegalStateException> { fast16.end(start.plus(hours(1))).end(start.plus(hours(2))) }
    }

    @Test fun `clock string formats hours beyond a day`() {
        assertEquals("36:04:09", Duration.ofHours(36).plusMinutes(4).plusSeconds(9).toClockString())
        assertEquals("0:00:00", Duration.ofSeconds(-5).toClockString())
    }
}
