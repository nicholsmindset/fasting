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

    /** A 16h fast ending at 12:00 local on [day]; completed unless [early]. */
    private fun fastEnding(day: LocalDate, early: Boolean = false, hoursLong: Long = 16): Fast {
        val end = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val start = end.minus(hours(hoursLong))
        return Fast(startAt = start, targetEndAt = start.plus(hours(hoursLong)), protocolId = "p").end(if (early) end.minus(hours(1)) else end)
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

    @Test fun `day is the local date the fast ended`() {
        // Ends 01:00 SGT on 20 Nov = 17:00Z on 19 Nov. In Singapore it belongs to 20 Nov.
        val end = today.atTime(1, 0).atZone(zone).toInstant()
        val f = Fast(startAt = end.minus(hours(16)), targetEndAt = end, protocolId = "p").end(end)
        assertEquals(setOf(today), StreakCalculator.completedDays(listOf(f), zone))
    }

    @Test fun `editing a fast updates the streak`() {
        val f = fastEnding(today.minusDays(1), early = true)
        assertEquals(0, StreakCalculator.calculate(listOf(f), zone, today).current)
        val edited = f.editTimes(startAt = f.startAt.minus(hours(2)))
        assertEquals(1, StreakCalculator.calculate(listOf(edited), zone, today).current)
    }

    @Test fun `one freeze per ISO week`() {
        val mon = LocalDate.of(2026, 11, 16)
        assertTrue(StreakCalculator.canFreeze(mon, emptySet()))
        assertFalse(StreakCalculator.canFreeze(mon.plusDays(2), setOf(mon)))
        assertTrue(StreakCalculator.canFreeze(mon.plusDays(7), setOf(mon)))
        assertFalse(StreakCalculator.canFreeze(mon, setOf(mon)))
    }
}
