package com.fastingshift.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class ProtocolType {
    /** Daily fasting/eating window such as 16:8. */
    WINDOW,

    /** A single fast of a set length such as 36h. */
    DURATION,

    /** A weekly schedule such as 5:2 or alternate-day fasting. */
    WEEKLY,
}

data class Protocol(
    val id: String,
    val name: String,
    val type: ProtocolType,
    val fastHours: Int,
    /** Eating window for WINDOW protocols; null otherwise. */
    val eatHours: Int? = null,
    val isCustom: Boolean = false,
)

/**
 * A recurring plan. Used for weekly protocols (5:2, ADF) in the MVP and left general enough
 * for later phases (shift-worker schedules, prayer-time-aware Ramadan schedules).
 */
data class Schedule(
    val id: Long = 0,
    val protocolId: String,
    val days: Set<DayOfWeek>,
    val startTime: LocalTime,
    val enabled: Boolean = true,
)
