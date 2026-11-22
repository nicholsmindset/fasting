package com.fastingshift.domain

import com.fastingshift.domain.backup.HistoryBatch
import com.fastingshift.domain.backup.HistoryCsv
import com.fastingshift.domain.backup.HistoryFileException
import com.fastingshift.domain.backup.HistorySkip
import com.fastingshift.domain.backup.against
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryCsvTest {
    private val origin = Instant.parse("2025-01-01T00:00:00Z")
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val header = "start_at,target_end_at,ended_at"
    private fun instant(hour: Long) = origin.plusSeconds(hour * 3600)
    private fun fast(start: Long, end: Long, target: Long = end) =
        Fast(startAt = instant(start), targetEndAt = instant(target), protocolId = "test").end(instant(end))

    @Test fun exportsOnlyFinishedTimesPlansAndKinds() {
        val first = fast(0, 12, 16).copy(id = 555, kind = FastKind.RAMADAN, mood = 3, note = "private")
        val second = fast(24, 40)
        val active = Fast(startAt = instant(48), targetEndAt = instant(60), protocolId = "x")
        val file = HistoryCsv.export(listOf(second, active, first))
        val result = HistoryCsv.parse(file, now)
        assertFalse("private" in file)
        assertEquals(listOf(first.copy(id = 0, note = null, mood = null), second), result.fasts)
        assertEquals(FastStatus.ENDED_EARLY, result.fasts.first().status)
    }

    @Test fun existingZipCsvCanHaveQuotedMultilineNotesBomAndOffsets() {
        val file = "\uFEFFid,start_at,target_end_at,ended_at,protocol_id,status,note,mood,kind\r\n" +
            "7,2025-01-01T02:00:00+02:00,2025-01-01T14:00:00+02:00,2025-01-01T14:00:00+02:00,test,COMPLETED,\"hi, \"\"there\"\"\r\nbye\",4,REGULAR"
        assertEquals(listOf(fast(0, 12)), HistoryCsv.parse(file, now).fasts)
    }

    @Test fun skipsMissingGoalsUnfinishedInvalidOrFutureTimesAndRejectsUnknownHeaders() {
        val file = listOf(
            header,
            listOf(instant(0), "", instant(4)).joinToString(","),
            listOf(instant(0), instant(4), "").joinToString(","),
            listOf(instant(0), instant(0), instant(4)).joinToString(","),
            listOf(instant(4), instant(8), instant(2)).joinToString(","),
            listOf(instant(0), instant(4), now.plusSeconds(1)).joinToString(","),
            listOf("2025-01-01T02:00:00", instant(4), instant(5)).joinToString(","),
        ).joinToString("\n")
        val result = HistoryCsv.parse(file, now)
        assertEquals(mapOf(HistorySkip.NO_GOAL to 1, HistorySkip.UNFINISHED to 1, HistorySkip.INVALID to 4), result.skipped)
        assertTrue(result.fasts.isEmpty())
        assertFailsWith<HistoryFileException> { HistoryCsv.parse("name,age\nx,1", now) }
        assertFailsWith<HistoryFileException> { HistoryCsv.parse(header + "\n\"unclosed", now) }
    }

    @Test fun legacyZeroReportsMissingEvidenceWithoutInventingGoals() {
        val csv = "Date,Start,End,Hours,Night Eating\n1/17/21,19:15,12:00,16,1"
        assertEquals(HistoryBatch(emptyList(), mapOf(HistorySkip.LEGACY_ZERO to 1)), HistoryCsv.parse(csv, now))
    }

    @Test fun nestedArchivedIntervalsActiveFastBoundariesAndInFileDuplicates() {
        val stored = listOf(
            fast(5, 25), fast(7, 8),
            Fast(startAt = instant(40), targetEndAt = instant(50), protocolId = "active"),
        )
        val keep = listOf(fast(0, 5), fast(25, 30), fast(30, 40))
        val input = keep.reversed() + listOf(fast(7, 8), fast(7, 8, 9), fast(12, 13), fast(41, 42), fast(26, 32), keep.first())
        val preview = HistoryBatch(input).against(stored)
        assertEquals(keep, preview.fasts)
        assertEquals(mapOf(HistorySkip.DUPLICATE to 2, HistorySkip.OVERLAP to 4), preview.skipped)
    }

    @Test fun aFastEndedAtItsStartRoundTrips() {
        val endedImmediately = fast(0, 0, 12)
        val parsed = HistoryCsv.parse(HistoryCsv.export(listOf(endedImmediately)), now)
        assertEquals(listOf(endedImmediately), parsed.fasts)
        assertEquals(mapOf(HistorySkip.DUPLICATE to 1), parsed.against(listOf(endedImmediately)).skipped)
    }

    @Test fun emptyFileWithRecognizedHeaderHasNoRows() {
        assertEquals(HistoryBatch(emptyList()), HistoryCsv.parse(header, now))
    }
}
