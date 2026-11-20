package com.fastingshift.domain

import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.EmergencyNumbers
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.ScreeningEvaluator
import com.fastingshift.domain.safety.StartDecision
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.safety.SymptomCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafetyTest {
    private val healthy = ScreeningAnswers(
        ageYears = 40, pregnantOrBreastfeeding = false, eatingDisorderHistory = false,
        type1Diabetes = false, medications = emptySet(), heightCm = 175.0, weightKg = 75.0,
    )

    @Test fun `healthy adult passes`() {
        assertEquals(GateResult.OK, ScreeningEvaluator.evaluate(healthy).result)
    }

    @Test fun `each SR-1 answer hard-blocks`() {
        val cases = mapOf(
            healthy.copy(ageYears = 17) to GateReason.UNDER_18,
            healthy.copy(pregnantOrBreastfeeding = true) to GateReason.PREGNANT_OR_BREASTFEEDING,
            healthy.copy(eatingDisorderHistory = true) to GateReason.EATING_DISORDER_HISTORY,
            healthy.copy(weightKg = 55.0) to GateReason.LOW_BMI, // BMI ~18.0
            healthy.copy(type1Diabetes = true) to GateReason.TYPE_1_DIABETES,
        )
        cases.forEach { (answers, reason) ->
            val outcome = ScreeningEvaluator.evaluate(answers)
            assertEquals(GateResult.BLOCK, outcome.result, "$reason")
            assertTrue(reason in outcome.reasons)
        }
    }

    @Test fun `BMI exactly 18_5 is not blocked`() {
        // 18.5 * 2.0^2 = 74.0 kg at 200 cm.
        assertEquals(GateResult.OK, ScreeningEvaluator.evaluate(healthy.copy(heightCm = 200.0, weightKg = 74.0)).result)
    }

    @Test fun `each SR-2 medication soft-gates`() {
        Medication.entries.forEach { med ->
            assertEquals(GateResult.SOFT, ScreeningEvaluator.evaluate(healthy.copy(medications = setOf(med))).result, "$med")
        }
    }

    @Test fun `block wins over soft`() {
        val o = ScreeningEvaluator.evaluate(healthy.copy(ageYears = 16, medications = setOf(Medication.GLP1)))
        assertEquals(GateResult.BLOCK, o.result)
        assertEquals(setOf(GateReason.UNDER_18, GateReason.GLP1), o.reasons)
    }

    @Test fun `policy limits by gate`() {
        fun d(gate: GateResult?, ack: Boolean, h: Long) = FastPolicy.evaluateStart(gate, ack, hours(h))

        assertEquals(StartDecision.Blocked(BlockReason.EDUCATION_ONLY), d(GateResult.BLOCK, true, 12))
        assertEquals(StartDecision.Blocked(BlockReason.NEEDS_CLINICIAN_ACKNOWLEDGEMENT), d(GateResult.SOFT, false, 12))
        assertTrue(d(GateResult.SOFT, true, 24) is StartDecision.Allowed)
        assertEquals(StartDecision.Blocked(BlockReason.EXCEEDS_GATED_LIMIT), d(GateResult.SOFT, true, 25))
        assertTrue(d(null, false, 16) is StartDecision.Allowed)
        assertEquals(StartDecision.Blocked(BlockReason.EXCEEDS_GATED_LIMIT), d(null, false, 36))
        assertTrue(d(GateResult.OK, false, 120) is StartDecision.Allowed)
        assertEquals(StartDecision.Blocked(BlockReason.OUT_OF_RANGE), d(GateResult.OK, false, 121))
        assertEquals(StartDecision.Blocked(BlockReason.OUT_OF_RANGE), d(GateResult.OK, false, 0))
    }

    @Test fun `requirements scale with length`() {
        assertEquals(emptySet(), FastPolicy.requirementsFor(hours(23)))
        assertEquals(
            setOf(StartRequirement.EXTENDED_FAST_CHECKLIST, StartRequirement.MEDICAL_DISCLAIMER),
            FastPolicy.requirementsFor(hours(24)),
        )
        assertFalse(StartRequirement.OVER_72H_ACKNOWLEDGEMENT in FastPolicy.requirementsFor(hours(72)))
        assertTrue(StartRequirement.OVER_72H_ACKNOWLEDGEMENT in FastPolicy.requirementsFor(hours(73)))
    }

    @Test fun `refeeding tiers`() {
        assertNull(FastPolicy.refeedingTier(hours(47)))
        assertEquals(RefeedingTier.STANDARD, FastPolicy.refeedingTier(hours(48)))
        assertEquals(RefeedingTier.EXTENDED, FastPolicy.refeedingTier(hours(72)))
        assertEquals(RefeedingTier.PROLONGED, FastPolicy.refeedingTier(hours(96)))
    }

    @Test fun `red flags`() {
        assertTrue(SymptomCheck.hasRedFlag(setOf(Symptom.HUNGER, Symptom.CHEST_PAIN)))
        assertFalse(SymptomCheck.hasRedFlag(setOf(Symptom.HUNGER, Symptom.HEADACHE)))
        assertFalse(SymptomCheck.hasRedFlag(emptySet()))
        assertEquals(5, Symptom.entries.count { it.isRedFlag })
    }

    @Test fun `emergency numbers`() {
        assertEquals("995", EmergencyNumbers.forRegion("SG"))
        assertEquals("995", EmergencyNumbers.forRegion("sg"))
        assertEquals("112", EmergencyNumbers.forRegion("ZZ"))
        assertEquals("112", EmergencyNumbers.forRegion(null))
    }
}
