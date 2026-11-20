package com.fastingshift.domain.model

import java.time.Instant

enum class RecordSource { MANUAL, HEALTH_CONNECT }

enum class WeightUnit { KG, LB }

enum class VolumeUnit { ML, FL_OZ }

enum class Electrolyte { SODIUM, POTASSIUM, MAGNESIUM }

data class WeightLog(
    val id: Long = 0,
    val at: Instant,
    val value: Double,
    val unit: WeightUnit,
    val source: RecordSource = RecordSource.MANUAL,
)

data class WaterLog(
    val id: Long = 0,
    val at: Instant,
    val value: Double,
    val unit: VolumeUnit,
    val source: RecordSource = RecordSource.MANUAL,
)

/** Electrolyte intake in milligrams. */
data class ElectrolyteLog(
    val id: Long = 0,
    val at: Instant,
    val electrolyte: Electrolyte,
    val milligrams: Double,
    val source: RecordSource = RecordSource.MANUAL,
)
