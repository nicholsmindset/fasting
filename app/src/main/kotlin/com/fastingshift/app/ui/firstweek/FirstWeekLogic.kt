package com.fastingshift.app.ui.firstweek

import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.coaching.CoachingPlanner
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.schedule.FirstPlanOffer
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

typealias EveningOffer = FirstPlanOffer

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
    fun nextEvening(now: Instant, zone: ZoneId): ZonedDateTime = nextStartAt(now, EVENING_START, zone)

    /** Uses the same civil-time resolution and minute precision as the Android schedule. */
    fun nextStartAt(now: Instant, time: LocalTime, zone: ZoneId): ZonedDateTime {
        val local = now.atZone(zone)
        val minute = time.withSecond(0).withNano(0)
        val today = local.toLocalDate().atTime(minute).atZone(zone)
        return if (today.toInstant().isAfter(now)) today else local.toLocalDate().plusDays(1).atTime(minute).atZone(zone)
    }

    /** Check the requested 8 pm occurrence, even if Android currently has an earlier alert queued. */
    fun eveningOffer(now: Instant, zone: ZoneId, routines: List<Schedule>, plan: PlanHistory): EveningOffer =
        offerAt(nextEvening(now, zone), routines, plan)

    fun offerAt(target: ZonedDateTime, routines: List<Schedule>, plan: PlanHistory, protocol: Protocol? = null): EveningOffer {
        val zone = target.zone
        val day = target.toLocalDate()
        val edit = plan.dayOverrides[day]
        if (plan.isBreak(day) || edit?.isSkipped == true) return EveningOffer.UNAVAILABLE
        val starts = ScheduleCalculator.startsOn(routines, day, zone, plan.breakWeeks, plan.dayOverrides)
        val existing = starts.firstOrNull { it.second == target.toInstant() }?.first
        if (existing != null) {
            return if (protocol == null || Presets.byId(existing.protocolId)?.fastHours == protocol.fastHours) {
                EveningOffer.COVERED
            } else EveningOffer.UNAVAILABLE
        }
        if (edit != null) {
            // A dated move belongs to the earliest original start. An earlier or coincident new
            // reminder would take over that edit rather than adding the time the button promises.
            val first = ScheduleCalculator.startsOn(routines, day, zone).firstOrNull()?.second
            if (first == null || !first.isBefore(target.toInstant())) return EveningOffer.UNAVAILABLE
        }
        return EveningOffer.AVAILABLE
    }

    /** Undo is only a quick "oops" right after ending ([FastService.UNDO_WINDOW]). */
    fun canUndo(fast: Fast, now: Instant): Boolean =
        fast.endedAt?.let { !it.isAfter(now.plus(FastService.CLOCK_SLACK)) && Duration.between(it, now) <= FastService.UNDO_WINDOW } ?: false
}

/** Identifies a one-time schedule. Its protocol and local start time live in the schedule row. */
data class PlannedFirstFast(val scheduleId: Long, val date: LocalDate)

class FirstFastPlanner @Inject constructor(
    private val schedules: ScheduleRepository,
    private val fasts: FastRepository,
    private val clock: Clock,
    private val screening: ScreeningRepository,
) {
    fun planned(): PlannedFirstFast? = schedules.firstEvening()?.let { (id, day) -> PlannedFirstFast(id, day) }

    suspend fun planAt(protocol: Protocol, start: ZonedDateTime, zone: ZoneId = ZoneId.systemDefault()): EveningOffer {
        val date = start.toLocalDate()
        val schedule = Schedule(protocolId = protocol.id, days = setOf(start.dayOfWeek), startTime = start.toLocalTime())
        return schedules.saveFirstEvening(schedule, date) { routines, plan ->
            val now = clock.instant()
            val today = now.atZone(zone).toLocalDate()
            val record = screening.getLatest()
            val max = FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true)
            // A stale preview cannot roll forward a day, and the queued alarm must be capable of
            // representing this exact instant (minute precision and the earlier overlap offset).
            if (start.zone.rules != zone.rules || start.toInstant() <= now || date > today.plusDays(1) ||
                start.second != 0 || start.nano != 0 ||
                start != start.withEarlierOffsetAtOverlap() || fasts.getAll().isNotEmpty() ||
                max == null || Presets.byId(protocol.id) != protocol || Duration.ofHours(protocol.fastHours.toLong()) > max
            ) {
                EveningOffer.UNAVAILABLE
            } else {
                FirstWeekRules.offerAt(start, eligibleSchedules(routines, max), plan, protocol)
            }
        }
    }

    suspend fun cancel() = schedules.cancelFirstEvening()

    /** Removes the one-off schedule once a fast exists or its evening is over. */
    suspend fun cleanUp(zone: ZoneId = ZoneId.systemDefault()) {
        val plan = planned() ?: return
        val today = clock.instant().atZone(zone).toLocalDate()
        if (fasts.getAll().isNotEmpty() || plan.date.isBefore(today)) {
            schedules.cancelFirstEvening(expected = plan.scheduleId to plan.date)
        }
    }
}
