package com.fastingshift.domain.safety

/**
 * Safety screening (PRD F1, SR-1, SR-2).
 *
 * The thresholds below are product rules pending clinician review. They are deliberately
 * conservative. The app does not diagnose; it decides which features to offer.
 */
enum class Medication {
    INSULIN,
    SULFONYLUREA,
    SGLT2_INHIBITOR,
    DIURETIC,
    BLOOD_PRESSURE,
    GLP1,
}

data class ScreeningAnswers(
    val ageYears: Int,
    val pregnantOrBreastfeeding: Boolean,
    val eatingDisorderHistory: Boolean,
    val type1Diabetes: Boolean,
    val medications: Set<Medication>,
    val heightCm: Double,
    val weightKg: Double,
) {
    init {
        require(ageYears in 0..130) { "age out of range" }
        require(heightCm > 0) { "height must be positive" }
        require(weightKg > 0) { "weight must be positive" }
    }

    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
}

enum class GateResult {
    /** No restrictions beyond the always-on safety layer. */
    OK,

    /** Clinician acknowledgement required; fasts over 24h locked. */
    SOFT,

    /** Education-only mode; no protocols offered. */
    BLOCK,
}

enum class GateReason(val result: GateResult) {
    UNDER_18(GateResult.BLOCK),
    PREGNANT_OR_BREASTFEEDING(GateResult.BLOCK),
    EATING_DISORDER_HISTORY(GateResult.BLOCK),
    LOW_BMI(GateResult.BLOCK),
    TYPE_1_DIABETES(GateResult.BLOCK),
    GLUCOSE_LOWERING_MEDICATION(GateResult.SOFT),
    SGLT2_INHIBITOR(GateResult.SOFT),
    BLOOD_PRESSURE_OR_DIURETIC(GateResult.SOFT),
    GLP1(GateResult.SOFT),
}

data class ScreeningOutcome(val result: GateResult, val reasons: Set<GateReason>)

object ScreeningEvaluator {
    const val MIN_AGE = 18
    const val MIN_BMI = 18.5

    fun evaluate(answers: ScreeningAnswers): ScreeningOutcome {
        val reasons = buildSet {
            if (answers.ageYears < MIN_AGE) add(GateReason.UNDER_18)
            if (answers.pregnantOrBreastfeeding) add(GateReason.PREGNANT_OR_BREASTFEEDING)
            if (answers.eatingDisorderHistory) add(GateReason.EATING_DISORDER_HISTORY)
            if (answers.bmi < MIN_BMI) add(GateReason.LOW_BMI)
            if (answers.type1Diabetes) add(GateReason.TYPE_1_DIABETES)
            val meds = answers.medications
            if (Medication.INSULIN in meds || Medication.SULFONYLUREA in meds) add(GateReason.GLUCOSE_LOWERING_MEDICATION)
            if (Medication.SGLT2_INHIBITOR in meds) add(GateReason.SGLT2_INHIBITOR)
            if (Medication.DIURETIC in meds || Medication.BLOOD_PRESSURE in meds) add(GateReason.BLOOD_PRESSURE_OR_DIURETIC)
            if (Medication.GLP1 in meds) add(GateReason.GLP1)
        }
        val result = when {
            reasons.any { it.result == GateResult.BLOCK } -> GateResult.BLOCK
            reasons.isNotEmpty() -> GateResult.SOFT
            else -> GateResult.OK
        }
        return ScreeningOutcome(result, reasons)
    }
}
