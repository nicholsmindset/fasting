package com.fastingshift.domain.model

import java.time.Duration
import java.time.Instant

enum class FastStatus {
    /** In progress. */
    ACTIVE,

    /** Ended at or after the planned end. Counts towards consistency streaks. */
    COMPLETED,

    /** Ended before the planned end. Never shamed; simply not counted as a completed plan. */
    ENDED_EARLY,
}

/**
 * A fast is stored as data, not as a running process: a start instant and a target end
 * instant (both UTC). Every surface (UI, widgets, notifications) derives elapsed time from
 * the clock, so a fast survives app kill, reboot and time-zone changes.
 */
data class Fast(
    val id: Long = 0,
    val startAt: Instant,
    val targetEndAt: Instant,
    val endedAt: Instant? = null,
    val protocolId: String,
    val status: FastStatus = FastStatus.ACTIVE,
    val note: String? = null,
    /** How the user felt when it ended, 1 (rough) to 5 (great); optional reflection. */
    val mood: Int? = null,
    /** Regular protocol fast, or a Ramadan / Sunnah / qada (make-up) fast. */
    val kind: FastKind = FastKind.REGULAR,
) {
    init {
        require(targetEndAt.isAfter(startAt)) { "targetEndAt must be after startAt" }
        if (endedAt != null) {
            require(!endedAt.isBefore(startAt)) { "endedAt must not be before startAt" }
            require(status != FastStatus.ACTIVE) { "an ended fast cannot be ACTIVE" }
        } else {
            require(status == FastStatus.ACTIVE) { "a fast without endedAt must be ACTIVE" }
        }
        require(mood == null || mood in MOOD_RANGE) { "mood must be 1..5" }
    }

    val isActive: Boolean get() = endedAt == null

    val plannedDuration: Duration get() = Duration.between(startAt, targetEndAt)

    /** Actual length of an ended fast, or null while active. */
    val actualDuration: Duration? get() = endedAt?.let { Duration.between(startAt, it) }

    /** Ends this fast at [at], deriving COMPLETED or ENDED_EARLY. */
    fun end(at: Instant): Fast {
        check(isActive) { "fast $id has already ended" }
        return copy(endedAt = at, status = statusFor(at, targetEndAt))
    }

    /**
     * Edits the times of an active or past fast. The planned duration is kept unless a new
     * [targetEndAt] is given. Status is re-derived for ended fasts.
     */
    fun editTimes(
        startAt: Instant = this.startAt,
        targetEndAt: Instant = startAt.plus(plannedDuration),
        endedAt: Instant? = this.endedAt,
    ): Fast {
        require(this.endedAt != null || endedAt == null) { "use end() to end an active fast" }
        val status = if (endedAt == null) FastStatus.ACTIVE else statusFor(endedAt, targetEndAt)
        return copy(startAt = startAt, targetEndAt = targetEndAt, endedAt = endedAt, status = status)
    }

    companion object {
        val MOOD_RANGE = 1..5

        fun statusFor(endedAt: Instant, targetEndAt: Instant): FastStatus =
            if (endedAt.isBefore(targetEndAt)) FastStatus.ENDED_EARLY else FastStatus.COMPLETED
    }
}
