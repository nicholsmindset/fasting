package com.fastingshift.domain.safety

/** Symptom check-in answers (PRD F7, SR-4). */
enum class Symptom(val isRedFlag: Boolean) {
    FAINTING(true),
    CHEST_PAIN(true),
    PALPITATIONS(true),
    CONFUSION(true),
    SEVERE_WEAKNESS(true),
    LIGHTHEADED(false),
    HEADACHE(false),
    NAUSEA(false),
    MUSCLE_CRAMPS(false),
    FATIGUE(false),
    HUNGER(false),
    IRRITABILITY(false),
}

object SymptomCheck {
    /** True if any answer must open the full-screen "End your fast and seek medical care" screen. */
    fun hasRedFlag(symptoms: Set<Symptom>): Boolean = symptoms.any { it.isRedFlag }
}

/**
 * Local emergency numbers for the red-flag screen. Works offline. Singapore first.
 * Region codes are ISO 3166-1 alpha-2. Verify each number during clinician/legal review.
 */
object EmergencyNumbers {
    const val FALLBACK = "112"

    private val byRegion = mapOf(
        "SG" to "995",
        "MY" to "999",
        "ID" to "112",
        "PH" to "911",
        "TH" to "1669",
        "VN" to "115",
        "US" to "911",
        "CA" to "911",
        "GB" to "999",
        "AU" to "000",
        "NZ" to "111",
        "IN" to "112",
    )

    fun forRegion(regionCode: String?): String =
        regionCode?.uppercase()?.let(byRegion::get) ?: FALLBACK
}
