package com.fastingshift.domain.model

import java.time.Instant
import java.time.LocalDate

/** What a fast is for. Religious kinds run suhoor to maghrib; regular fasts follow a protocol. */
enum class FastKind { REGULAR, RAMADAN, SUNNAH, QADA }

/** Optional metabolic readings the user logs (glucose also syncs with Health Connect). */
enum class MetricType { GLUCOSE, KETONE }

enum class MetricUnit { MG_DL, MMOL_L }

data class MetricLog(
    val id: Long = 0,
    val at: Instant,
    val type: MetricType,
    val value: Double,
    val unit: MetricUnit,
    val source: RecordSource = RecordSource.MANUAL,
    val note: String? = null,
) {
    init {
        require(value.isFinite() && value > 0) { "metric value must be positive" }
        require(type != MetricType.KETONE || unit == MetricUnit.MMOL_L) { "ketones are logged in mmol/L" }
    }

    /** The value in mmol/L (glucose mg/dL ÷ 18.016). */
    val mmolPerL: Double get() = if (unit == MetricUnit.MMOL_L) value else value / GLUCOSE_MG_DL_PER_MMOL_L

    companion object {
        const val GLUCOSE_MG_DL_PER_MMOL_L = 18.016
    }
}

/** Ramadan days owed (qada) for one Hijri year. Repayments are recorded explicitly against the owed year. */
data class QadaDebt(val hijriYear: Int, val daysOwed: Int) {
    init {
        require(daysOwed in 0..30) { "a Ramadan has at most 30 days" }
    }
}

/** What a challenge asks for each day (append-only: stored by name). */
enum class ChallengeType {
    /** Complete the challenge's protocol every day. */
    DAILY_PROTOCOL,

    /** Fast every Monday and Thursday. */
    SUNNAH_MON_THU,

    /** Fast the white days (13th–15th of each Hijri month). */
    AYYAMUL_BIDH,
}

/**
 * A challenge joined on this device. Challenges are shared as links holding only these
 * parameters; there is no server, so progress is always computed locally from fasts.
 */
data class Challenge(
    val id: Long = 0,
    val type: ChallengeType,
    val protocolId: String? = null,
    val startDate: LocalDate,
    val lengthDays: Int,
    val name: String? = null,
    val joinedAt: Instant,
    val leftAt: Instant? = null,
    /** Private to this participant. The invitation always keeps its original plan. */
    val followsOwnPlan: Boolean = false,
) {
    init {
        require(lengthDays in 1..366) { "challenge length must be 1..366 days" }
        require(type != ChallengeType.DAILY_PROTOCOL || protocolId != null) { "a daily challenge needs a protocol" }
    }

    val endDate: LocalDate get() = startDate.plusDays(lengthDays - 1L)
}
