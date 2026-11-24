package com.fastingshift.domain

import com.fastingshift.domain.insights.BodySignals
import com.fastingshift.domain.insights.RestingHeartRateSample
import com.fastingshift.domain.insights.SleepSession
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodySignalsTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val mon = LocalDate.of(2026, 10, 5) // a Monday
    private fun at(d: LocalDate, h: Int, m: Int = 0): Instant = d.atTime(h, m).atZone(zone).toInstant()
    private fun h(n: Long) = Duration.ofHours(n)

    /** A fast of [hours] that ended at noon on [day]. */
    private fun fastEnding(day: LocalDate, hours: Long = 16): Fast {
        val end = at(day, 12)
        return Fast(startAt = end.minus(h(hours)), targetEndAt = end, protocolId = "16:8").end(end)
    }

    /** Sleep from 23:00 on [day] for [hours]. */
    private fun night(day: LocalDate, hours: Long, minutes: Long = 0) = at(day, 23).let { SleepSession(it, it.plus(h(hours)).plus(Duration.ofMinutes(minutes))) }

    private fun glucose(at: Instant, mmol: Double) = MetricLog(at = at, type = MetricType.GLUCOSE, value = mmol, unit = MetricUnit.MMOL_L)
    private fun ketone(at: Instant, mmol: Double) = MetricLog(at = at, type = MetricType.KETONE, value = mmol, unit = MetricUnit.MMOL_L)

    // ---------- Fasting days and nights ----------

    @Test fun `fasting days are days a fast of 12h or more ended`() {
        val short = fastEnding(mon, hours = 10)
        val long = fastEnding(mon.plusDays(1), hours = 13)
        val active = Fast(startAt = at(mon.plusDays(2), 8), targetEndAt = at(mon.plusDays(3), 8), protocolId = "p")
        assertEquals(setOf(mon.plusDays(1)), BodySignals.fastingDays(listOf(short, long, active), zone))
    }

    @Test fun `fasting day uses the local date of the end`() {
        // Ends 01:00 Singapore time on Tuesday, which is still Monday in UTC.
        val end = at(mon.plusDays(1), 1)
        val f = Fast(startAt = end.minus(h(16)), targetEndAt = end, protocolId = "p").end(end)
        assertEquals(setOf(mon.plusDays(1)), BodySignals.fastingDays(listOf(f), zone))
    }

    @Test fun `sleep before and after midnight belongs to the same night`() {
        assertEquals(mon, BodySignals.nightOf(SleepSession(at(mon, 23), at(mon.plusDays(1), 7)), zone))
        assertEquals(mon, BodySignals.nightOf(SleepSession(at(mon.plusDays(1), 1), at(mon.plusDays(1), 8)), zone))
        assertEquals(mon.plusDays(1), BodySignals.nightOf(SleepSession(at(mon.plusDays(1), 21), at(mon.plusDays(2), 5)), zone))
    }

    @Test fun `local noon divides sleep nights on both daylight saving transitions`() {
        val newYork = ZoneId.of("America/New_York")
        fun night(start: String): LocalDate {
            val instant = Instant.parse(start)
            return BodySignals.nightOf(SleepSession(instant, instant.plus(h(4))), newYork)
        }
        assertEquals(LocalDate.of(2026, 3, 8), night("2026-03-08T16:30:00Z")) // 12:30 EDT
        assertEquals(LocalDate.of(2026, 10, 31), night("2026-11-01T16:30:00Z")) // 11:30 EST
    }

    @Test fun `overlapping sessions are merged, split sessions added and naps ignored`() {
        val sessions = listOf(
            SleepSession(at(mon, 23), at(mon.plusDays(1), 6)), // watch: 7h
            SleepSession(at(mon, 23, 30), at(mon.plusDays(1), 6, 30)), // phone overlaps: +30 min
            SleepSession(at(mon.plusDays(1), 2), at(mon.plusDays(1), 3)), // inside, adds nothing
            SleepSession(at(mon.plusDays(2), 1), at(mon.plusDays(2), 3)), // 2h only: a nap-length night, dropped
            SleepSession(at(mon.plusDays(2), 4), at(mon.plusDays(2), 6)), // same night (Tue), together 4h
            SleepSession(at(mon.plusDays(3), 14), at(mon.plusDays(3), 15)), // afternoon nap on Thu → Thu night, 1h, dropped
        )
        val perNight = BodySignals.sleepPerNight(sessions, zone)
        assertEquals(mapOf(mon to Duration.ofMinutes(450), mon.plusDays(1) to h(4)), perNight)
    }

    @Test fun `sleep session must end after it starts`() {
        assertFailsWith<IllegalArgumentException> { SleepSession(at(mon, 23), at(mon, 23)) }
    }

    // ---------- Sleep comparison ----------

    @Test fun `sleep after fasting days vs other nights`() {
        // Fasts end on Mon, Wed, Fri for three weeks → 9 fasting days; the other 12 days are not.
        val days = (0L until 21L).map { mon.plusDays(it) }
        val fastDays = days.filter { it.dayOfWeek.value in setOf(1, 3, 5) }
        val fasts = fastDays.map { fastEnding(it) }
        val sleep = days.map { d -> if (d in fastDays) night(d, 7, 30) else night(d, 7) }
        val c = assertNotNull(BodySignals.sleepAfterFasting(fasts, sleep, zone))
        assertEquals(9, c.fastingNights)
        assertEquals(12, c.otherNights)
        assertEquals(Duration.ofMinutes(450), c.afterFastingDay)
        assertEquals(h(7), c.otherwise)
        assertEquals(Duration.ofMinutes(30), c.difference)
    }

    @Test fun `sleep comparison needs five nights on each side`() {
        val days = (0L until 9L).map { mon.plusDays(it) }
        val fasts = days.take(4).map { fastEnding(it) } // only 4 fasting nights
        val sleep = days.map { night(it, 7) }
        assertNull(BodySignals.sleepAfterFasting(fasts, sleep, zone))
        // One more fasting night with sleep tips it over: 5 vs 5.
        val fasts5 = days.take(5).map { fastEnding(it) }
        val sleep10 = (0L until 10L).map { night(mon.plusDays(it), 7) }
        assertNotNull(BodySignals.sleepAfterFasting(fasts5, sleep10, zone))
        // Every night after a fasting day: nothing to compare with.
        assertNull(BodySignals.sleepAfterFasting(days.map { fastEnding(it) }, sleep, zone))
    }

    @Test fun `no sleep data gives nothing`() {
        assertNull(BodySignals.sleepAfterFasting(listOf(fastEnding(mon)), emptyList(), zone))
    }

    // ---------- Resting heart rate ----------

    @Test fun `resting heart rate in fasting weeks vs other weeks`() {
        // Weeks 0, 2, 4 have two fasts each (fasting weeks); weeks 1, 3, 5 have one (not).
        val fasts = (0L until 6L).flatMap { w ->
            val start = mon.plusWeeks(w)
            if (w % 2 == 0L) listOf(fastEnding(start), fastEnding(start.plusDays(3))) else listOf(fastEnding(start))
        }
        val samples = (0L until 42L).map { d ->
            val day = mon.plusDays(d)
            val week = d / 7
            RestingHeartRateSample(at(day, 7), if (week % 2 == 0L) 58.0 else 62.0)
        } + RestingHeartRateSample(at(mon, 8), 400.0) // sensor glitch, ignored
        val c = assertNotNull(BodySignals.restingHeartRateByWeek(fasts, samples, zone))
        assertEquals(3, c.fastingWeeks)
        assertEquals(3, c.otherWeeks)
        assertEquals(58.0, c.fastingAverageBpm, 1e-9)
        assertEquals(62.0, c.otherAverageBpm, 1e-9)
        assertEquals(-4.0, c.difference, 1e-9)
        assertEquals((0L until 6L).map { mon.plusWeeks(it) }, c.weekly.map { it.weekStart })
        assertEquals(listOf(true, false, true, false, true, false), c.weekly.map { it.fastingWeek })
    }

    @Test fun `weekly average weighs every sample in the week`() {
        val fasts = (0L until 3L).flatMap { w -> listOf(fastEnding(mon.plusWeeks(w)), fastEnding(mon.plusWeeks(w).plusDays(1))) }
        val samples = (0L until 6L).flatMap { w ->
            listOf(RestingHeartRateSample(at(mon.plusWeeks(w), 7), 60.0), RestingHeartRateSample(at(mon.plusWeeks(w).plusDays(2), 7), 64.0))
        }
        val c = assertNotNull(BodySignals.restingHeartRateByWeek(fasts, samples, zone))
        assertTrue(c.weekly.all { it.averageBpm == 62.0 })
        assertEquals(0.0, c.difference, 1e-9)
    }

    @Test fun `heart rate comparison needs three weeks on each side`() {
        val fasts = (0L until 2L).flatMap { w -> listOf(fastEnding(mon.plusWeeks(w)), fastEnding(mon.plusWeeks(w).plusDays(1))) }
        val samples = (0L until 6L).map { w -> RestingHeartRateSample(at(mon.plusWeeks(w), 7), 60.0) }
        assertNull(BodySignals.restingHeartRateByWeek(fasts, samples, zone)) // 2 fasting weeks vs 4
        assertNull(BodySignals.restingHeartRateByWeek(fasts, emptyList(), zone))
    }

    // ---------- Glucose ----------

    @Test fun `glucose during fasts vs outside, mixing units`() {
        val f1 = fastEnding(mon) // Sun 20:00 → Mon 12:00
        val active = Fast(startAt = at(mon.plusDays(2), 20), targetEndAt = at(mon.plusDays(3), 12), protocolId = "p")
        val now = at(mon.plusDays(3), 9)
        val during = listOf(at(mon, 6), at(mon, 8), at(mon, 12), at(mon.plusDays(3), 7), at(mon.plusDays(3), 8)).map { glucose(it, 4.5) }
        val outside = listOf(at(mon, 13), at(mon, 18), at(mon.plusDays(1), 9), at(mon.plusDays(1), 18), at(mon.plusDays(3), 10)).map {
            MetricLog(at = it, type = MetricType.GLUCOSE, value = 99.088, unit = MetricUnit.MG_DL) // 5.5 mmol/L
        }
        val ketones = listOf(ketone(at(mon, 7), 1.2))
        val c = assertNotNull(BodySignals.glucoseDuringFasts(listOf(f1, active), during + outside + ketones, now))
        assertEquals(5, c.duringFastReadings)
        assertEquals(5, c.outsideReadings)
        assertEquals(4.5, c.duringFastMmolPerL, 1e-9)
        assertEquals(5.5, c.outsideMmolPerL, 1e-6)
        assertEquals(-1.0, c.differenceMmolPerL, 1e-6)
    }

    @Test fun `glucose comparison needs five readings on each side`() {
        val f = fastEnding(mon)
        val readings = (6..9).map { glucose(at(mon, it), 4.5) } + (13..20).map { glucose(at(mon, it), 5.5) }
        assertNull(BodySignals.glucoseDuringFasts(listOf(f), readings, at(mon, 23)))
        assertNull(BodySignals.glucoseDuringFasts(emptyList(), readings, at(mon, 23)))
    }

    @Test fun `report is empty without enough data`() {
        val r = BodySignals.report(emptyList(), emptyList(), emptyList(), emptyList(), zone, at(mon, 12))
        assertTrue(r.isEmpty)
        val days = (0L until 21L).map { mon.plusDays(it) }
        val fastDays = days.filter { it.dayOfWeek.value in setOf(1, 3, 5) }
        val full = BodySignals.report(fastDays.map { fastEnding(it) }, days.map { night(it, 7) }, emptyList(), emptyList(), zone, at(mon, 12))
        assertFalse(full.isEmpty)
        assertNotNull(full.sleep)
        assertNull(full.restingHeartRate)
        assertNull(full.glucose)
    }

    // ---------- Doctor report summary ----------

    @Test fun `summary per type for a period`() {
        val from = at(mon, 0)
        val to = at(mon.plusDays(7), 0)
        val readings = listOf(
            glucose(at(mon, 8), 5.0),
            MetricLog(at = at(mon.plusDays(1), 8), type = MetricType.GLUCOSE, value = 108.096, unit = MetricUnit.MG_DL), // 6.0
            glucose(at(mon.plusDays(2), 8), 4.0),
            glucose(at(mon.minusDays(1), 8), 9.0), // before the period
            glucose(to, 9.0), // the end is exclusive
            ketone(at(mon, 9), 0.4),
            ketone(at(mon.plusDays(3), 9), 1.6),
        )
        val s = BodySignals.summarise(readings, from, to)
        assertEquals(listOf(MetricType.GLUCOSE, MetricType.KETONE), s.map { it.type })
        val g = s[0]
        assertEquals(MetricUnit.MMOL_L, g.unit)
        assertEquals(3, g.count)
        assertEquals(5.0, g.average, 1e-6)
        assertEquals(4.0, g.min, 1e-6)
        assertEquals(6.0, g.max, 1e-6)
        val k = s[1]
        assertEquals(MetricUnit.MMOL_L, k.unit)
        assertEquals(2, k.count)
        assertEquals(1.0, k.average, 1e-9)
        assertEquals(0.4, k.min, 1e-9)
        assertEquals(1.6, k.max, 1e-9)
    }

    @Test fun `summary in mg per dL keeps ketones in mmol per L and skips empty types`() {
        val readings = listOf(glucose(at(mon, 8), 5.0), ketone(at(mon, 9), 0.5))
        val s = BodySignals.summarise(readings, at(mon, 0), at(mon.plusDays(1), 0), MetricUnit.MG_DL)
        assertEquals(MetricUnit.MG_DL, s[0].unit)
        assertEquals(90.08, s[0].average, 1e-6)
        assertEquals(MetricUnit.MMOL_L, s[1].unit)
        assertEquals(0.5, s[1].average, 1e-9)
        assertEquals(listOf(MetricType.KETONE), BodySignals.summarise(listOf(ketone(at(mon, 9), 0.5)), at(mon, 0), at(mon, 12)).map { it.type })
        assertTrue(BodySignals.summarise(readings, at(mon.plusDays(5), 0), at(mon.plusDays(6), 0)).isEmpty())
    }
}
