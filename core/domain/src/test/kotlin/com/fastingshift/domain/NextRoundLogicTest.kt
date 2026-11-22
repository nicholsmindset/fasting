package com.fastingshift.domain

import com.fastingshift.domain.coaching.CoachingPlanner
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.history.WeeklyRecaps
import com.fastingshift.domain.hydration.WaterGoal
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPhase
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.reminders.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextRoundLogicTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")
    private fun fast(h: Long, start: Instant = t0) = Fast(id = 7, startAt = start, targetEndAt = start.plus(hours(h)), protocolId = "p")

    // Prayer times: within 3 minutes of published sunrise/sunset references (+2 min ihtiyati where applied).
    private fun near(expected: String, actual: Instant?, zone: ZoneId, minutes: Long = 3) {
        val a = requireNotNull(actual).atZone(zone).toLocalTime()
        val diff = abs(Duration.between(LocalTime.parse(expected), a).toMinutes())
        assertTrue(diff <= minutes, "expected ~$expected, got $a")
    }

    @Test fun `prayer times match reference values`() {
        val london = Cities.byId("gb_london")!!
        val l = RamadanPlanner.day(london, PrayerMethod.MWL, LocalDate.of(2026, 3, 1))
        near("17:40", l.maghrib, london.zone) // sunset, no ihtiyati for MWL
        near("06:46", l.sunrise, london.zone)
        val s = Cities.byId("sg_singapore")!!
        val june = RamadanPlanner.day(s, PrayerMethod.MUIS, LocalDate.of(2026, 6, 21))
        near("19:14", june.maghrib, s.zone) // sunset 19:12 + 2 min
        val march = RamadanPlanner.day(s, PrayerMethod.MUIS, LocalDate.of(2026, 3, 1))
        near("05:59", march.fajr, s.zone)
        near("19:21", march.maghrib, s.zone)
        val events = listOf(march.fajr, march.sunrise, march.dhuhr, march.maghrib, march.isha).requireNoNulls()
        assertTrue(events.zipWithNext().all { (earlier, later) -> earlier < later })
    }

    @Test fun `ramadan phase and reminders`() {
        val s = Cities.byId("sg_singapore")!!
        val day = RamadanPlanner.day(s, PrayerMethod.MUIS, LocalDate.of(2026, 3, 1))
        val noon = LocalDate.of(2026, 3, 1).atTime(12, 0).atZone(s.zone).toInstant()
        val fasting = assertIs<RamadanPhase.Fasting>(RamadanPlanner.phase(s, PrayerMethod.MUIS, noon))
        assertEquals(day.maghrib, fasting.iftar)
        val night = LocalDate.of(2026, 3, 1).atTime(22, 0).atZone(s.zone).toInstant()
        val eating = assertIs<RamadanPhase.Eating>(RamadanPlanner.phase(s, PrayerMethod.MUIS, night))
        assertTrue(eating.suhoorEnds.atZone(s.zone).toLocalDate() == LocalDate.of(2026, 3, 2))
        val rem = RamadanPlanner.reminders(s, PrayerMethod.MUIS, noon)
        assertEquals(setOf(ReminderKind.SUHOOR, ReminderKind.IFTAR), rem.map { it.kind }.toSet())
        assertEquals(day.maghrib, rem.first { it.kind == ReminderKind.IFTAR }.triggerAt)
        assertEquals(PrayerMethod.JAKIM, Cities.defaultMethod(Cities.byId("my_kuala_lumpur")!!))
    }

    @Test fun `coaching tips for the first fasts only`() {
        val f = fast(16)
        assertEquals(CoachingTip.HUNGER_WAVES, CoachingPlanner.currentTip(f, t0.plus(hours(4))))
        assertEquals(CoachingTip.BREAK_GENTLY, CoachingPlanner.currentTip(f, t0.plus(hours(15) + Duration.ofMinutes(20))))
        assertNull(CoachingPlanner.currentTip(f, t0.plus(hours(1))))
        assertEquals(4, CoachingPlanner.reminders(f, t0).size)
        // On a 12:12 the electrolytes tip (10h) still fits; on a 6h fast only the early ones do.
        assertEquals(4, CoachingPlanner.schedule(fast(12)).size)
        assertEquals(listOf(CoachingTip.HUNGER_WAVES, CoachingTip.BREAK_GENTLY), CoachingPlanner.schedule(fast(6)).map { it.first })
        assertTrue(CoachingPlanner.isCoaching(0, t0, t0.plus(Duration.ofDays(3))))
        assertFalse(CoachingPlanner.isCoaching(3, t0, t0))
        assertFalse(CoachingPlanner.isCoaching(1, t0, t0.plus(Duration.ofDays(20))))
    }

    @Test fun `water goal`() {
        assertEquals(2_000, WaterGoal.defaultMl(null))
        assertEquals(2_800, WaterGoal.defaultMl(80.0))
        assertEquals(1_500, WaterGoal.defaultMl(30.0))
        assertEquals(4_000, WaterGoal.defaultMl(200.0))
        assertEquals(0.5f, WaterGoal.progress(1000.0, 2000))
        assertEquals(1f, WaterGoal.progress(3000.0, 2000))
    }

    @Test fun `near goal, hydration and window closing reminders`() {
        val s = ReminderSettings(nearGoalEnabled = true, forgotToEndAfter = null, fastEndEnabled = false)
        val plan = ReminderPlanner.planForActiveFast(fast(36), s, t0)
        assertEquals(t0.plus(hours(35)), plan.single { it.kind == ReminderKind.NEAR_GOAL }.triggerAt)
        val hydration = plan.filter { it.kind == ReminderKind.HYDRATION }.map { Duration.between(t0, it.triggerAt).toHours() }
        assertEquals(listOf(3L, 6L, 9L, 12L, 15L, 18L, 21L, 24L, 27L, 30L, 33L), hydration)
        assertTrue(ReminderPlanner.planForActiveFast(fast(16), s, t0).none { it.kind == ReminderKind.HYDRATION })
        val ended = fast(16).end(t0.plus(hours(16)))
        val closing = ReminderPlanner.planWindowClosing(ended, 8, ReminderSettings(windowClosingEnabled = true), t0.plus(hours(17)))!!
        assertEquals(t0.plus(hours(24)).minus(Duration.ofMinutes(30)), closing.triggerAt)
        assertNull(ReminderPlanner.planWindowClosing(ended, 8, ReminderSettings(), t0.plus(hours(17))))
        // New kinds were appended: existing request codes are unchanged.
        assertEquals(0, ReminderKind.FAST_START.ordinal)
        assertEquals(4, ReminderKind.SYMPTOM_CHECK_IN.ordinal)
    }

    @Test fun `weekly recap`() {
        val monday = LocalDate.of(2026, 11, 16)
        val start = monday.atTime(8, 0).atZone(sg).toInstant()
        val fasts = listOf(
            Fast(startAt = start, targetEndAt = start.plus(hours(16)), protocolId = "p").end(start.plus(hours(16))),
            Fast(startAt = start.plus(hours(24)), targetEndAt = start.plus(hours(40)), protocolId = "p").end(start.plus(hours(34))),
        )
        val water = listOf(WaterLog(at = start, value = 2000.0, unit = VolumeUnit.ML), WaterLog(at = start.plus(hours(24)), value = 500.0, unit = VolumeUnit.ML))
        val r = WeeklyRecaps.compute(fasts, water, sg, monday, monday.plusDays(6).atTime(20, 0).atZone(sg).toInstant(), 2000)
        assertEquals(hours(26), r.fastingTime)
        assertEquals(2, r.fastsStarted)
        assertEquals(1, r.completedToPlan)
        assertEquals(1, r.completedDays)
        assertEquals(7, r.plannedDays)
        assertEquals(1, r.waterGoalDays)
        assertEquals(2, r.waterLoggedDays)
        val fri = LocalDate.of(2026, 11, 20).atTime(9, 0).atZone(sg).toInstant()
        assertEquals(LocalDate.of(2026, 11, 22).atTime(18, 0).atZone(sg).toInstant(), WeeklyRecaps.nextRecapAt(fri, sg))
        val sunEvening = LocalDate.of(2026, 11, 22).atTime(19, 0).atZone(sg).toInstant()
        assertEquals(LocalDate.of(2026, 11, 29).atTime(18, 0).atZone(sg).toInstant(), WeeklyRecaps.nextRecapAt(sunEvening, sg))
    }

    @Test fun `mood is optional and bounded`() {
        assertEquals(4, fast(16).copy(mood = 4).mood)
        assertFailsWith<IllegalArgumentException> { fast(16).copy(mood = 9) }
    }
}
