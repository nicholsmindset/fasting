package com.fastingshift.domain.insights

import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit

/**
 * Rules for glucose and ketone readings the user types in. Ranges are plausibility checks for
 * typing mistakes, not clinical targets. Everything here is general information, not advice.
 */
object MetricReadings {
    /** Accepted glucose input in mg/dL. */
    val GLUCOSE_MG_DL = 20.0..600.0

    /** Accepted glucose input in mmol/L. */
    val GLUCOSE_MMOL_L = 1.1..33.3

    /** Accepted ketone input in mmol/L (ketones are always logged in mmol/L). */
    val KETONE_MMOL_L = 0.1..10.0

    /** Below this (mmol/L) the app suggests breaking the fast. */
    const val LOW_GLUCOSE_MMOL_L = 3.9

    /** Below this (mg/dL) the app suggests breaking the fast. */
    const val LOW_GLUCOSE_MG_DL = 70.0

    /** At or above this (mmol/L) the app suggests checking in with a clinician. */
    const val HIGH_KETONE_MMOL_L = 3.0

    /** The range a typed value must fall in, or null when the pair is not allowed. */
    fun range(type: MetricType, unit: MetricUnit): ClosedFloatingPointRange<Double>? = when (type) {
        MetricType.GLUCOSE -> if (unit == MetricUnit.MG_DL) GLUCOSE_MG_DL else GLUCOSE_MMOL_L
        MetricType.KETONE -> if (unit == MetricUnit.MMOL_L) KETONE_MMOL_L else null
    }

    fun isValid(type: MetricType, unit: MetricUnit, value: Double): Boolean =
        value.isFinite() && range(type, unit)?.contains(value) == true

    /**
     * Parses what the user typed ("5,4" or "5.4"). Returns null for anything that isn't a
     * plausible reading for [type] in [unit].
     */
    fun parse(text: String, type: MetricType, unit: MetricUnit): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { isValid(type, unit, it) }

    /** Below about 70 mg/dL / 3.9 mmol/L. */
    fun isLowGlucose(log: MetricLog): Boolean = log.type == MetricType.GLUCOSE &&
        if (log.unit == MetricUnit.MG_DL) log.value < LOW_GLUCOSE_MG_DL else log.value < LOW_GLUCOSE_MMOL_L

    fun isHighKetone(log: MetricLog): Boolean = log.type == MetricType.KETONE && log.value >= HIGH_KETONE_MMOL_L

    /** [mmol] (mmol/L) shown in [unit]. */
    fun glucoseIn(mmol: Double, unit: MetricUnit): Double =
        if (unit == MetricUnit.MMOL_L) mmol else mmol * MetricLog.GLUCOSE_MG_DL_PER_MMOL_L

    /** A reading's value in [unit]; ketones are always mmol/L. */
    fun valueIn(log: MetricLog, unit: MetricUnit): Double =
        if (log.type == MetricType.KETONE) log.value else glucoseIn(log.mmolPerL, unit)

    /**
     * The glucose unit to offer by default: the unit of the user's latest glucose reading,
     * or mg/dL in the US and mmol/L elsewhere.
     */
    fun defaultGlucoseUnit(latestGlucose: MetricLog?, countryCode: String?): MetricUnit =
        latestGlucose?.takeIf { it.type == MetricType.GLUCOSE }?.unit
            ?: if (countryCode.equals("US", ignoreCase = true)) MetricUnit.MG_DL else MetricUnit.MMOL_L
}
