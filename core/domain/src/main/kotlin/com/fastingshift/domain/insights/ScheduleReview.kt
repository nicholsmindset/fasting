package com.fastingshift.domain.insights

import com.fastingshift.domain.history.History
import com.fastingshift.domain.history.ScheduleFit
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.schedule.PlanHistory
import java.time.LocalDate
import java.time.ZoneId

enum class PlanCoverage { PLANNED, WEEK_OFF, BEFORE_TRACKING, NO_PLANNED_DAYS }

data class ReviewWeek(
    val consistency: WeeklyConsistency,
    val coverage: PlanCoverage,
    val trackedFrom: LocalDate? = null,
    val movedDates: Int = 0,
    val skippedDates: Int = 0,
    val reflection: ScheduleFit? = null,
)

object ScheduleReview {
    /** The four complete Monday–Sunday weeks before the current local week, most recent first. */
    fun finishedWeeks(
        fasts: List<Fast>, plan: PlanHistory, reflections: Map<LocalDate, ScheduleFit>, zone: ZoneId, today: LocalDate,
    ): List<ReviewWeek> {
        val lastSunday = History.weekStart(today).minusDays(1)
        return Insights.weeklyConsistency(fasts, zone, lastSunday, weeks = 4, isPlannedDay = plan::isPlanned)
            .asReversed().map { week ->
                val monday = week.weekStart
                val sunday = monday.plusDays(6)
                val first = plan.trackedFrom
                val changes = plan.dayOverrides.filterKeys { it in monday..sunday }.values
                ReviewWeek(
                    consistency = week,
                    coverage = when {
                        plan.isBreak(monday) -> PlanCoverage.WEEK_OFF
                        first == null || first > sunday -> PlanCoverage.BEFORE_TRACKING
                        week.plannedDays == 0 -> PlanCoverage.NO_PLANNED_DAYS
                        else -> PlanCoverage.PLANNED
                    },
                    trackedFrom = first?.takeIf { it > monday && it <= sunday },
                    movedDates = changes.count { !it.isSkipped },
                    skippedDates = changes.count { it.isSkipped },
                    reflection = reflections[monday]?.takeUnless { it == ScheduleFit.SKIPPED },
                )
            }
    }
}
