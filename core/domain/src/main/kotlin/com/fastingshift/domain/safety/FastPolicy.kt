package com.fastingshift.domain.safety

import java.time.Duration

/** What the user must see or confirm before a fast starts (PRD F7, SR-3, SR-8). */
enum class StartRequirement {
    /** Hydration, electrolytes, medications and who to call. Fasts of 24h or more. */
    EXTENDED_FAST_CHECKLIST,

    /** Explicit acknowledgement for fasts over 72h. */
    OVER_72H_ACKNOWLEDGEMENT,

    /** "Not medical advice" notice at the start of extended fasts. */
    MEDICAL_DISCLAIMER,
}

enum class BlockReason {
    /** Hard-block screening result: education-only mode. */
    EDUCATION_ONLY,

    /** Soft-gate result without a recorded clinician acknowledgement. */
    NEEDS_CLINICIAN_ACKNOWLEDGEMENT,

    /** Soft-gate users (and users not yet screened) cannot start fasts over 24h. */
    EXCEEDS_GATED_LIMIT,

    /** Over the absolute 120h limit or under 1h. */
    OUT_OF_RANGE,

    /** No safety screening on record yet: SR-1 must be checked before any fast. */
    NEEDS_SCREENING,
}

sealed interface StartDecision {
    data class Allowed(val requirements: Set<StartRequirement>) : StartDecision
    data class Blocked(val reason: BlockReason) : StartDecision
}

enum class RefeedingTier {
    /** 48h to under 72h. */
    STANDARD,

    /** 72h to under 96h. */
    EXTENDED,

    /** 96h or more. */
    PROLONGED,
}

/**
 * The always-on safety rules. None of this is ever behind the paywall and none of it can be
 * switched off for fasts of 24h or more.
 */
object FastPolicy {
    val MIN_FAST: Duration = Duration.ofHours(1)
    val MAX_FAST: Duration = Duration.ofHours(120)
    val GATED_MAX: Duration = Duration.ofHours(24)
    val EXTENDED_THRESHOLD: Duration = Duration.ofHours(24)
    val OVER_72H_THRESHOLD: Duration = Duration.ofHours(72)
    val CHECK_IN_INTERVAL: Duration = Duration.ofHours(8)
    val REFEEDING_THRESHOLD: Duration = Duration.ofHours(48)

    /**
     * Decides whether a fast of [planned] length may start.
     *
     * @param gate the latest screening result, or null if the user has not been screened yet.
     *   Unscreened users cannot start any fast: the hard-block questions (SR-1) come first.
     * @param clinicianAcknowledged whether a soft-gated user has recorded the clinician
     *   acknowledgement.
     */
    fun evaluateStart(gate: GateResult?, clinicianAcknowledged: Boolean, planned: Duration): StartDecision {
        if (planned < MIN_FAST || planned > MAX_FAST) return StartDecision.Blocked(BlockReason.OUT_OF_RANGE)
        when (gate) {
            GateResult.BLOCK -> return StartDecision.Blocked(BlockReason.EDUCATION_ONLY)
            GateResult.SOFT -> {
                if (!clinicianAcknowledged) return StartDecision.Blocked(BlockReason.NEEDS_CLINICIAN_ACKNOWLEDGEMENT)
                if (planned > GATED_MAX) return StartDecision.Blocked(BlockReason.EXCEEDS_GATED_LIMIT)
            }
            null -> return StartDecision.Blocked(BlockReason.NEEDS_SCREENING)
            GateResult.OK -> Unit
        }
        return StartDecision.Allowed(requirementsFor(planned))
    }

    fun maxAllowed(gate: GateResult?, clinicianAcknowledged: Boolean): Duration? = when (gate) {
        GateResult.BLOCK -> null
        GateResult.SOFT -> if (clinicianAcknowledged) GATED_MAX else null
        null -> null
        GateResult.OK -> MAX_FAST
    }

    fun requirementsFor(planned: Duration): Set<StartRequirement> = buildSet {
        if (planned >= EXTENDED_THRESHOLD) {
            add(StartRequirement.EXTENDED_FAST_CHECKLIST)
            add(StartRequirement.MEDICAL_DISCLAIMER)
        }
        if (planned > OVER_72H_THRESHOLD) add(StartRequirement.OVER_72H_ACKNOWLEDGEMENT)
    }

    /** Symptom check-ins run during fasts of 24h or more. */
    fun requiresCheckIns(planned: Duration): Boolean = planned >= EXTENDED_THRESHOLD

    /** Refeeding guidance shown automatically when a fast of 48h or more ends (SR-5). */
    fun refeedingTier(actual: Duration): RefeedingTier? = when {
        actual < REFEEDING_THRESHOLD -> null
        actual < Duration.ofHours(72) -> RefeedingTier.STANDARD
        actual < Duration.ofHours(96) -> RefeedingTier.EXTENDED
        else -> RefeedingTier.PROLONGED
    }
}
