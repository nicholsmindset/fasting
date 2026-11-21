package com.fastingshift.domain.service

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.StartDecision
import com.fastingshift.domain.safety.StartRequirement
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface StartResult {
    data class Started(val fast: Fast) : StartResult

    /** The fast is allowed but the user must first see or confirm these items. */
    data class NeedsConfirmation(val requirements: Set<StartRequirement>) : StartResult
    data class Blocked(val decision: StartDecision.Blocked) : StartResult
    data class AlreadyActive(val fast: Fast) : StartResult

    /** The start time is in the future or further back than the longest allowed fast. */
    data object InvalidStartTime : StartResult
}

sealed interface EditResult {
    data class Updated(val fast: Fast) : EditResult
    data class Blocked(val decision: StartDecision.Blocked) : EditResult

    /** Lengthening an active fast needs the same confirmations as starting it (SR-3). */
    data class NeedsConfirmation(val requirements: Set<StartRequirement>) : EditResult
    data object NotFound : EditResult
    data object Invalid : EditResult
}

/** A fast that was just ended, and the refeeding guidance it calls for (SR-5), if any. */
data class EndResult(val fast: Fast, val refeeding: RefeedingTier?)

/**
 * Use cases for the fasting loop. All rules live here so the UI, widgets and notification
 * actions behave identically.
 */
class FastService(
    private val fasts: FastRepository,
    private val screening: ScreeningRepository,
    private val clock: Clock,
) {
    /**
     * Starts a fast of [protocol]'s length, now or at an earlier [startAt] ("I started
     * earlier"). If the policy needs the user to confirm the safety checklist or other items
     * first, returns [StartResult.NeedsConfirmation] until [confirmed] contains all of them.
     */
    suspend fun start(
        protocol: Protocol,
        confirmed: Set<StartRequirement> = emptySet(),
        startAt: Instant = clock.instant(),
    ): StartResult {
        val now = clock.instant()
        if (startAt.isAfter(now.plus(CLOCK_SLACK)) || startAt.isBefore(now.minus(FastPolicy.MAX_FAST))) {
            return StartResult.InvalidStartTime
        }
        fasts.getActive()?.let { return StartResult.AlreadyActive(it) }
        // "I started earlier" can't reach back into a fast that's already recorded.
        if (overlapsAny(startAt, now, excludeId = null)) return StartResult.InvalidStartTime
        val planned = Duration.ofHours(protocol.fastHours.toLong())
        when (val decision = evaluate(planned)) {
            is StartDecision.Blocked -> return StartResult.Blocked(decision)
            is StartDecision.Allowed -> {
                val missing = decision.requirements - confirmed
                if (missing.isNotEmpty()) return StartResult.NeedsConfirmation(missing)
            }
        }
        val fast = Fast(startAt = startAt, targetEndAt = startAt.plus(planned), protocolId = protocol.id)
        val saved = fasts.insertIfNoActive(fast)
            ?: return fasts.getActive()?.let { StartResult.AlreadyActive(it) } ?: StartResult.InvalidStartTime
        return StartResult.Started(saved)
    }

    /**
     * Ends the active fast now. One tap from the UI, no confirmation, no shame copy. Every
     * caller (Timer, widget, notification, red-flag screen) gets the refeeding guidance tier.
     */
    suspend fun endActive(at: Instant = clock.instant()): EndResult? {
        val active = fasts.getActive() ?: return null
        val endAt = if (at.isBefore(active.startAt)) active.startAt else at
        val ended = active.end(endAt)
        fasts.update(ended)
        return EndResult(ended, ended.actualDuration?.let(FastPolicy::refeedingTier))
    }

    /**
     * Undo for an accidental end: re-opens [ended] only shortly after it ended ([UNDO_WINDOW]),
     * if nothing else has started or been recorded since, and never beyond the longest allowed
     * fast. It is a quick "oops", not a way to resume a fast hours or days later.
     */
    suspend fun undoEnd(ended: Fast): Boolean {
        if (ended.isActive || fasts.getActive() != null) return false
        val now = clock.instant()
        val endedAt = ended.endedAt ?: return false
        if (Duration.between(endedAt, now) > UNDO_WINDOW) return false
        if (Duration.between(ended.startAt, now) > FastPolicy.MAX_FAST) return false
        if (overlapsAny(endedAt, now, excludeId = ended.id)) return false
        val current = fasts.getById(ended.id) ?: return false
        // Compare at storage precision (milliseconds): the stored copy of the fast just ended
        // must still be that fast, not one edited since.
        if (current.isActive || current.endedAt?.toEpochMilli() != ended.endedAt?.toEpochMilli()) return false
        fasts.update(current.copy(endedAt = null, status = FastStatus.ACTIVE))
        return true
    }

    /**
     * Records a fast that already happened (forgot to start the timer). Past fasts are a
     * record, not a plan, so only sanity limits apply: within the last year, 1–120h long.
     */
    suspend fun addPastFast(protocol: Protocol, startAt: Instant, endedAt: Instant): EditResult {
        val now = clock.instant()
        val length = Duration.between(startAt, endedAt)
        if (endedAt.isAfter(now.plus(CLOCK_SLACK)) || startAt.isBefore(now.minus(Duration.ofDays(366)))) return EditResult.Invalid
        if (length < FastPolicy.MIN_FAST || length > FastPolicy.MAX_FAST) return EditResult.Invalid
        if (overlapsAny(startAt, endedAt, excludeId = null)) return EditResult.Invalid
        val planned = Duration.ofHours(protocol.fastHours.toLong())
        val fast = Fast(startAt = startAt, targetEndAt = startAt.plus(planned), protocolId = protocol.id).end(endedAt)
        return EditResult.Updated(fasts.insert(fast))
    }

    /**
     * Edits the times of an active or past fast. Lengthening an active fast is re-checked
     * against the full safety policy, including the checklist and the 72h acknowledgement,
     * so editing can never bypass what starting would have required.
     */
    suspend fun editTimes(
        id: Long,
        startAt: Instant,
        targetEndAt: Instant,
        endedAt: Instant?,
        confirmed: Set<StartRequirement> = emptySet(),
    ): EditResult {
        val existing = fasts.getById(id) ?: return EditResult.NotFound
        val now = clock.instant()
        if (!targetEndAt.isAfter(startAt)) return EditResult.Invalid
        if (existing.isActive && endedAt != null) return EditResult.Invalid
        if (!existing.isActive && endedAt == null) return EditResult.Invalid // re-opening uses undoEnd
        if (startAt.isAfter(now.plus(CLOCK_SLACK))) return EditResult.Invalid
        if (existing.isActive && startAt.isBefore(now.minus(FastPolicy.MAX_FAST))) return EditResult.Invalid
        if (endedAt != null && (endedAt.isBefore(startAt) || endedAt.isAfter(now.plus(CLOCK_SLACK)))) return EditResult.Invalid
        if (overlapsAny(startAt, endedAt ?: now, excludeId = id)) return EditResult.Invalid

        val newPlanned = Duration.between(startAt, targetEndAt)
        if (existing.isActive) {
            if (newPlanned > existing.plannedDuration) {
                when (val decision = evaluate(newPlanned)) {
                    is StartDecision.Blocked -> return EditResult.Blocked(decision)
                    is StartDecision.Allowed -> {
                        val added = decision.requirements - FastPolicy.requirementsFor(existing.plannedDuration)
                        val missing = added - confirmed
                        if (missing.isNotEmpty()) return EditResult.NeedsConfirmation(missing)
                    }
                }
            }
        } else {
            val actual = Duration.between(startAt, endedAt)
            if (newPlanned > FastPolicy.MAX_FAST || actual > FastPolicy.MAX_FAST) return EditResult.Invalid
        }
        val updated = existing.editTimes(startAt = startAt, targetEndAt = targetEndAt, endedAt = endedAt)
        fasts.update(updated)
        return EditResult.Updated(updated)
    }

    /** Whether [from, to) overlaps any recorded fast other than [excludeId]. Touching ends is fine. */
    private suspend fun overlapsAny(from: Instant, to: Instant, excludeId: Long?): Boolean {
        val now = clock.instant()
        return fasts.getAll().any { it.id != excludeId && it.startAt.isBefore(to) && (it.endedAt ?: now).isAfter(from) }
    }

    private suspend fun evaluate(planned: Duration): StartDecision {
        val record = screening.getLatest()
        return FastPolicy.evaluateStart(record?.gateResult, record?.clinicianAcknowledged ?: false, planned)
    }

    companion object {
        /** Tolerates small clock differences between a picker and "now". */
        val CLOCK_SLACK: Duration = Duration.ofMinutes(2)

        /** How long after ending a fast "Undo" is still offered. */
        val UNDO_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
