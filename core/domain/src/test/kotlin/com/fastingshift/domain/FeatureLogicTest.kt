package com.fastingshift.domain

import com.fastingshift.domain.backup.Backup
import com.fastingshift.domain.backup.BackupCodec
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.backup.CsvExport
import com.fastingshift.domain.backup.FastRecord
import com.fastingshift.domain.backup.SymptomCheckinRecord
import com.fastingshift.domain.billing.BillingReminderKind
import com.fastingshift.domain.billing.BillingReminders
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.Feature
import com.fastingshift.domain.billing.PlanType
import com.fastingshift.domain.history.History
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.program.ProgramProgress
import com.fastingshift.domain.program.Program
import com.fastingshift.domain.program.ProgramReading
import com.fastingshift.domain.program.ProgramDay
import com.fastingshift.domain.safety.Helplines
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.timeline.BodyTimeline
import com.fastingshift.domain.timeline.EvidenceLabel
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureLogicTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val fri = LocalDate.of(2026, 11, 20)
    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).atZone(zone).toInstant()
    private fun fast(startDay: LocalDate, h: Long, endAfter: Long? = h) =
        Fast(startAt = at(startDay, 20), targetEndAt = at(startDay, 20).plus(hours(h)), protocolId = "p")
            .let { f -> endAfter?.let { f.end(f.startAt.plus(hours(it))) } ?: f }

    // Schedules
    @Test fun `next scheduled start`() {
        val s = Schedule(protocolId = "weekly_5_2", days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), startTime = LocalTime.of(20, 0))
        val (_, next) = ScheduleCalculator.nextStart(listOf(s), zone, at(fri, 9))!!
        assertEquals(at(LocalDate.of(2026, 11, 23), 20), next)
        // Thursday 19:00 → same day 20:00.
        assertEquals(at(LocalDate.of(2026, 11, 19), 20), ScheduleCalculator.nextStart(listOf(s), zone, at(LocalDate.of(2026, 11, 19), 19))!!.second)
        assertNull(ScheduleCalculator.nextStart(listOf(s.copy(enabled = false)), zone, at(fri, 9)))
        assertTrue(ScheduleCalculator.isPlannedDay(emptyList(), fri))
        assertFalse(ScheduleCalculator.isPlannedDay(listOf(s), fri))
    }

    // History
    @Test fun `weekly totals and calendar`() {
        val fasts = listOf(fast(fri, 16), fast(fri.minusDays(1), 16, 10), fast(fri.minusDays(7), 24))
        val weeks = History.weeklyTotals(fasts, zone, at(fri, 23))
        assertEquals(LocalDate.of(2026, 11, 16), weeks[0].weekStart)
        assertEquals(Duration.ofHours(26), weeks[0].fastingTime)
        assertEquals(1, weeks[0].completed)
        assertEquals(2, weeks[0].fasts)
        assertEquals(setOf(fri, fri.minusDays(1)), History.byDay(fasts, zone, YearMonth.of(2026, 11)).keys - setOf(fri.minusDays(7)))
    }

    // Insights
    @Test fun `insights`() {
        val fasts = listOf(fast(fri.minusDays(1), 16), fast(fri.minusDays(2), 16), fast(fri.minusDays(3), 16, 8))
        val weeks = Insights.weeklyConsistency(fasts, zone, fri, weeks = 2)
        assertEquals(2, weeks.size)
        assertEquals(2, weeks[1].completedDays)
        assertEquals(5, weeks[1].plannedDays) // Mon–Fri so far
        val byDay = Insights.averageByWeekday(fasts, zone)
        assertEquals(Duration.ofHours(8), byDay[DayOfWeek.TUESDAY])
        val wv = Insights.weightVsFasting(fasts, listOf(WeightLog(at = at(fri, 8), value = 176.37, unit = WeightUnit.LB)), zone, at(fri, 23))
        assertEquals(80.0, wv.last().averageWeightKg!!, 0.01)
    }

    // Timeline
    @Test fun `every timeline claim has a label and a source`() {
        val claims = BodyTimeline.stages.flatMap { it.claims }
        assertTrue(claims.isNotEmpty())
        claims.forEach { assertTrue(it.source.isNotBlank() && it.text.isNotBlank()) }
        val autophagy = claims.filter { "utophagy" in it.text }
        assertTrue(autophagy.isNotEmpty() && autophagy.all { it.label == EvidenceLabel.UNKNOWN })
        assertEquals(BodyTimeline.stages.sortedBy { it.fromHours }, BodyTimeline.stages)
        assertEquals(18, BodyTimeline.stageAt(hours(20))!!.fromHours)
        assertEquals(24, BodyTimeline.nextStage(hours(20))!!.fromHours)
    }

    // Program
    @Test fun `draft program cannot surface a timer target or accept old progress`() {
        val draft = GuidedPrograms.fastingShift21
        val oldProgress = (1..21).associateWith { at(fri, 9) }
        assertFalse(draft.isPublished)
        assertNull(GuidedPrograms.byId(draft.id))
        assertNull(ProgramProgress.currentDay(draft, oldProgress, zone, fri))
        assertFalse(ProgramProgress.isFinished(draft, oldProgress))
    }

    @Test fun `program unlocks daily only after a complete release`() {
        val days = (1..21).map { day ->
            ProgramDay(day, listOf("en", "ms", "id").associateWith { lang ->
                ProgramReading("Title $day $lang", "Reading $lang", "Action $lang", "Question $lang", "Page $day")
            })
        }
        val p = Program(id = "test", days = days, expectedDays = 21, approvedForRelease = true)
        assertFalse(p.copy(approvedForRelease = false).isPublished)
        assertFalse(p.copy(days = days.dropLast(1)).isPublished)
        assertFalse(p.copy(days = days.reversed()).isPublished)
        val last = days.last()
        assertNull(p.copy(approvedForRelease = false).freePreview)
        val previewOnly = p.copy(days = days.take(1), approvedForRelease = false, firstDayApprovedForPreview = true)
        assertEquals(days.first(), previewOnly.freePreview)
        assertFalse(previewOnly.isPublished)
        assertNull(ProgramProgress.currentDay(previewOnly, emptyMap(), zone, fri))
        assertNull(previewOnly.copy(days = listOf(days[1])).freePreview)
        assertNull(previewOnly.copy(days = listOf(days.first().copy(readings = days.first().readings - "ms"))).freePreview)
        assertEquals(days.first(), p.freePreview)
        assertEquals(days.first().reading("id"), p.freePreview?.reading("in"))
        assertEquals(days.first().reading("en"), p.freePreview?.reading("fr"))
        assertFalse(p.copy(days = days.dropLast(1) + last.copy(readings = last.readings - "ms")).isPublished)
        val incomplete = last.readings + ("id" to last.readings.getValue("id").copy(action = ""))
        assertNull(p.copy(days = days.dropLast(1) + last.copy(readings = incomplete)).freePreview)
        assertEquals(1, ProgramProgress.currentDay(p, emptyMap(), zone, fri)!!.day)
        val doneToday = mapOf(1 to at(fri, 9))
        assertNull(ProgramProgress.currentDay(p, doneToday, zone, fri))
        assertEquals(2, ProgramProgress.currentDay(p, doneToday, zone, fri.plusDays(1))!!.day)
        assertTrue(ProgramProgress.isFinished(p, (1..21).associateWith { at(fri, 9) }))
    }

    // Billing
    @Test fun `billing reminders and paywall rules`() {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        val trial = Entitlement(PlanType.ANNUAL, active = true, inTrial = true, expiresAt = now.plus(Duration.ofDays(7)), willRenew = true)
        val r = BillingReminders.plan(trial, now)!!
        assertEquals(BillingReminderKind.TRIAL_ENDING, r.kind)
        assertEquals(now.plus(Duration.ofDays(4)), r.at)
        val annual = trial.copy(inTrial = false, expiresAt = now.plus(Duration.ofDays(30)))
        assertEquals(now.plus(Duration.ofDays(23)), BillingReminders.plan(annual, now)!!.at)
        assertNull(BillingReminders.plan(annual.copy(willRenew = false), now))
        assertNull(BillingReminders.plan(Entitlement.FREE, now))
        assertEquals(now, BillingReminders.plan(annual.copy(expiresAt = now.plus(Duration.ofDays(2))), now)!!.at)

        val free = Entitlement.FREE
        Feature.entries.filter { !it.requiresShiftPlus }.forEach { assertTrue(it.isAvailable(free), "$it") }
        assertFalse(Feature.INSIGHTS.isAvailable(free))
        assertTrue(Feature.SAFETY_LAYER in Feature.entries.filter { !it.requiresShiftPlus })
    }

    // Backup
    @Test fun `backup round trip and validation`() {
        val b = Backup(
            exportedAt = 1L,
            fasts = listOf(FastRecord(1, 1000, 2000, 1500, "window_16_8", "ENDED_EARLY", "note, with \"quotes\"")),
            symptomCheckins = listOf(SymptomCheckinRecord(1, 1, 1200, listOf("HUNGER"), false)),
            streakFreezes = listOf("2026-11-19"),
        )
        assertEquals(b, BackupCodec.decode(BackupCodec.encode(b)))
        assertFailsWith<BackupFormatException> { BackupCodec.decode("{}") }
        assertFailsWith<BackupFormatException> { BackupCodec.decode("not json") }
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(b.copy(version = 99))) }
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(b.copy(fasts = emptyList()))) }
        val csv = CsvExport.files(b).getValue("fasts.csv")
        assertTrue("\"note, with \"\"quotes\"\"\"" in csv)
        assertNotNull(CsvExport.files(b)["symptom_checkins.csv"])
    }

    @Test fun `support numbers belong to the selected country`() {
        assertEquals(listOf("1767", "1771"), Helplines.forRegion("sg").map { it.phone })
        assertEquals("+60 3-7627 2929", Helplines.forRegion("MY").single().phone)
        val indonesia = Helplines.forRegion("ID").single()
        assertEquals("119", indonesia.phone)
        assertEquals(8, indonesia.extension)
        assertEquals("12356", Helplines.forRegion("cn").single().phone)
        assertTrue(Helplines.forRegion("ZZ").isEmpty())
        assertTrue(Helplines.forRegion(null).isEmpty())
        assertEquals("988", Helplines.forRegion("us").first().phone)
        assertEquals(
            listOf("0808 801 0677", "0808 801 0432", "0808 801 0433", "0808 801 0434"),
            Helplines.forRegion("GB").map { it.phone },
        )
    }
}
