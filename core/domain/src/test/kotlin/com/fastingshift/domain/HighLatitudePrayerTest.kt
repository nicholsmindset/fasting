package com.fastingshift.domain

import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPhase
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.reminders.ReminderKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighLatitudePrayerTest {
    private val solstice = LocalDate.of(2026, 6, 21)

    @Test fun `London in June has a sunset reminder but no calculated dawn or fasting countdown`() {
        val city = requireNotNull(Cities.byId("gb_london"))
        val now = solstice.atStartOfDay(city.zone).toInstant()
        val day = RamadanPlanner.day(city, PrayerMethod.MWL, solstice)
        assertNull(day.fajr)
        assertNull(day.isha)
        assertNotNull(day.maghrib)
        assertNull(RamadanPlanner.phase(city, PrayerMethod.MWL, now))
        val reminder = RamadanPlanner.reminders(city, PrayerMethod.MWL, now).single()
        assertEquals(ReminderKind.IFTAR, reminder.kind)
        assertEquals(day.maghrib, reminder.triggerAt)
    }

    @Test fun `a polar day supplies neither reminder`() {
        val city = City("test_tromso", "Tromsø", "NO", 69.65, 18.96, "Europe/Oslo")
        val now = solstice.atStartOfDay(city.zone).toInstant()
        val day = RamadanPlanner.day(city, PrayerMethod.MWL, solstice)
        assertNull(day.sunrise)
        assertNull(day.maghrib)
        assertNull(RamadanPlanner.phase(city, PrayerMethod.MWL, now))
        assertTrue(RamadanPlanner.reminders(city, PrayerMethod.MWL, now).isEmpty())
    }

    @Test fun `ordinary Kuala Lumpur day retains both boundaries and reminders`() {
        val city = requireNotNull(Cities.byId("my_kuala_lumpur"))
        val method = Cities.defaultMethod(city)
        val midnight = solstice.atStartOfDay(city.zone).toInstant()
        val day = RamadanPlanner.day(city, method, solstice)
        val dawn = assertNotNull(day.fajr)
        val sunset = assertNotNull(day.maghrib)
        val phase = assertIs<RamadanPhase.Fasting>(RamadanPlanner.phase(city, method, dawn))
        assertEquals(sunset, phase.iftar)
        val reminders = RamadanPlanner.reminders(city, method, midnight).associate { it.kind to it.triggerAt }
        assertEquals(mapOf(ReminderKind.SUHOOR to dawn.minus(RamadanPlanner.SUHOOR_REMINDER_BEFORE), ReminderKind.IFTAR to sunset), reminders)
    }
}
