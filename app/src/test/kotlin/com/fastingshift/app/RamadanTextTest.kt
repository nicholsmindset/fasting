package com.fastingshift.app

import com.fastingshift.app.ui.ramadan.Countdown
import com.fastingshift.app.ui.ramadan.RamadanStatus
import com.fastingshift.app.ui.ramadan.RamadanText
import com.fastingshift.app.ui.ramadan.filterCities
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.RamadanPhase
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RamadanTextTest {
    private val fajr = Instant.parse("2027-02-10T21:40:00Z")
    private val maghrib = Instant.parse("2027-02-11T11:20:00Z")

    @Test fun fastingShowsIftarCountdownAndProgress() {
        val now = maghrib.minus(Duration.ofHours(3)).minus(Duration.ofMinutes(12))
        val s = RamadanText.status(RamadanPhase.Fasting(fajr, maghrib), now) as RamadanStatus.Fasting
        assertEquals(maghrib, s.iftar)
        assertEquals(Countdown(3, 12), s.remaining)
        val expected = Duration.between(fajr, now).toMillis().toFloat() / Duration.between(fajr, maghrib).toMillis()
        assertEquals(expected, s.progress, 0.001f)
    }

    @Test fun eatingShowsSuhoorEnd() {
        val nextFajr = fajr.plus(Duration.ofDays(1))
        val now = nextFajr.minus(Duration.ofHours(6)).minus(Duration.ofMinutes(5))
        val s = RamadanText.status(RamadanPhase.Eating(nextFajr, maghrib.plus(Duration.ofDays(1))), now)
        assertTrue(s is RamadanStatus.Eating)
        s as RamadanStatus.Eating
        assertEquals(nextFajr, s.suhoorEnds)
        assertEquals(Countdown(6, 5), s.remaining)
    }

    @Test fun countdownRoundsUpPartialMinutesAndNeverGoesNegative() {
        val t = Instant.parse("2027-02-11T11:20:00Z")
        assertEquals(Countdown(0, 1), Countdown.until(t.minusSeconds(10), t))
        assertEquals(Countdown(0, 0), Countdown.until(t.plusSeconds(10), t))
        assertEquals(Countdown(1, 0), Countdown.until(t.minusSeconds(3600), t))
    }

    @Test fun progressIsClampedAtTheEdges() {
        val before = RamadanText.status(RamadanPhase.Fasting(fajr, maghrib), fajr.minusSeconds(60)) as RamadanStatus.Fasting
        val after = RamadanText.status(RamadanPhase.Fasting(fajr, maghrib), maghrib.plusSeconds(60)) as RamadanStatus.Fasting
        assertEquals(0f, before.progress)
        assertEquals(1f, after.progress)
    }

    private val names = mapOf("SG" to "Singapore", "MY" to "Malaysia", "ID" to "Indonesia")
    private fun country(code: String) = names[code] ?: code

    @Test fun emptyQueryListsEveryCityGroupedByCountryInOrder() {
        val groups = filterCities(Cities.all, "  ", ::country)
        assertEquals(Cities.all.size, groups.sumOf { it.second.size })
        assertEquals("Singapore", groups.first().first)
        assertEquals("Malaysia", groups[1].first)
        assertEquals(4, groups[1].second.size)
    }

    @Test fun queryMatchesCityNameCaseInsensitively() {
        val groups = filterCities(Cities.all, "kuala", ::country)
        assertEquals(listOf("Malaysia"), groups.map { it.first })
        assertEquals(listOf("my_kuala_lumpur"), groups.single().second.map { it.id })
    }

    @Test fun queryMatchesCountryName() {
        val groups = filterCities(Cities.all, "indo", ::country)
        assertEquals(listOf("Indonesia"), groups.map { it.first })
        assertEquals(5, groups.single().second.size)
    }

    @Test fun noMatchGivesEmptyList() {
        assertTrue(filterCities(Cities.all, "zzz", ::country).isEmpty())
    }
}
