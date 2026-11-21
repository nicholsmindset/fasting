package com.fastingshift.domain

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.streak.StreakCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakCalculatorTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val today = LocalDate.of(2026, 11, 20) // a Friday

    /** A fast starting 06:00 local on [day] (the day it counts for); completed unless [early]. */
    private fun fastEnding(day: LocalDate, early: Boolean = false, hoursLong: Long = 16): Fast {
        val start = day.atTime(LocalTime.of(6, 0)).atZone(zone).toInstant()
        val end = start.plus(hours(hoursLong))
        return Fast(startAt = start, targetEndAt = end, protocolId = "p").end(if (early) end.minus(hours(1)) else end)
    }

    private fun days(vararg offsets: Long) = offsets.map { fastEnding(today.minusDays(it)) }

    @Test fun `empty history`() {
        assertEquals(0, StreakCalculator.calculate(emptyList(), zone, today).current)
    }

    @Test fun `consecutive days including today`() {
        assertEquals(3, StreakCalculator.calculate(days(0, 1, 2), zone, today).current)
    }

    @Test fun `today not done yet does not break streak`() {
        assertEquals(2, StreakCalculator.calculate(days(1, 2), zone, today).current)
    }

    @Test fun `gap breaks streak but longest is kept`() {
        val r = StreakCalculator.calculate(days(0, 2, 3, 4, 5), zone, today)
        assertEquals(1, r.current)
        assertEquals(4, r.longest)
    }

    @Test fun `ended early does not count but is not shamed`() {
        val fasts = days(1) + fastEnding(today, early = true)
        assertEquals(1, StreakCalculator.calculate(fasts, zone, today).current)
    }

    @Test fun `length never matters`() {
        val long = listOf(fastEnding(today, hoursLong = 72), fastEnding(today.minusDays(1), hoursLong = 12))
        assertEquals(2, StreakCalculator.calculate(long, zone, today).current)
    }

    @Test fun `frozen day bridges without adding`() {
        val r = StreakCalculator.calculate(days(0, 2, 3), zone, today, frozenDays = setOf(today.minusDays(1)))
        assertEquals(3, r.current)
    }

    @Test fun `unplanned days bridge for weekly protocols`() {
        // 5:2 on Monday and Thursday. Today is Friday.
        val planned = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val mon = today.with(DayOfWeek.MONDAY)
        val thu = today.with(DayOfWeek.THURSDAY)
        val r = StreakCalculator.calculate(listOf(fastEnding(mon), fastEnding(thu)), zone, today) { it.dayOfWeek in planned }
        assertEquals(2, r.current)
    }

    @Test fun `a fast counts for the day it started`() {
        // Starts 20:00 SGT on 19 Nov, ends 12:00 on 20 Nov: it was the 19th's planned fast.
        val start = today.minusDays(1).atTime(20, 0).atZone(zone).toInstant()
        val f = Fast(startAt = start, targetEndAt = start.plus(hours(16)), protocolId = "p").end(start.plus(hours(16)))
        assertEquals(setOf(today.minusDays(1)), StreakCalculator.completedDays(listOf(f), zone))
    }

    @Test fun `5-2 fast ending the next day keeps the streak`() {
        val planned = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        fun overnight(day: LocalDate): Fast {
            val s0 = day.atTime(20, 0).atZone(zone).toInstant()
            return Fast(startAt = s0, targetEndAt = s0.plus(hours(24)), protocolId = "p").end(s0.plus(hours(24)))
        }
        val mon = today.with(DayOfWeek.MONDAY)
        val thu = today.with(DayOfWeek.THURSDAY)
        val r = StreakCalculator.calculate(listOf(overnight(mon), overnight(thu)), zone, today) { it.dayOfWeek in planned }
        assertEquals(2, r.current)
    }

    @Test fun `a 48h fast counts once and does not break the streak`() {
        val fasts = listOf(fastEnding(today.minusDays(3)), fastEnding(today.minusDays(2), hoursLong = 48))
        // Yesterday was covered by the 48h fast; today is not done yet.
        val r = StreakCalculator.calculate(fasts, zone, today, isPlannedDay = { it != today.minusDays(1) })
        assertEquals(2, r.current)
    }

    @Test fun `a fast still running does not break the streak`() {
        val running = today.minusDays(1).atTime(20, 0).atZone(zone).toInstant().let {
            Fast(startAt = it, targetEndAt = it.plus(hours(16)), protocolId = "p")
        }
        val r = StreakCalculator.calculate(days(2, 3) + running, zone, today)
        assertEquals(2, r.current)
    }

    @Test fun `editing a fast updates the streak`() {
        val f = fastEnding(today.minusDays(1), early = true)
        assertEquals(0, StreakCalculator.calculate(listOf(f), zone, today).current)
        val edited = f.editTimes(startAt = f.startAt.minus(hours(2)))
        assertEquals(1, StreakCalculator.calculate(listOf(edited), zone, today).current)
    }

    @Test fun `freezes only cover a recent missed day, one in any 7 days`() {
        assertTrue(StreakCalculator.canFreeze(today.minusDays(1), emptySet(), today))
        assertTrue(StreakCalculator.canFreeze(today.minusDays(2), emptySet(), today))
        assertFalse(StreakCalculator.canFreeze(today, emptySet(), today))
        assertFalse(StreakCalculator.canFreeze(today.minusDays(20), emptySet(), today), "old gaps can't be backfilled")
        assertFalse(StreakCalculator.canFreeze(today.minusDays(1), setOf(today.minusDays(5)), today))
        assertTrue(StreakCalculator.canFreeze(today.minusDays(1), setOf(today.minusDays(8)), today))
        assertFalse(StreakCalculator.canFreeze(today.minusDays(1), setOf(today.minusDays(1)), today))
    }
}
