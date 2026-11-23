package com.fastingshift.domain

import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.history.Period
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.schedule.DayOverride
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

    @Test fun movingOneStartPreservesOtherStartsAndWorksAcrossTravel() {
        val early = schedule.copy(id = 3, startTime = LocalTime.of(18, 0))
        val later = schedule.copy(id = 4)
        val plans = listOf(later, early)
        val moved = mapOf(monday to DayOverride(LocalTime.of(21, 30)))
        val thursday = monday.plusDays(3)
        for (localZone in listOf(zone, ZoneId.of("Asia/Singapore"))) {
            val tonight = monday.atTime(21, 30).atZone(localZone).toInstant()
            val next = ScheduleCalculator.nextStart(plans, localZone, tonight.minusSeconds(1), dayOverrides = moved)
            assertEquals(early to tonight, next)
            val originalLater = monday.atTime(20, 0).atZone(localZone).toInstant()
            assertEquals(listOf(later to originalLater, early to tonight),
                ScheduleCalculator.startsOn(plans, monday, localZone, dayOverrides = moved))
            assertEquals(listOf(early to originalLater),
                ScheduleCalculator.startsOn(plans, monday, localZone, dayOverrides = mapOf(monday to DayOverride(LocalTime.of(20, 0)))))
            assertEquals(early to thursday.atTime(18, 0).atZone(localZone).toInstant(),
                ScheduleCalculator.nextStart(plans, localZone, tonight, dayOverrides = moved))
            assertEquals(2, ScheduleCalculator.startsOn(plans, thursday, localZone, dayOverrides = moved).size)
        }
    }

    @Test fun oneOffSkipsRespectBreaksAndNeverInventUnscheduledDays() {
        val thursday = monday.plusDays(3)
        val tuesday = monday.plusDays(1)
        val sunday = monday.plusDays(6)
        val previous = PlanHistory().record(monday, PlanHistory.mask(listOf(schedule)))
        val history = previous.copy(
            choices = mapOf(monday.plusWeeks(1) to WeekChoice.BREAK),
            dayOverrides = mapOf(thursday to DayOverride(), tuesday to DayOverride(LocalTime.NOON)),
        ).record(monday.plusWeeks(1), PlanHistory.EVERY_DAY)
        assertFalse(history.isPlanned(thursday))
        assertFalse(history.isPlanned(tuesday))
        assertTrue(history.isPlanned(monday))
        assertFalse(history.isPlanned(sunday))
        val next = ScheduleCalculator.nextStart(listOf(schedule), zone, monday.atTime(23, 0).atZone(zone).toInstant(),
            history.breakWeeks, history.dayOverrides)
        assertEquals(monday.plusWeeks(2).atTime(20, 0).atZone(zone).toInstant(), next?.second)
        assertTrue(ScheduleCalculator.startsOn(listOf(schedule), tuesday, zone, dayOverrides = history.dayOverrides).isEmpty())
    }

    @Test fun dstGapMovesTheOverrideToTheNextValidLocalTime() {
        val sunday = monday.plusDays(6) // New York springs forward on 8 March 2026.
        val plan = schedule.copy(days = setOf(DayOfWeek.SUNDAY))
        val moved = mapOf(sunday to DayOverride(LocalTime.of(2, 30)))
        val expected = sunday.atTime(3, 30).atZone(zone).toInstant()
        assertEquals(plan to expected, ScheduleCalculator.nextStart(listOf(plan), zone, sunday.atStartOfDay(zone).toInstant(), dayOverrides = moved))
        assertTrue(ScheduleCalculator.startsOn(listOf(plan), sunday, zone, setOf(monday), moved).isEmpty())
        assertNull(ScheduleCalculator.nextStart(listOf(plan.copy(enabled = false)), zone, expected, dayOverrides = moved))
    }

    @Test fun temporaryFirstEveningOccursOnlyOnceAndDoesNotSuppressOtherSchedules() {
        val temporary = schedule.copy(id = 1, days = setOf(monday.dayOfWeek))
        val marker = temporary.id to monday
        val tonight = monday.atTime(20, 0).atZone(zone).toInstant()
        assertEquals(temporary to tonight, ScheduleCalculator.nextStart(listOf(temporary), zone, tonight.minusSeconds(1), firstEvening = marker))
        assertNull(ScheduleCalculator.nextStart(listOf(temporary), zone, tonight, firstEvening = marker))
        assertTrue(ScheduleCalculator.startsOn(listOf(temporary), monday.plusWeeks(1), zone, firstEvening = marker).isEmpty())
        val regular = schedule.copy(id = 2, days = setOf(DayOfWeek.FRIDAY))
        val friday = monday.plusDays(4).atTime(20, 0).atZone(zone).toInstant()
        assertEquals(regular to friday, ScheduleCalculator.nextStart(listOf(temporary, regular), zone, tonight, firstEvening = marker))
    }
}
