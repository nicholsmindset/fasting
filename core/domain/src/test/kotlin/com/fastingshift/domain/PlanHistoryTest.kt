package com.fastingshift.domain

import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.history.Period
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.schedule.WeekChoice
import com.fastingshift.domain.streak.StreakCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanHistoryTest {
    private val monday = LocalDate.of(2026, 3, 2)
    private val zone = ZoneId.of("America/New_York")
    private val schedule = Schedule(protocolId = "p", days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), startTime = LocalTime.of(20, 0))

    private fun completed(day: LocalDate): Fast {
        val start = day.atTime(8, 0).atZone(zone).toInstant()
        val end = start.plus(Duration.ofHours(12))
        return Fast(startAt = start, targetEndAt = end, protocolId = "p").end(end)
    }

    @Test fun midweekBaselineExcludesEarlierDaysAndLaterEditsPreservePastWeeks() {
        val wed = monday.plusDays(2)
        val fri = monday.plusDays(4)
        val history = PlanHistory().record(wed, PlanHistory.EVERY_DAY)
            .record(fri, PlanHistory.mask(listOf(schedule)))
        val fasts = listOf(completed(monday), completed(wed), completed(fri))
        val summary = HistoryStats.periodSummary(fasts, zone, Period.WEEK, fri, fri.plusDays(1).atStartOfDay(zone).toInstant(), history::isPlanned)
        assertEquals(2, summary.plannedDays)
        assertEquals(1, summary.completedDays)
        assertEquals(3, summary.fasts)
        assertFalse(history.isPlanned(monday))
        assertTrue(history.isPlanned(monday.plusWeeks(1)))
        assertEquals(listOf(0, 2), Insights.weeklyConsistency(fasts, zone, fri, 2, history::isPlanned).map { it.plannedDays })
        assertEquals(1, StreakCalculator.calculate(fasts, zone, fri, isPlannedDay = history::isPlanned).longest)
    }

    @Test fun sameDayChangesReplaceOnlyThatDaysRevision() {
        val wed = monday.plusDays(2)
        val changed = PlanHistory().record(monday, 127).record(wed, 1).record(wed, 8)
        assertEquals(mapOf(monday to 127, wed to 8), changed.revisions)
        assertEquals(mapOf(monday to 127), changed.record(wed, 127).revisions)
    }

    @Test fun weekOffBridgesStreakAndSkipsStartsAcrossDstAndConsecutiveBreaks() {
        val following = monday.plusWeeks(1)
        val history = PlanHistory(mapOf(monday.minusWeeks(1) to PlanHistory.EVERY_DAY), mapOf(monday to WeekChoice.BREAK, following to WeekChoice.BREAK))
        val resumed = monday.plusWeeks(2)
        val fasts = listOf(completed(monday.minusDays(1)), completed(monday), completed(resumed))
        assertFalse(history.isPlanned(following.plusDays(6)))
        assertTrue(history.isPlanned(resumed))
        assertEquals(2, StreakCalculator.calculate(fasts, zone, resumed, isPlannedDay = history::isPlanned).current)
        val next = ScheduleCalculator.nextStart(listOf(schedule), zone, monday.atStartOfDay(zone).toInstant(), history.breakWeeks)
        assertEquals(resumed.atTime(20, 0).atZone(zone).toInstant(), next?.second)
    }

    @Test fun oldOrInvalidBackupDataDoesNotInventDays() {
        val history = PlanHistory.fromRecords(mapOf("not a date" to 127, monday.toString() to 256), mapOf(monday.plusDays(1).toString() to "BREAK"))
        assertNull(history.trackedFrom)
        assertFalse(history.isPlanned(monday))
        assertTrue(history.breakWeeks.isEmpty())
        val restored = PlanHistory.fromRecords(mapOf(monday.toString() to 1), mapOf(monday.plusWeeks(1).toString() to "BREAK"))
        assertTrue(restored.isPlanned(monday))
        assertFalse(restored.isPlanned(monday.plusWeeks(1)))
        assertFalse(restored.isPlanned(monday.minusWeeks(1)))
    }
}
