package com.fastingshift.app.ui.ramadan

import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPhase
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** What the Ramadan card shows right now; turned into strings by the composable. */
sealed interface RamadanStatus {
    /** Between Fajr and Maghrib: "Iftar in 3h 12m", with progress through the fasting day. */
    data class Fasting(val iftar: Instant, val remaining: Countdown, val progress: Float) : RamadanStatus

    /** After Maghrib or before Fajr: "Suhoor ends 5:41 AM", "in 6h 05m". */
    data class Eating(val suhoorEnds: Instant, val remaining: Countdown) : RamadanStatus
}

/** Whole hours and minutes until an event, rounded up so "in 0m" never shows. */
data class Countdown(val hours: Long, val minutes: Long) {
    companion object {
        fun until(now: Instant, target: Instant): Countdown {
            val seconds = Duration.between(now, target).seconds.coerceAtLeast(0)
            val totalMinutes = (seconds + 59) / 60
            return Countdown(totalMinutes / 60, totalMinutes % 60)
        }
    }
}

object RamadanText {
    /** Pure decision for the card: which event is next, how long until it, and fasting progress. */
    fun status(phase: RamadanPhase, now: Instant): RamadanStatus = when (phase) {
        is RamadanPhase.Fasting -> {
            val total = Duration.between(phase.suhoorEnded, phase.iftar).toMillis().toFloat()
            val done = Duration.between(phase.suhoorEnded, now).toMillis().toFloat()
            RamadanStatus.Fasting(
                iftar = phase.iftar,
                remaining = Countdown.until(now, phase.iftar),
                progress = if (total <= 0f) 0f else (done / total).coerceIn(0f, 1f),
            )
        }
        is RamadanPhase.Eating -> RamadanStatus.Eating(phase.suhoorEnds, Countdown.until(now, phase.suhoorEnds))
    }

    /** Display name for a calculation method ("MUIS", "JAKIM", "Kemenag", "MWL"). */
    fun methodName(method: PrayerMethod): String = when (method) {
        PrayerMethod.MUIS -> "MUIS"
        PrayerMethod.JAKIM -> "JAKIM"
        PrayerMethod.KEMENAG -> "Kemenag"
        PrayerMethod.MWL -> "MWL"
    }

    /** Country name for a city's ISO code, in the user's language. */
    fun countryName(code: String, locale: Locale = Locale.getDefault()): String =
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { code }
}

/** City picker: cities matching [query] (name or country, case-insensitive), grouped by country in list order. */
fun filterCities(cities: List<City>, query: String, countryName: (String) -> String = { RamadanText.countryName(it) }): List<Pair<String, List<City>>> {
    val q = query.trim()
    val matches = if (q.isEmpty()) {
        cities
    } else {
        cities.filter { c ->
            c.name.contains(q, ignoreCase = true) || countryName(c.country).contains(q, ignoreCase = true) || c.country.equals(q, ignoreCase = true)
        }
    }
    return matches.groupBy { it.country }.map { (code, list) -> countryName(code) to list }
}
