package com.fastingshift.domain

import com.fastingshift.domain.ramadan.HijriMonth
import com.fastingshift.domain.ramadan.NoFastReason
import com.fastingshift.domain.ramadan.Sunnah
import com.fastingshift.domain.ramadan.SunnahType
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dates use the JDK's Umm al-Qura calendar with no offset (see HijriTest). */
class SunnahTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val sg = ZoneId.of("Asia/Singapore")
    private val all = SunnahType.entries.toSet()

    @Test fun `Monday and Thursday`() {
        assertEquals(setOf(SunnahType.MON_THU), Sunnah.typesFor(d("2026-11-26"))) // Thursday, 16 Jumada II
        assertEquals(setOf(SunnahType.MON_THU), Sunnah.typesFor(d("2026-02-16"))) // Monday, 28 Sha'ban
        assertEquals(emptySet(), Sunnah.typesFor(d("2026-11-22"))) // Sunday, 12 Jumada II
    }

    @Test fun `white days are the 13th to 15th`() {
        assertEquals(setOf(SunnahType.AYYAMUL_BIDH, SunnahType.MON_THU), Sunnah.typesFor(d("2026-11-23"))) // Monday, 13th
        assertEquals(setOf(SunnahType.AYYAMUL_BIDH), Sunnah.typesFor(d("2026-11-24")))
        assertEquals(setOf(SunnahType.AYYAMUL_BIDH), Sunnah.typesFor(d("2026-11-25")))
        // The offset moves them: with +1 the 13th is a day earlier.
        assertEquals(setOf(SunnahType.AYYAMUL_BIDH), Sunnah.typesFor(d("2026-11-22"), offsetDays = 1))
        assertEquals(setOf(SunnahType.MON_THU), Sunnah.typesFor(d("2026-11-26"), offsetDays = 0))
    }

    @Test fun `Arafah, Eid al-Adha and tashreeq`() {
        assertEquals(setOf(SunnahType.ARAFAH), Sunnah.typesFor(d("2026-05-26")))
        assertEquals(NoFastReason.EID_AL_ADHA, Sunnah.noFastReason(d("2026-05-27")))
        for (day in listOf("2026-05-28", "2026-05-29", "2026-05-30")) {
            assertEquals(NoFastReason.TASHREEQ, Sunnah.noFastReason(d(day)))
            // 28 May is a Thursday and 13 Dhul Hijjah a white day, but neither is suggested.
            assertEquals(emptySet(), Sunnah.typesFor(d(day)))
        }
        assertNull(Sunnah.noFastReason(d("2026-05-31")))
    }

    @Test fun `Tasua and Ashura`() {
        assertEquals(setOf(SunnahType.ASHURA), Sunnah.typesFor(d("2026-06-24")))
        val ashura = Sunnah.typesFor(d("2026-06-25")) // Thursday
        assertEquals(setOf(SunnahType.ASHURA, SunnahType.MON_THU), ashura)
        assertEquals(SunnahType.ASHURA, Sunnah.primary(ashura))
        assertEquals(emptySet(), Sunnah.typesFor(d("2026-06-26")))
    }

    @Test fun `Shawwal from the 2nd, never on Eid`() {
        assertEquals(NoFastReason.EID_AL_FITR, Sunnah.noFastReason(d("2026-03-20")))
        assertEquals(emptySet(), Sunnah.typesFor(d("2026-03-20")))
        assertEquals(setOf(SunnahType.SHAWWAL), Sunnah.typesFor(d("2026-03-21")))
        val monday = Sunnah.typesFor(d("2026-03-23"))
        assertEquals(setOf(SunnahType.SHAWWAL, SunnahType.MON_THU), monday)
        assertEquals(SunnahType.SHAWWAL, Sunnah.primary(monday))
        // Last day of Shawwal 1447 (29 days) is still a suggestion; 1 Dhul Qa'dah is not.
        assertEquals(setOf(SunnahType.SHAWWAL), Sunnah.typesFor(d("2026-04-17")))
        assertTrue(SunnahType.SHAWWAL !in Sunnah.typesFor(d("2026-04-18")))
    }

    @Test fun `no voluntary fasts in Ramadan`() {
        // 19 Feb 2026 is a Thursday and 3 Mar the 14th: both in Ramadan.
        assertEquals(NoFastReason.RAMADAN, Sunnah.noFastReason(d("2026-02-19")))
        assertEquals(emptySet(), Sunnah.typesFor(d("2026-02-19")))
        assertEquals(emptySet(), Sunnah.typesFor(d("2026-03-03")))
        assertTrue(Sunnah.upcoming(d("2026-02-18"), 30).isEmpty())
    }

    @Test fun `out of range dates have no types`() {
        assertEquals(emptySet(), Sunnah.typesFor(d("1800-01-02")))
        assertNull(Sunnah.noFastReason(d("1800-01-02")))
        assertTrue(Sunnah.upcoming(d("1800-01-01"), 30).isEmpty())
    }

    @Test fun `upcoming lists Sunnah days in order, filtered by type`() {
        val days = Sunnah.upcoming(d("2026-11-22"), 7)
        assertEquals(listOf("2026-11-23", "2026-11-24", "2026-11-25", "2026-11-26"), days.map { it.date.toString() })
        assertEquals(HijriMonth.JUMADA_AL_AKHIRAH, days.first().hijri.month)
        assertEquals(13, days.first().hijri.day)
        assertEquals(SunnahType.AYYAMUL_BIDH, days.first().primary)

        val monThu = Sunnah.upcoming(d("2026-11-22"), 7, enabled = setOf(SunnahType.MON_THU))
        assertEquals(listOf("2026-11-23", "2026-11-26"), monThu.map { it.date.toString() })
        assertEquals(setOf(SunnahType.MON_THU), monThu.first().types)
        assertTrue(Sunnah.upcoming(d("2026-11-22"), 0).isEmpty())
        assertTrue(Sunnah.upcoming(d("2026-11-22"), 30, enabled = emptySet()).isEmpty())
    }

    @Test fun `reminders fire at 8pm the evening before, for the next 7 days`() {
        val now = LocalDateTime.parse("2026-11-22T10:00").atZone(sg).toInstant()
        val plan = Sunnah.reminders(now, sg, 0, all)
        assertEquals(4, plan.size)
        assertTrue(plan.all { it.kind == ReminderKind.SUNNAH_FAST && it.fastId == 0L })
        assertEquals(
            listOf("2026-11-22T20:00", "2026-11-23T20:00", "2026-11-24T20:00", "2026-11-25T20:00"),
            plan.map { it.triggerAt.atZone(sg).toLocalDateTime().toString() },
        )
        assertEquals(listOf(SunnahType.AYYAMUL_BIDH, SunnahType.AYYAMUL_BIDH, SunnahType.AYYAMUL_BIDH, SunnahType.MON_THU), plan.map { Sunnah.typeOf(it) })
        assertEquals(plan.size, plan.map { it.requestCode }.toSet().size)
        assertTrue(plan.all { it.index in 0 until Reminder.MAX_PER_KIND })
    }

    @Test fun `reminders skip evenings already past and disabled types`() {
        val late = LocalDateTime.parse("2026-11-22T21:00").atZone(sg).toInstant()
        val plan = Sunnah.reminders(late, sg, 0, setOf(SunnahType.MON_THU))
        assertEquals(listOf("2026-11-25T20:00"), plan.map { it.triggerAt.atZone(sg).toLocalDateTime().toString() })
        assertEquals(SunnahType.MON_THU, Sunnah.typeOf(plan.single()))
        assertTrue(Sunnah.reminders(late, sg, 0, emptySet()).isEmpty())
        assertTrue(Sunnah.reminders(late, sg, 0, setOf(SunnahType.ARAFAH)).isEmpty())
    }

    @Test fun `reminder time is configurable and indices stay unique across a busy week`() {
        // Shawwal: every day is a suggestion, so 7 reminders in a row.
        val now = LocalDateTime.parse("2026-03-20T06:00").atZone(sg).toInstant()
        val plan = Sunnah.reminders(now, sg, 0, all, at = LocalTime.of(21, 30))
        assertEquals(7, plan.size)
        assertEquals(LocalTime.of(21, 30), plan.first().triggerAt.atZone(sg).toLocalTime())
        assertEquals(7, plan.map { it.requestCode }.toSet().size)
        assertTrue(plan.all { it.index < Reminder.MAX_PER_KIND })
        assertTrue(plan.all { Sunnah.typeOf(it) == SunnahType.SHAWWAL })
    }

    @Test fun `typeOf ignores other kinds`() {
        assertNull(Sunnah.typeOf(Reminder(ReminderKind.IFTAR, java.time.Instant.EPOCH, 0, index = 1)))
    }
}
