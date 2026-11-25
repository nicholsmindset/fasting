package com.fastingshift.domain.revival

import com.fastingshift.domain.model.Fast
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevivalTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val january = RevivalCalendar.windows(2027, zone).first()
    private fun date(day: Int) = LocalDate.of(2027, 1, day)
    private fun fast(day: Int, hour: Int = 2, minutes: Long = 60): Fast {
        val start = date(day).atTime(hour, 0).atZone(zone).toInstant()
        return Fast(startAt = start, targetEndAt = start.plusSeconds(60 * minutes), protocolId = "test").end(start.plusSeconds(60 * minutes))
    }

    @Test fun onlyPublishedSingaporeYearsProducePostEidWindows() {
        assertEquals(2, RevivalCalendar.windows(2026, zone).size)
        assertEquals(LocalDate.of(2026, 3, 22), RevivalCalendar.windows(2026, zone)[1].start.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2027, 3, 11), RevivalCalendar.windows(2027, zone)[1].start.atZone(zone).toLocalDate())
        assertEquals(listOf("january-2028"), RevivalCalendar.windows(2028, zone).map { it.id })
        assertFalse(RevivalCalendar.eligibleDay(LocalDate.of(2027, 3, 10), -2))
        for (day in 17..20) assertFalse(RevivalCalendar.eligibleDay(LocalDate.of(2027, 5, day), 2))
        assertEquals(date(15), january.endExclusive.atZone(zone).toLocalDate())
    }

    @Test fun eachCompletedPlannedDayCountsOnceRegardlessOfProtocolOrHours() {
        val fasts = listOf(fast(1), fast(1, 4), fast(2, minutes = 59), fast(3),
            fast(4, minutes = 60 * 25L), fast(6), fast(7))
        val marks = Revival.marks(january, january.start, fasts, january.endExclusive, 0, { it != date(3) })
        assertEquals(listOf(date(1), date(4), date(6)), marks.map { it.startAt.atZone(zone).toLocalDate() })
        assertEquals(3, marks.size)
    }

    @Test fun joinAndEventBoundsAndLocalDayAreEnforced() {
        val joined = date(2).atStartOfDay(zone).toInstant()
        val active = Fast(startAt = joined, targetEndAt = joined.plusSeconds(3600), protocolId = "test")
        val endedEarly = active.end(joined.plusSeconds(60))
        val candidates = listOf(fast(1), active, endedEarly, fast(2, 0), fast(14, 23), fast(15))
        val marks = Revival.marks(january, joined, candidates, january.endExclusive.plus(Duration.ofDays(3)), 0, { true })
        assertEquals(listOf(date(2)), marks.map { it.startAt.atZone(zone).toLocalDate() })
        assertTrue(Revival.marks(january.copy(target = "unknown"), joined, candidates, january.endExclusive, 0, { true }).isEmpty())
    }

    @Test fun crossingIntoEidCannotEarnAMarkInACustomWindow() {
        fun at(day: Int) = LocalDate.of(2027, 3, day).atStartOfDay(zone).toInstant()
        val window = RevivalWindow("spring", at(1), at(16), zone)
        val end = at(11)
        val crossing = Fast(startAt = at(8), targetEndAt = end, protocolId = "test").end(end)
        assertTrue(Revival.marks(window, at(1), listOf(crossing), at(15), 0, { true }).isEmpty())
    }

    @Test fun restoredCountAdvancesOnlyAcrossRealPlannedDays() {
        val records = listOf(fast(6), fast(8))
        val plan: (LocalDate) -> Boolean = { it != date(7) }
        assertEquals(42, Revival.currentRevived(42, date(5), records, emptySet(), date(5), zone, plan))
        assertEquals(44, Revival.currentRevived(42, date(5), records, emptySet(), date(9), zone, plan))
        assertNull(Revival.currentRevived(42, date(5), records, emptySet(), date(10), zone, plan))
        assertEquals(44, Revival.currentRevived(42, date(5), records, setOf(date(9)), date(10), zone, plan))
        assertEquals(2, records.size)
    }
}
