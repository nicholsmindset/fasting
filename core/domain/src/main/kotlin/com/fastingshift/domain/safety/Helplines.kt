package com.fastingshift.domain.safety

enum class HelplineAvailability { ALL_DAY, CALL_OR_TEXT_ALL_DAY }

data class Helpline(
    val name: String,
    val phone: String,
    val availability: HelplineAvailability? = null,
    val hoursWebsite: String? = null,
    val extension: Int? = null,
)

/**
 * Support lines shown in education-only mode, the red-flag screen and settings (SR-6).
 * Singapore first. VERIFY EVERY NUMBER before launch; numbers change.
 */
object Helplines {
    private val byRegion = mapOf(
        "SG" to listOf(
            Helpline("Samaritans of Singapore (SOS)", "1767", HelplineAvailability.ALL_DAY),
            Helpline("national mindline 1771", "1771", HelplineAvailability.ALL_DAY),
        ),
        "MY" to listOf(Helpline("Befrienders KL", "+60 3-7627 2929", HelplineAvailability.ALL_DAY)),
        "ID" to listOf(Helpline("Healing119.id", "119", extension = 8)),
        "CN" to listOf(Helpline("全国统一心理援助热线", "12356")),
        "US" to listOf(Helpline("988 Suicide & Crisis Lifeline", "988", HelplineAvailability.CALL_OR_TEXT_ALL_DAY)),
        "GB" to listOf(
            Helpline("Beat eating disorder helpline (England)", "0808 801 0677", hoursWebsite = "beateatingdisorders.org.uk"),
            Helpline("Beat eating disorder helpline (Scotland)", "0808 801 0432", hoursWebsite = "beateatingdisorders.org.uk"),
            Helpline("Beat eating disorder helpline (Wales)", "0808 801 0433", hoursWebsite = "beateatingdisorders.org.uk"),
            Helpline("Beat eating disorder helpline (Northern Ireland)", "0808 801 0434", hoursWebsite = "beateatingdisorders.org.uk"),
        ),
        "AU" to listOf(Helpline("Butterfly Foundation", "1800 33 4673", hoursWebsite = "butterfly.org.au")),
    )

    fun forRegion(regionCode: String?): List<Helpline> =
        regionCode?.uppercase()?.let(byRegion::get) ?: emptyList()
}
