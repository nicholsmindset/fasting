package com.fastingshift.app.ui.partner

import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartnerPlanTimingTest {
    private val quiet = PartnerQuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0))

    @Test fun overnightAndDaytimeQuietHoursIncludeOnlyTheRequestedPeriod() {
        assertTrue(quiet.contains(LocalTime.of(22, 0)))
        assertTrue(quiet.contains(LocalTime.of(7, 59)))
        assertFalse(quiet.contains(LocalTime.of(8, 0)))
        assertFalse(quiet.contains(LocalTime.NOON))
        val daytime = PartnerQuietHours(LocalTime.NOON, LocalTime.of(14, 0))
        assertTrue(daytime.contains(LocalTime.NOON))
        assertFalse(daytime.contains(LocalTime.of(14, 0)))
    }

    @Test fun onlyTheNextChosenUnmarkedDateInTheMembersWeekCanBeScheduled() {
        val now = ZonedDateTime.parse("2026-11-26T19:00:00+14:00[Pacific/Kiritimati]")
        val weekStart = now.toLocalDate().minusDays(1)
        val end = weekStart.plusDays(6)
        val dates = setOf("2026-11-26", "2026-11-28", "2026-11-29")
        fun target(hour: Int, marked: Set<String>) = partnerReminderTarget(now, LocalTime.of(hour, 0),
            weekStart, end, dates, marked, quiet)
        assertEquals("2026-11-29", target(18, setOf("2026-11-28"))!!.toLocalDate().toString())
        assertNull(target(23, emptySet()))
        assertNull(target(18, dates))
        assertNull(partnerReminderTarget(now, LocalTime.NOON, weekStart, now.toLocalDate(), emptySet(), emptySet(), null))
        assertEquals(now.toLocalDate(), partnerReminderTarget(now, LocalTime.of(20, 0), weekStart, end,
            emptySet(), emptySet(), null)!!.toLocalDate())
    }

    @Test fun delayedWorkCannotNotifyInQuietHoursOnAnotherDateOrAfterItsGracePeriod() {
        val due = ZonedDateTime.parse("2026-11-26T21:55:00+08:00[Asia/Singapore]")
        assertTrue(canDeliverPartnerReminder(due, due.plusMinutes(4), quiet))
        assertFalse(canDeliverPartnerReminder(due, due.plusMinutes(5), quiet))
        assertFalse(canDeliverPartnerReminder(due, due.minusSeconds(1), null))
        assertFalse(canDeliverPartnerReminder(due, due.plusMinutes(61), null))
        val midnight = due.withHour(23).withMinute(55)
        assertFalse(canDeliverPartnerReminder(midnight, midnight.plusMinutes(10), null))
    }

    @Test fun springClockChangeCannotShiftDeliveryIntoQuietHoursOrPastTheEndOfTheWeek() {
        val now = ZonedDateTime.parse("2026-03-08T00:15:00-05:00[America/New_York]")
        val day = now.toLocalDate()
        val gapQuiet = PartnerQuietHours(LocalTime.of(3, 0), LocalTime.of(4, 0))
        assertNull(partnerReminderTarget(now, LocalTime.of(2, 30), day.minusDays(6), day,
            setOf(day.toString()), emptySet(), gapQuiet))
        assertEquals(LocalTime.of(3, 30), partnerReminderTarget(now, LocalTime.of(2, 30), day.minusDays(6),
            day, emptySet(), emptySet(), null)!!.toLocalTime())
    }
}
