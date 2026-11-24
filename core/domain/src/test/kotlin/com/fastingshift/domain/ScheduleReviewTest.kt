package com.fastingshift.domain

import com.fastingshift.domain.history.ScheduleFit
import com.fastingshift.domain.insights.PlanCoverage
import com.fastingshift.domain.insights.ScheduleReview
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.WeekChoice
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleReviewTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val monday = LocalDate.of(2026, 11, 16)
    private val today = monday.plusDays(8)

    private fun fast(day: LocalDate, hour: Int = 20, length: Long = 16): Fast {
        val start = day.atTime(hour, 0).atZone(zone).toInstant()
        return Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8")
            .end(start.plus(Duration.ofHours(length)))
    }

    @Test fun countsCompletedPlannedStartDatesAcrossTheLastFourFinishedWeeks() {
        val firstTracked = monday.minusWeeks(2).plusDays(2)
        val plan = PlanHistory(revisions = mapOf(firstTracked to 21), // Mon, Wed, Fri
            choices = mapOf(monday.minusWeeks(1) to WeekChoice.BREAK),
            dayOverrides = mapOf(monday.plusDays(2) to DayOverride(LocalTime.of(21, 30)),
                monday.plusDays(4) to DayOverride(), monday.plusWeeks(1) to DayOverride()))
        val other = monday.minusWeeks(2)
        val fasts = listOf(fast(monday, 0), fast(monday, 20), fast(monday.plusDays(2), length = 8),
            fast(firstTracked), fast(monday.plusWeeks(1)))
        val result = ScheduleReview.finishedWeeks(fasts, plan,
            mapOf(monday to ScheduleFit.PARTLY, other to ScheduleFit.SKIPPED), zone, today)
        assertEquals(listOf(monday, monday.minusWeeks(1), other, monday.minusWeeks(3)), result.map { it.consistency.weekStart })
        assertEquals(listOf(1, 0, 1, 0), result.map { it.consistency.completedDays })
        assertEquals(listOf(2, 0, 2, 0), result.map { it.consistency.plannedDays })
        assertEquals(listOf(PlanCoverage.PLANNED, PlanCoverage.WEEK_OFF, PlanCoverage.PLANNED, PlanCoverage.BEFORE_TRACKING), result.map { it.coverage })
        assertEquals(firstTracked, result[2].trackedFrom)
        assertEquals(listOf(1, 0, 0, 0), result.map { it.movedDates })
        assertEquals(listOf(1, 0, 0, 0), result.map { it.skippedDates })
        assertEquals(ScheduleFit.PARTLY, result.first().reflection)
        assertNull(result[2].reflection)
    }

    @Test fun repeatedChangesUseDistinctRoutineDatesInFinishedWeeks() {
        val tue = monday.plusDays(1)
        val plan = PlanHistory(revisions = mapOf(tue.minusWeeks(2) to 2),
            choices = mapOf(monday.minusWeeks(1) to WeekChoice.BREAK),
            dayOverrides = mapOf(
                tue to DayOverride(), tue.minusWeeks(2) to DayOverride(LocalTime.NOON),
                tue.minusWeeks(1) to DayOverride(), tue.minusWeeks(3) to DayOverride(),
                tue.plusWeeks(1) to DayOverride(), monday to DayOverride(),
            ))
        val weeks = ScheduleReview.finishedWeeks(emptyList(), plan, emptyMap(), zone, today)
        assertEquals(mapOf(tue.dayOfWeek to 2), ScheduleReview.repeatedChanges(weeks))
        assertEquals(2, weeks.sumOf { it.changes.size })
        assertEquals(emptyMap(), ScheduleReview.repeatedChanges(weeks.take(1)))
    }

    @Test fun allSkippedIsDifferentFromZeroOfOneAndPreTracking() {
        val plan = PlanHistory(mapOf(monday.minusWeeks(1) to 1), dayOverrides = mapOf(monday to DayOverride()))
        val review = ScheduleReview.finishedWeeks(emptyList(), plan, emptyMap(), zone, today)
        assertEquals(PlanCoverage.NO_PLANNED_DAYS, review[0].coverage)
        assertEquals(1, review[0].skippedDates)
        assertEquals(PlanCoverage.PLANNED, review[1].coverage)
        assertEquals(1, review[1].consistency.plannedDays)
        assertEquals(0, review[1].consistency.completedDays)
        assertEquals(PlanCoverage.BEFORE_TRACKING, review[2].coverage)
    }

    @Test fun anOvernightFastBelongsToItsLocalStartAndTheCurrentWeekIsExcludedEvenOnSunday() {
        val plan = PlanHistory(mapOf(monday.minusWeeks(4) to 1))
        val fast = fast(monday, 1) // Sunday in UTC, Monday in Singapore.
        val nextSunday = monday.plusDays(13)
        val local = ScheduleReview.finishedWeeks(listOf(fast), plan, emptyMap(), zone, nextSunday)
        val utc = ScheduleReview.finishedWeeks(listOf(fast), plan, emptyMap(), ZoneId.of("UTC"), nextSunday)
        assertEquals(monday, local.first().consistency.weekStart)
        assertEquals(1, local.first().consistency.completedDays)
        assertEquals(0, utc.first().consistency.completedDays)
    }
}
