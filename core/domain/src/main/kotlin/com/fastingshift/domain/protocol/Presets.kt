package com.fastingshift.domain.protocol

import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.ProtocolType

/** Built-in protocols (PRD F2). Every protocol is free. */
object Presets {
    const val CUSTOM_MIN_HOURS = 1
    const val CUSTOM_MAX_HOURS = 120

    private fun window(fast: Int) =
        Protocol(id = "window_${fast}_${24 - fast}", name = "$fast:${24 - fast}", type = ProtocolType.WINDOW, fastHours = fast, eatHours = 24 - fast)

    private fun duration(hours: Int) =
        Protocol(id = "duration_$hours", name = "${hours}h", type = ProtocolType.DURATION, fastHours = hours)

    val W12_12 = window(12)
    val W14_10 = window(14)
    val W16_8 = window(16)
    val W18_6 = window(18)
    val W20_4 = window(20)
    val OMAD = Protocol(id = "omad", name = "OMAD", type = ProtocolType.WINDOW, fastHours = 23, eatHours = 1)
    val D24 = duration(24)
    val D36 = duration(36)
    val D48 = duration(48)
    val D72 = duration(72)

    /** 5:2: two non-consecutive reduced-intake days a week. Fast days are tracked as 24h fasts. */
    val FIVE_TWO = Protocol(id = "weekly_5_2", name = "5:2", type = ProtocolType.WEEKLY, fastHours = 24)

    /** Alternate-day fasting, tracked as 24h fasts on alternating days. */
    val ADF = Protocol(id = "weekly_adf", name = "Alternate day", type = ProtocolType.WEEKLY, fastHours = 24)

    val all: List<Protocol> = listOf(W12_12, W14_10, W16_8, W18_6, W20_4, OMAD, D24, D36, D48, D72, FIVE_TWO, ADF)

    fun byId(id: String): Protocol? = all.firstOrNull { it.id == id } ?: parseCustom(id)

    fun custom(hours: Int): Protocol {
        require(hours in CUSTOM_MIN_HOURS..CUSTOM_MAX_HOURS) { "custom fasts must be $CUSTOM_MIN_HOURS–$CUSTOM_MAX_HOURS hours" }
        return Protocol(id = "custom_$hours", name = "${hours}h custom", type = ProtocolType.DURATION, fastHours = hours, isCustom = true)
    }

    private fun parseCustom(id: String): Protocol? =
        id.removePrefix("custom_").takeIf { id.startsWith("custom_") }?.toIntOrNull()
            ?.takeIf { it in CUSTOM_MIN_HOURS..CUSTOM_MAX_HOURS }
            ?.let(::custom)
}
