package com.fastingshift.domain.timer

import com.fastingshift.domain.model.Fast
import java.time.Duration
import java.time.Instant

/** Snapshot of a fast at a point in time. Everything is derived; nothing is stored. */
data class TimerState(
    val elapsed: Duration,
    /** Time left until the planned end; zero once reached. */
    val remaining: Duration,
    /** 0.0–1.0 progress towards the planned end (capped at 1.0). */
    val progress: Float,
    val isTargetReached: Boolean,
    /** How far past the planned end; zero if not reached. */
    val pastTarget: Duration,
    /** Next user milestone not yet reached, measured from the start of the fast. */
    val nextMilestone: Duration?,
)

object FastTimer {
    /**
     * Computes the timer state for [fast] at [now]. For an ended fast, time stops at its end.
     * If the device clock reads earlier than the start (clock change, edited start time),
     * elapsed is clamped to zero rather than going negative.
     */
    fun state(fast: Fast, now: Instant, milestones: List<Duration> = emptyList()): TimerState {
        val reference = fast.endedAt ?: now
        val elapsed = Duration.between(fast.startAt, reference).coerceAtLeast(Duration.ZERO)
        val planned = fast.plannedDuration
        val remaining = (planned - elapsed).coerceAtLeast(Duration.ZERO)
        val reached = elapsed >= planned
        val progress = (elapsed.toMillis().toDouble() / planned.toMillis()).coerceIn(0.0, 1.0).toFloat()
        val next = milestones.sorted().firstOrNull { it > elapsed }
        return TimerState(
            elapsed = elapsed,
            remaining = remaining,
            progress = progress,
            isTargetReached = reached,
            pastTarget = if (reached) elapsed - planned else Duration.ZERO,
            nextMilestone = next,
        )
    }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this
