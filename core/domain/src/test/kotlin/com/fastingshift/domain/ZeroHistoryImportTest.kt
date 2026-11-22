package com.fastingshift.domain

import com.fastingshift.domain.backup.HistoryBatch
import com.fastingshift.domain.backup.HistoryFileException
import com.fastingshift.domain.backup.HistoryImport
import com.fastingshift.domain.backup.HistorySkip
import com.fastingshift.domain.backup.against
import com.fastingshift.domain.model.FastStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Fabricated sessions. Structure follows Lightly's public Zero interoperability tests. */
class ZeroHistoryImportTest {
    private val now = Instant.parse("2026-11-22T00:00:00Z")
    private val firestore = """
        {"fasts":[{"data":{
          "start":"2026-02-01T20:00:00+02:00","end":"2026-02-02T10:00:00Z",
          "endWithSeconds":"2026-02-02T09:59:59Z","isEnded":true,"goal":{"hours":16}
        }}]}
    """.trimIndent()

    @Test fun originalGoalAndSecondsDetermineOutcome() {
        val parsed = HistoryImport.parse(firestore, now)
        val fast = parsed.fasts.single()
        assertEquals(Instant.parse("2026-02-01T18:00:00Z"), fast.startAt)
        assertEquals(Instant.parse("2026-02-02T10:00:00Z"), fast.targetEndAt)
        assertEquals(fast.targetEndAt.minusSeconds(1), fast.endedAt)
        assertEquals(FastStatus.ENDED_EARLY, fast.status)
    }

    @Test fun alternateFileDeduplicatesAgainstFirstWithoutExternalIds() {
        val bio = """{"fast_data":[{"StartDTM":"2026-02-01T18:00:00Z","EndDTM":"2026-02-02T09:59:59Z","GoalHours":16,"IsEnded":true,"FastID":"fake"}]}"""
        val first = HistoryImport.parse(firestore, now)
        val second = HistoryImport.parse(bio, now)
        assertEquals(first.fasts, second.fasts)
        assertEquals(HistoryBatch(emptyList(), mapOf(HistorySkip.DUPLICATE to 1)), second.against(first.fasts))
    }

    @Test fun numericInstantsAndExplicitFractionalGoal() {
        val root = """{"fast_data":[{"StartDTM":1735689600,"EndDTM":1735747200000,"GoalHours":"15.5","IsEnded":true}]}"""
        val fast = HistoryImport.parse(root, now).fasts.single()
        assertEquals(Duration.ofMinutes(930), fast.plannedDuration)
        assertEquals(FastStatus.COMPLETED, fast.status)
    }

    @Test fun skippedRowsCannotCreateHistoryGoalsOrActiveTimers() {
        val root = """
            {"fasts":[
              {"data":{"start":"2024-01-01T18:00:00Z","end":null,"isEnded":false,"goal":{"hours":16}}},
              {"data":{"start":"2024-01-01T18:00:00Z","end":"2024-01-02T10:00:00Z","isEnded":true}},
              {"data":{"start":"2024-01-01T18:00:00","end":"2024-01-02T10:00:00Z","goal":{"hours":14}}},
              {"data":{"start":"2024-01-01T18:00:00Z","end":"2024-01-02T10:00:00Z","goal":{"hours":0}}},
              {"data":{"start":"2024-01-01T18:00:00Z","end":"2030-01-02T10:00:00Z","goal":{"hours":14}}},
              {"data":{"start":"2024-01-01T18:00:00Z","end":"2024-01-02T10:00:00Z","goal":{"hours":14},"isEnded":"no"}}
            ]}
        """.trimIndent()
        val result = HistoryImport.parse(root, now)
        assertTrue(result.fasts.isEmpty())
        assertEquals(mapOf(HistorySkip.UNFINISHED to 1, HistorySkip.NO_GOAL to 1, HistorySkip.INVALID to 4), result.skipped)
    }

    @Test fun unknownJsonAndNativeBackupsAreRejected() {
        assertFailsWith<HistoryFileException> { HistoryImport.parse("""{"records":[]}""", now) }
        assertFailsWith<HistoryFileException> { HistoryImport.parse("""{"fasts":[{"start_at":1}]}""", now) }
        assertFailsWith<HistoryFileException> { HistoryImport.parse("""{"fasts":""", now) }
    }
}
