package com.fastingshift.domain

import com.fastingshift.domain.history.FastingWrapped
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.protocol.Presets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class FastingWrappedTest {
    private val utc = ZoneId.of("UTC")
    private val dec = Instant.parse("2026-12-06T22:00:00Z")
    private fun session(day: String, hours: Long = 16, planned: Long = 16, id: String = Presets.W16_8.id): Fast {
        val start = Instant.parse(day)
        return Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(planned)), protocolId = id)
            .end(start.plus(Duration.ofHours(hours)))
    }

    @Test fun decemberUnlockUsesLocalDateAndNeverCreatesAnEmptyStory() {
        val singapore = ZoneId.of("Asia/Singapore")
        val edge = Instant.parse("2026-11-30T16:00:00Z")
        val sessions = listOf(session("2026-11-01T12:00:00Z"))
        assertTrue(FastingWrapped.hasStory(sessions, singapore, edge))
        assertFalse(FastingWrapped.hasStory(sessions, utc, edge))
        assertNull(FastingWrapped.annual(emptyList(), singapore, edge, emptySet()) { true })
        assertEquals(2026, FastingWrapped.latestYear(LocalDate.of(2027, 11, 30)))
    }

    @Test fun novemberClipsAndUnionsTheActualTimeWithoutDoubleCountingImports() {
        val nov = session("2026-10-31T00:00:00Z", 60)
        val overlapping = session("2026-11-01T00:00:00Z", 36)
        val decEdge = session("2026-11-30T12:00:00Z", 24)
        val next = session("2027-01-01T00:00:00Z")
        val result = FastingWrapped.annual(listOf(nov, overlapping, decEdge, next), utc, dec, emptySet()) { true }!!
        assertEquals(7, result.novemberTimerPercent) // 48 of 720 hours (rounded).
        assertEquals(3, result.completed)
        assertEquals(60, result.longestSession.toHours())
        assertEquals(3, result.recordedDays)
        assertTrue(result.repeatedWindow)
    }

    @Test fun sharesNoPrivatePlanLabelAndRespectsPlannedOffDaysAndFreeze() {
        val a = session("2026-11-02T00:00:00Z").copy(kind = FastKind.SUNNAH, note = "hidden")
        val b = session("2026-11-05T00:00:00Z").copy(kind = FastKind.QADA)
        val c = session("2026-11-08T00:00:00Z", 3, 16, "sensitive label")
        val result = FastingWrapped.annual(listOf(a, b, c), utc, dec, setOf(LocalDate.of(2026, 11, 4))) { it.dayOfMonth != 3 }!!
        assertEquals(2, result.bestPlanDays)
        assertEquals(2, result.completed)
        assertEquals(3, result.recordedDays)
        assertNull(result.topWindow)
        assertTrue(result.personalPlan)
    }

    @Test fun sundaysCompareTwoLocalMondayWeeksAndPreserveEarlyFinishes() {
        val current = session("2026-12-03T00:00:00Z", 4, 16)
        val prior = session("2026-11-28T00:00:00Z", 10, 10)
        val rows = listOf(prior,current,session("2026-12-06T00:00:00Z", 6, 6))
        val result = FastingWrapped.sunday(rows, utc, dec)!!
        assertEquals(LocalDate.of(2026, 11, 30), result.weekStart)
        assertEquals(1, result.completed)
        assertEquals(Duration.ofHours(5), result.average)
        assertEquals(Duration.ofHours(10), result.previousAverage)
        assertNull(FastingWrapped.sunday(rows, utc, dec.plus(Duration.ofDays(1))))
        assertNull(FastingWrapped.sunday(emptyList(), utc, dec)!!.average)
    }

    @Test fun sundayUsesLocalWeekBoundariesAndElapsedTimeAcrossSpringForward() {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val rows = listOf(
            session("2026-03-02T07:30:00Z", 1, 1), // Sunday night locally, in the prior week.
            session("2026-03-02T08:15:00Z", 4, 4), // Monday morning locally.
            session("2026-03-08T09:00:00Z", 2, 2), // 01:00 PST to 04:00 PDT.
            session("2026-03-08T10:30:00Z", 3, 3), // Has not ended at the time of the recap.
        )
        assertNull(FastingWrapped.sunday(rows, losAngeles, Instant.parse("2026-03-08T07:59:59Z")))
        val sunday = assertNotNull(FastingWrapped.sunday(rows, losAngeles, Instant.parse("2026-03-08T11:00:00Z")))
        assertEquals(LocalDate.of(2026, 3, 2), sunday.weekStart)
        assertEquals(2, sunday.completed)
        assertEquals(Duration.ofHours(3), sunday.average)
        assertEquals(Duration.ofHours(1), sunday.previousAverage)
        assertNull(FastingWrapped.sunday(rows, losAngeles, Instant.parse("2026-03-09T07:00:00Z")))
    }
}
