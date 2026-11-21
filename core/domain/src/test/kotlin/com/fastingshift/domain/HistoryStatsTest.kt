package com.fastingshift.domain

import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.history.Period
import com.fastingshift.domain.model.Fast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryStatsTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private fun at(d: LocalDate, h: Int, m: Int = 0): Instant = d.atTime(h, m).atZone(zone).toInstant()
    private fun fast(start: Instant, planned: Long, actual: Long? = planned) =
        Fast(startAt = start, targetEndAt = start.plus(hours(planned)), protocolId = "p").let { f -> actual?.let { f.end(start.plus(hours(it))) } ?: f }

    private val fri = LocalDate.of(2026, 11, 20) // Friday; week starts Mon 16 Nov

    @Test fun `week summary clips fasts at the week edges`() {
        val sunNight = fast(at(LocalDate.of(2026, 11, 15), 20), 16) // Sun 20:00 → Mon 12:00, 12h inside the week
        val wed = fast(at(LocalDate.of(2026, 11, 18), 20), 16, 10) // ended early
        val s = HistoryStats.periodSummary(listOf(sunNight, wed), zone, Period.WEEK, fri, at(fri, 23))
        assertEquals(hours(22), s.totalFasting)
        assertEquals(1, s.fasts) // only the Wednesday fast started this week
        assertEquals(0, s.completed)
        assertEquals(hours(10), s.average)
        assertEquals(5, s.plannedDays) // Mon..Fri so far
        assertEquals(0, s.completedDays) // the Sunday-night fast counts for Sunday, last week
        assertEquals(0f, s.consistency)
    }

    @Test fun `active fast counts up to now and month total clips at month start`() {
        val acrossMonths = fast(at(LocalDate.of(2026, 10, 31), 20), 36) // 4h in Oct, 32h in Nov
        val active = fast(at(fri, 8), 16, null)
        val now = at(fri, 12)
        assertEquals(hours(36), HistoryStats.monthTotal(listOf(acrossMonths, active), zone, YearMonth.of(2026, 11), now))
        assertEquals(hours(4), HistoryStats.monthTotal(listOf(acrossMonths), zone, YearMonth.of(2026, 10), now))
        val month = HistoryStats.periodSummary(listOf(acrossMonths, active), zone, Period.MONTH, fri, now)
        assertEquals(1, month.fasts)
        assertNull(month.average) // the only fast started in November is still running
    }

    @Test fun `empty periods`() {
        val s = HistoryStats.periodSummary(emptyList(), zone, Period.YEAR, fri, at(fri, 12))
        assertEquals(Duration.ZERO, s.totalFasting)
        assertEquals(0f, s.consistency)
        assertEquals(LocalDate.of(2026, 1, 1), s.range.start)
    }

    @Test fun `recent fasts are oldest first with average, active excluded`() {
        val a = fast(at(fri.minusDays(3), 20), 16)
        val b = fast(at(fri.minusDays(2), 20), 16, 12)
        val c = fast(at(fri.minusDays(1), 20), 16, null)
        val r = HistoryStats.recentFasts(listOf(c, b, a))
        assertEquals(listOf(a.startAt, b.startAt), r.bars.map { it.fast.startAt })
        assertEquals(hours(14), r.average)
        assertEquals(listOf(true, false), r.bars.map { it.completed })
    }

    @Test fun `segments use time-of-day positions`() {
        val f = fast(at(LocalDate.of(2026, 11, 17), 18), 16) // Tue 18:00 → Wed 10:00
        val seg = HistoryStats.weekSegments(listOf(f), zone, LocalDate.of(2026, 11, 16), at(fri, 12)).single()
        assertEquals(1.75f, seg.startDay, 0.001f)
        assertEquals(2f + 10f / 24f, seg.endDay, 0.001f)
        assertTrue(seg.showLabel)
        assertFalse(seg.continuesBefore || seg.continuesAfter)
    }

    @Test fun `a fast crossing weeks is split and labelled once`() {
        val f = fast(at(LocalDate.of(2026, 11, 22), 6), 72) // Sun 06:00 → Wed 06:00: 18h in week 1, 54h in week 2
        val w1 = HistoryStats.weekSegments(listOf(f), zone, LocalDate.of(2026, 11, 16), at(fri.plusDays(10), 0)).single()
        val w2 = HistoryStats.weekSegments(listOf(f), zone, LocalDate.of(2026, 11, 23), at(fri.plusDays(10), 0)).single()
        assertEquals(7f, w1.endDay)
        assertEquals(0f, w2.startDay)
        assertTrue(w1.continuesAfter && w2.continuesBefore)
        assertFalse(w1.showLabel)
        assertTrue(w2.showLabel)
        assertEquals(hours(72), w2.totalLength)
    }

    @Test fun `positions honour a 23-hour DST day`() {
        val ny = ZoneId.of("America/New_York")
        val sun = LocalDate.of(2026, 3, 8) // spring forward
        val monday = LocalDate.of(2026, 3, 2)
        val noon = sun.atTime(12, 0).atZone(ny).toInstant() // 11 real hours after midnight
        assertEquals(6f + 11f / 23f, HistoryStats.position(noon, monday, ny), 0.001f)
    }
}
