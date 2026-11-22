package com.fastingshift.domain

import com.fastingshift.domain.ramadan.Hijri
import com.fastingshift.domain.ramadan.HijriDate
import com.fastingshift.domain.ramadan.HijriMonth
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Expected values are what the JDK's Umm al-Qura tables return. Local sighting authorities
 * (MUIS, JAKIM, Kemenag) can differ by a day either way, which the user's offset covers; these
 * tests pin the calculated calendar, not any particular announcement.
 */
class HijriTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun `1 Ramadan 1447 is 18 February 2026 (Umm al-Qura)`() {
        assertEquals(HijriDate(1447, HijriMonth.RAMADAN, 1, 30), Hijri.of(d("2026-02-18")))
        assertEquals(HijriDate(1447, HijriMonth.SHABAN, 29, 29), Hijri.of(d("2026-02-17")))
        assertEquals(d("2026-02-18"), Hijri.toGregorian(1447, HijriMonth.RAMADAN, 1))
    }

    @Test fun `Ramadan 1447 has 30 days and Eid al-Fitr is 20 March 2026`() {
        assertEquals(HijriDate(1447, HijriMonth.RAMADAN, 30, 30), Hijri.of(d("2026-03-19")))
        assertEquals(HijriDate(1447, HijriMonth.SHAWWAL, 1, 29), Hijri.of(d("2026-03-20")))
    }

    @Test fun `Arafah 1447 is 26 May 2026 and Ashura 1448 is 25 June 2026`() {
        assertEquals(HijriDate(1447, HijriMonth.DHUL_HIJJAH, 9, 29), Hijri.of(d("2026-05-26")))
        assertEquals(HijriDate(1448, HijriMonth.MUHARRAM, 1, 29), Hijri.of(d("2026-06-16")))
        assertEquals(d("2026-06-25"), Hijri.toGregorian(1448, HijriMonth.MUHARRAM, 10))
    }

    @Test fun `offset shifts the Hijri date, as for local sighting`() {
        // Sighting a day later than Umm al-Qura (offset -1): 18 Feb is still Sha'ban.
        assertEquals(HijriDate(1447, HijriMonth.SHABAN, 29, 29), Hijri.of(d("2026-02-18"), offsetDays = -1))
        assertEquals(HijriDate(1447, HijriMonth.RAMADAN, 1, 30), Hijri.of(d("2026-02-19"), offsetDays = -1))
        assertEquals(HijriDate(1447, HijriMonth.RAMADAN, 1, 30), Hijri.of(d("2026-02-17"), offsetDays = 1))
        // Gregorian date of 1 Ramadan moves the other way.
        assertEquals(d("2026-02-19"), Hijri.toGregorian(1447, HijriMonth.RAMADAN, 1, offsetDays = -1))
        assertEquals(d("2026-02-17"), Hijri.toGregorian(1447, HijriMonth.RAMADAN, 1, offsetDays = 1))
    }

    @Test fun `offset is clamped to plus or minus two days`() {
        assertEquals(Hijri.of(d("2026-02-18"), 2), Hijri.of(d("2026-02-18"), 9))
        assertEquals(Hijri.of(d("2026-02-18"), -2), Hijri.of(d("2026-02-18"), -9))
    }

    @Test fun `helpers`() {
        assertTrue(Hijri.isRamadan(d("2026-03-01")))
        assertFalse(Hijri.isRamadan(d("2026-03-20")))
        assertFalse(Hijri.isRamadan(d("2026-03-19"), offsetDays = 1))
        assertEquals(12, Hijri.dayOfMonth(d("2026-11-22")))
        assertEquals(HijriMonth.JUMADA_AL_AKHIRAH, Hijri.of(d("2026-11-22"))?.month)
        assertEquals(9, HijriMonth.RAMADAN.number)
        assertEquals(HijriMonth.DHUL_HIJJAH, HijriMonth.of(12))
    }

    @Test fun `dates outside the supported range return null`() {
        assertNull(Hijri.of(d("1800-01-01")))
        assertNull(Hijri.of(LocalDate.of(2300, 1, 1)))
        assertNull(Hijri.dayOfMonth(d("1800-01-01")))
        assertFalse(Hijri.isRamadan(d("1800-01-01")))
        assertNull(Hijri.toGregorian(1200, HijriMonth.RAMADAN, 1))
        // A 30th in a 29-day month doesn't exist.
        assertNull(Hijri.toGregorian(1447, HijriMonth.SHAWWAL, 30))
    }
}
