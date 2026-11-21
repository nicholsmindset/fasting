package com.fastingshift.domain.hydration

import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Daily water goal (PRD F6). A general wellness default, not medical advice; always editable. */
object WaterGoal {
    const val ML_PER_KG = 35
    const val FALLBACK_ML = 2_000
    const val MIN_ML = 1_500
    const val MAX_ML = 4_000

    /** 35 ml per kg, rounded to 50 ml, kept within 1.5–4 L. Fallback 2 L with no weight. */
    fun defaultMl(weightKg: Double?): Int {
        if (weightKg == null || !weightKg.isFinite() || weightKg <= 0) return FALLBACK_ML
        val raw = weightKg * ML_PER_KG
        return ((raw / 50).roundToInt() * 50).coerceIn(MIN_ML, MAX_ML)
    }

    fun toMl(log: WaterLog): Double = if (log.unit == VolumeUnit.ML) log.value else log.value * 29.5735

    /** Total water per local day. */
    fun totalsByDay(logs: List<WaterLog>, zone: ZoneId): Map<LocalDate, Double> =
        logs.groupBy { it.at.atZone(zone).toLocalDate() }.mapValues { (_, l) -> l.sumOf(::toMl) }

    /** Water logged within [from, to), e.g. during a fast. */
    fun totalBetween(logs: List<WaterLog>, from: Instant, to: Instant): Double =
        logs.filter { !it.at.isBefore(from) && it.at.isBefore(to) }.sumOf(::toMl)

    fun progress(todayMl: Double, goalMl: Int): Float = if (goalMl <= 0) 0f else (todayMl / goalMl).toFloat().coerceIn(0f, 1f)
}
