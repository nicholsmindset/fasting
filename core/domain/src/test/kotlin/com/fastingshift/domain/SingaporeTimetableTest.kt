package com.fastingshift.domain

import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.FastingTimeSource
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.PrayerTimes
import com.fastingshift.domain.ramadan.RamadanPhase
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.reminders.ReminderKind
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class SingaporeTimetableTest {
    private val city = requireNotNull(Cities.byId("sg_singapore"))
    private val method = PrayerMethod.MUIS

    @Test fun `every published day matches the MUIS annual tables`() {
        // Digests of ISO date, Subuh HH:mm and Maghrib HH:mm, newline terminated, from each PDF.
        val annualDigests = requireNotNull(javaClass.getResourceAsStream("/muis-reviewed-pdfs.csv"))
            .bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).map { line ->
                    val (year, digest) = line.split(',')
                    year.toInt() to digest
                }.toMap()
            }
        assertTrue(annualDigests.isNotEmpty())
        for ((year, expected) in annualDigests) {
            val digest = MessageDigest.getInstance("SHA-256")
            for (ordinal in 1..LocalDate.of(year, 1, 1).lengthOfYear()) {
                val date = LocalDate.ofYearDay(year, ordinal)
                val day = RamadanPlanner.day(city, method, date)
                assertEquals(FastingTimeSource.MUIS_TIMETABLE, day.fastingTimeSource, date.toString())
                val fajr = requireNotNull(day.fajr).atZone(city.zone)
                val maghrib = requireNotNull(day.maghrib).atZone(city.zone)
                assertEquals(date, fajr.toLocalDate())
                assertEquals(date, maghrib.toLocalDate())
                val row = "$date,${fajr.toLocalTime()},${maghrib.toLocalTime()}\n"
                digest.update(row.toByteArray(Charsets.UTF_8))
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals(expected, actual, "MUIS $year")
        }
    }

    @Test fun `Ramadan 1447 starts at published Subuh and changes phase at Maghrib`() {
        val date = LocalDate.of(2026, 2, 19)
        val fajr = date.atTime(5, 58).atZone(city.zone).toInstant()
        val maghrib = date.atTime(19, 22).atZone(city.zone).toInstant()
        val beforeFajr = assertIs<RamadanPhase.Eating>(RamadanPlanner.phase(city, method, fajr.minusSeconds(1)))
        assertEquals(fajr, beforeFajr.suhoorEnds)
        val atFajr = assertIs<RamadanPhase.Fasting>(RamadanPlanner.phase(city, method, fajr))
        assertEquals(maghrib, atFajr.iftar)
        assertEquals(FastingTimeSource.MUIS_TIMETABLE, atFajr.fastingTimeSource)

        val atMaghrib = assertIs<RamadanPhase.Eating>(RamadanPlanner.phase(city, method, maghrib))
        assertEquals(date.plusDays(1).atTime(5, 58).atZone(city.zone).toInstant(), atMaghrib.suhoorEnds)
        val reminders = RamadanPlanner.reminders(city, method, date.atStartOfDay(city.zone).toInstant())
        assertEquals(fajr.minusSeconds(30 * 60L), reminders.single { it.kind == ReminderKind.SUHOOR }.triggerAt)
        assertEquals(maghrib, reminders.single { it.kind == ReminderKind.IFTAR }.triggerAt)
    }

    @Test fun `other cities methods and unpublished years retain calculated times`() {
        val nearby = requireNotNull(Cities.byId("my_johor_bahru"))
        val covered = LocalDate.of(2027, 1, 1)
        val cases = listOf(
            Triple(nearby, method, covered),
            Triple(city, PrayerMethod.MWL, covered),
            Triple(city, method, LocalDate.of(2025, 12, 31)),
            Triple(city, method, LocalDate.of(2028, 1, 1)),
        )
        for ((location, selectedMethod, date) in cases) {
            val expected = PrayerTimes.compute(date, location.latitude, location.longitude, selectedMethod)
            assertEquals(FastingTimeSource.CALCULATED, expected.fastingTimeSource)
            assertEquals(expected, RamadanPlanner.day(location, selectedMethod, date))
        }
    }

    @Test fun `after sunset the source follows the upcoming fasting day across coverage boundaries`() {
        fun night(year: Int) = LocalDate.of(year, 12, 31).atTime(22, 0).atZone(city.zone).toInstant()
        val first = assertIs<RamadanPhase.Eating>(RamadanPlanner.phase(city, method, night(2025)))
        assertEquals(FastingTimeSource.MUIS_TIMETABLE, first.fastingTimeSource)
        assertEquals(LocalTime.of(5, 44), first.suhoorEnds.atZone(city.zone).toLocalTime())
        assertEquals(LocalTime.of(19, 11), first.nextIftar.atZone(city.zone).toLocalTime())

        val last = assertIs<RamadanPhase.Eating>(RamadanPlanner.phase(city, method, night(2027)))
        assertEquals(FastingTimeSource.CALCULATED, last.fastingTimeSource)
        assertEquals(LocalDate.of(2028, 1, 1), last.suhoorEnds.atZone(city.zone).toLocalDate())
    }
}
