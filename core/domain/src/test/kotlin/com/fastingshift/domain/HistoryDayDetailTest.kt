package com.fastingshift.domain

import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.model.Fast
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryDayDetailTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 1)
    private val midnight = day.atStartOfDay(zone).toInstant()
    private val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()

    private fun fast(id: Long, start: Instant, end: Instant? = null) =
        Fast(id = id, startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8")
            .let { if (end == null) it else it.end(end) }

    @Test fun `overnight records retain their original times and return in chronological order`() {
        val yesterday = fast(1, midnight.minusSeconds(7200), midnight.plusSeconds(3600))
        val tonight = fast(2, nextMidnight.minusSeconds(7200), nextMidnight.plusSeconds(3600))
        val endsExactlyAtStart = fast(3, midnight.minusSeconds(3600), midnight)
        val startsExactlyAtEnd = fast(4, nextMidnight, nextMidnight.plusSeconds(3600))
        val detail = HistoryStats.dayDetail(
            listOf(tonight, startsExactlyAtEnd, yesterday, endsExactlyAtStart), zone, day, nextMidnight.plusSeconds(7200),
        )
        assertEquals(listOf(yesterday, tonight), detail.entries.map { it.fast })
        assertEquals(listOf(midnight, tonight.startAt), detail.entries.map { it.from })
        assertEquals(listOf(yesterday.endedAt, nextMidnight), detail.entries.map { it.to })
        assertEquals(Duration.ofHours(3), detail.totalFasting)
    }

    @Test fun `day contributions follow civil midnight on short and long DST dates`() {
        val ny = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val from = date.atStartOfDay(ny).toInstant()
            val to = date.plusDays(1).atStartOfDay(ny).toInstant()
            val across = fast(1, from.minusSeconds(3600), to.plusSeconds(3600))
            val result = HistoryStats.dayDetail(listOf(across), ny, date, to.plusSeconds(3600))
            assertEquals(Duration.ofHours(hours), result.entries.single().duration)
            assertEquals(Duration.ofHours(hours), result.totalFasting)
        }
    }

    @Test fun `active fast ends at now and future instants are never credited`() {
        val now = midnight.plusSeconds(7200)
        val active = fast(1, midnight.minusSeconds(900))
        val future = fast(2, now.plusSeconds(60), now.plusSeconds(3600))
        val result = HistoryStats.dayDetail(listOf(active, future), zone, day, now)
        assertEquals(active, result.entries.single().fast)
        assertEquals(now, result.entries.single().to)
        assertEquals(Duration.ofHours(2), result.totalFasting)
        assertTrue(HistoryStats.dayDetail(listOf(active, future), zone, day.plusDays(1), now).entries.isEmpty())
        assertEquals(Duration.ZERO, HistoryStats.dayDetail(listOf(active), zone, LocalDate.MAX, now).totalFasting)

        // A clock correction can place the end of a saved record ahead of the device clock.
        val clockShift = fast(3, midnight, now.plusSeconds(600))
        assertEquals(now, HistoryStats.dayDetail(listOf(clockShift), zone, day, now).entries.single().to)
    }

    @Test fun `a just-started or immediately-ended fast can be opened on its own date`() {
        val ended = fast(2, midnight, midnight)
        val active = fast(1, midnight)
        val result = HistoryStats.dayDetail(listOf(ended, active), zone, day, midnight)
        assertEquals(listOf(active, ended), result.entries.map { it.fast })
        assertEquals(Duration.ZERO, result.totalFasting)
        assertTrue(HistoryStats.dayDetail(listOf(ended, active), zone, day.minusDays(1), midnight).entries.isEmpty())
    }
}
