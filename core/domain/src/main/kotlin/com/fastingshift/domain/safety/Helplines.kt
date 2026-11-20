package com.fastingshift.domain.safety

data class Helpline(val name: String, val phone: String, val note: String)

/**
 * Support lines shown in education-only mode, the red-flag screen and settings (SR-6).
 * Singapore first. VERIFY EVERY NUMBER before launch; numbers change.
 */
object Helplines {
    private val byRegion = mapOf(
        "SG" to listOf(
            Helpline("Samaritans of Singapore (SOS)", "1767", "24 hours"),
            Helpline("IMH Mental Health Helpline", "6389 2222", "24 hours"),
        ),
        "MY" to listOf(Helpline("Befrienders KL", "+60 3-7627 2929", "24 hours")),
        "US" to listOf(Helpline("988 Suicide & Crisis Lifeline", "988", "Call or text, 24 hours")),
        "GB" to listOf(Helpline("Beat eating disorder helpline", "0808 801 0677", "See beateatingdisorders.org.uk for hours")),
        "AU" to listOf(Helpline("Butterfly Foundation", "1800 33 4673", "See butterfly.org.au for hours")),
    )

    fun forRegion(regionCode: String?): List<Helpline> =
        regionCode?.uppercase()?.let(byRegion::get) ?: byRegion.getValue("SG")
}
