package com.fastingshift.domain

import com.fastingshift.domain.ramadan.SunnahReminderPlanner
import com.fastingshift.domain.ramadan.SunnahType
import com.fastingshift.domain.reminders.ReminderKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunnahReminderPlannerTest {
    private val zone = ZoneId.of("America/New_York")
    private val selections = SunnahType.entries.toSet()
    private fun date(month: Int, day: Int) = LocalDate.from(HijrahDate.of(1447, month, day))
    private fun follows(month: Int, day: Int, type: SunnahType) =
        SunnahReminderPlanner.follows(date(month, day), setOf(type), 0)

    @Test fun schedulesBeforeEachSelectedObservanceInLocalTime() {
        val day = date(12, 9)
        val dayBefore = day.minusDays(1)
        val noon = dayBefore.atTime(12, 0).atZone(zone).toInstant()
        val reminder = assertNotNull(SunnahReminderPlanner.next(setOf(SunnahType.ARAFAH), 0, zone, noon))
        assertEquals(ReminderKind.SUNNAH_FAST, reminder.kind)
        assertEquals(dayBefore.atTime(20, 0).atZone(zone).toInstant(), reminder.triggerAt)
        assertTrue(follows(1, 9, SunnahType.ASHURA))
        assertTrue(follows(1, 10, SunnahType.ASHURA))
        assertTrue(follows(2, 14, SunnahType.AYYAMUL_BIDH))
        assertTrue(follows(10, 2, SunnahType.SHAWWAL))
        assertFalse(follows(10, 3, SunnahType.SHAWWAL))
        assertNull(SunnahReminderPlanner.next(emptySet(), 0, zone, noon))
    }

    @Test fun overlappingObservancesNeverOverrideExclusions() {
        val excluded = listOf(date(10, 1)) + (10..13).map { date(12, it) } + (1..29).map { date(9, it) }
        for (day in excluded) assertFalse(SunnahReminderPlanner.follows(day, selections, 0))
        assertTrue(follows(12, 14, SunnahType.AYYAMUL_BIDH))
    }

    @Test fun appliesMoonSightingOffsetToLunarDatesOnly() {
        val dayBeforeArafah = date(12, 8)
        assertFalse(SunnahReminderPlanner.follows(dayBeforeArafah, setOf(SunnahType.ARAFAH), 0))
        assertTrue(SunnahReminderPlanner.follows(dayBeforeArafah, setOf(SunnahType.ARAFAH), 1))
        assertFalse(SunnahReminderPlanner.follows(date(12, 9), selections, 1))
    }

    @Test fun breakSkipsTheTargetDayAndNotificationsDuringTheBreak() {
        val monday = date(2, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val sunday = monday.minusDays(1)
        val noon = sunday.atTime(12, 0).atZone(zone).toInstant()
        val selected = setOf(SunnahType.MON_THU)
        val normal = assertNotNull(SunnahReminderPlanner.next(selected, 0, zone, noon))
        assertEquals(sunday, normal.triggerAt.atZone(zone).toLocalDate())
        val next = assertNotNull(SunnahReminderPlanner.next(selected, 0, zone, noon) { it in monday..monday.plusDays(6) })
        assertEquals(monday.plusDays(9), next.triggerAt.atZone(zone).toLocalDate())
        assertTrue(next.triggerAt.isAfter(normal.triggerAt))
    }
}
