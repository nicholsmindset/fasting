package com.fastingshift.domain.service

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.safety.FastPolicy
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
}

sealed interface EditResult {
    data class Updated(val fast: Fast) : EditResult
    data class Blocked(val decision: StartDecision.Blocked) : EditResult
    data object NotFound : EditResult
    data object Invalid : EditResult
}

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
     * Starts a fast of [protocol]'s length. If the policy needs the user to confirm the
     * safety checklist or other items first, returns [StartResult.NeedsConfirmation] until
     * [confirmed] contains all of them.
     */
    suspend fun start(
        protocol: Protocol,
        confirmed: Set<StartRequirement> = emptySet(),
        startAt: Instant = clock.instant(),
    ): StartResult {
        fasts.getActive()?.let { return StartResult.AlreadyActive(it) }
        val planned = Duration.ofHours(protocol.fastHours.toLong())
        when (val decision = evaluate(planned)) {
            is StartDecision.Blocked -> return StartResult.Blocked(decision)
            is StartDecision.Allowed -> {
                val missing = decision.requirements - confirmed
                if (missing.isNotEmpty()) return StartResult.NeedsConfirmation(missing)
            }
        }
        val fast = Fast(startAt = startAt, targetEndAt = startAt.plus(planned), protocolId = protocol.id)
        return StartResult.Started(fasts.insert(fast))
    }

    /** Ends the active fast now. One tap from the UI, no confirmation, no shame copy. */
    suspend fun endActive(at: Instant = clock.instant()): Fast? {
        val active = fasts.getActive() ?: return null
        val endAt = if (at.isBefore(active.startAt)) active.startAt else at
        val ended = active.end(endAt)
        fasts.update(ended)
        return ended
    }

    /**
     * Edits the times of an active or past fast. Lengthening a fast is re-checked against the
     * safety policy so editing cannot bypass the screening gates.
     */
    suspend fun editTimes(id: Long, startAt: Instant, targetEndAt: Instant, endedAt: Instant?): EditResult {
        val existing = fasts.getById(id) ?: return EditResult.NotFound
        if (!targetEndAt.isAfter(startAt)) return EditResult.Invalid
        if (endedAt != null && endedAt.isBefore(startAt)) return EditResult.Invalid
        if (existing.isActive && endedAt != null) return EditResult.Invalid
        if (existing.isActive && startAt.isAfter(clock.instant())) return EditResult.Invalid

        val newPlanned = Duration.between(startAt, targetEndAt)
        if (newPlanned > existing.plannedDuration) {
            val decision = evaluate(newPlanned)
            if (decision is StartDecision.Blocked) return EditResult.Blocked(decision)
        }
        val updated = existing.editTimes(startAt = startAt, targetEndAt = targetEndAt, endedAt = endedAt)
        fasts.update(updated)
        return EditResult.Updated(updated)
    }

    private suspend fun evaluate(planned: Duration): StartDecision {
        val record = screening.getLatest()
        return FastPolicy.evaluateStart(record?.gateResult, record?.clinicianAcknowledged ?: false, planned)
    }
}
