package com.fastingshift.app.ui.firstweek

import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.coaching.CoachingPlanner
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** Pure decisions for the first-week journey, kept apart from Compose so they're easy to test. */
object FirstWeekRules {
    val EVENING_START: LocalTime = LocalTime.of(20, 0)

    /**
     * "Your first fast" shows only to someone who has never started a fast and may start their
     * default protocol now (screened, and the gate allows that length).
     */
    fun showFirstFastCard(hasAnyFast: Boolean, gate: GateResult?, clinicianAcknowledged: Boolean, protocol: Protocol): Boolean {
        if (hasAnyFast) return false
        val max = FastPolicy.maxAllowed(gate, clinicianAcknowledged) ?: return false
        return Duration.ofHours(protocol.fastHours.toLong()) <= max
    }

    /** The coaching tip to show under the timer now, or null (coaching off, past the first fasts, or too early). */
    fun coachingTip(enabled: Boolean, fastsStartedBefore: Int, onboardedAt: Instant?, fast: Fast, now: Instant): CoachingTip? {
        if (!enabled || !fast.isActive) return null
        if (!CoachingPlanner.isCoaching(fastsStartedBefore, onboardedAt, now)) return null
        return CoachingPlanner.currentTip(fast, now)
    }

    /** Fasts started before [fast], which decides whether it is still one of the coached first fasts. */
    fun fastsStartedBefore(all: List<Fast>, fast: Fast): Int = all.count { it.id != fast.id && it.startAt.isBefore(fast.startAt) }

    /** The next 8:00 PM local time strictly after [now]: tonight, or tomorrow if it has passed. */
    fun nextEvening(now: Instant, zone: ZoneId): ZonedDateTime {
        val local = now.atZone(zone)
        val tonight = local.toLocalDate().atTime(EVENING_START).atZone(zone)
        return if (tonight.toInstant().isAfter(now)) tonight else local.toLocalDate().plusDays(1).atTime(EVENING_START).atZone(zone)
    }

    /** Undo is only a quick "oops" right after ending ([FastService.UNDO_WINDOW]). */
    fun canUndo(fast: Fast, now: Instant): Boolean =
        fast.endedAt?.let { !it.isAfter(now.plus(FastService.CLOCK_SLACK)) && Duration.between(it, now) <= FastService.UNDO_WINDOW } ?: false
}

/** A one-off "start tonight" plan: when it is for and the schedule that carries its reminder. */
data class PlannedFirstFast(val scheduleId: Long, val date: LocalDate)

/**
 * "Start tonight at 8:00 PM" for the very first fast. The reminder rides on the existing fast-start
 * schedule reminders: we save a one-day [Schedule] at 20:00 and remember its id here, then delete
 * it as soon as any fast has started, or once its evening has passed, so it never repeats weekly
 * or counts as a planned day in History.
 */
class FirstFastPlanner @Inject constructor(
    private val schedules: ScheduleRepository,
    private val fasts: FastRepository,
    private val clock: Clock,
) {
    fun planned(): PlannedFirstFast? = schedules.firstEvening()?.let { (id, day) -> PlannedFirstFast(id, day) }

    suspend fun planTonight(protocol: Protocol, zone: ZoneId = ZoneId.systemDefault()): PlannedFirstFast {
        val at = FirstWeekRules.nextEvening(clock.instant(), zone)
        val schedule = Schedule(protocolId = protocol.id, days = setOf(at.dayOfWeek), startTime = FirstWeekRules.EVENING_START)
        val id = schedules.saveFirstEvening(schedule, at.toLocalDate())
        return PlannedFirstFast(id, at.toLocalDate())
    }

    suspend fun cancel() = schedules.cancelFirstEvening()

    /** Removes the one-off schedule once a fast exists or its evening is over. */
    suspend fun cleanUp(zone: ZoneId = ZoneId.systemDefault()) {
        val plan = planned() ?: return
        val today = clock.instant().atZone(zone).toLocalDate()
        if (fasts.getAll().isNotEmpty() || plan.date.isBefore(today)) cancel()
    }
}
