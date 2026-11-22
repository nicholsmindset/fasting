package com.fastingshift.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.export.DoctorReportPdf
import com.fastingshift.app.export.DoctorReports
import com.fastingshift.data.repo.SymptomCheckin
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.service.ScreeningRecord
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Report for your doctor": what goes into the PDF, and that a PDF is produced. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, shadows = [ShadowPdfDocument::class])
class DoctorReportTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val now = Instant.parse("2026-11-22T04:00:00Z") // 12:00 in Singapore

    private fun fast(id: Long, daysAgo: Long, hours: Long, planned: Long = 16, kind: FastKind = FastKind.REGULAR): Fast {
        val start = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(hours + 1))
        return Fast(id = id, startAt = start, targetEndAt = start.plus(Duration.ofHours(planned)), protocolId = "window_16_8", kind = kind)
            .end(start.plus(Duration.ofHours(hours)))
    }

    private val fasts = listOf(
        fast(1, daysAgo = 1, hours = 16),
        fast(2, daysAgo = 3, hours = 12),
        fast(3, daysAgo = 5, hours = 13, planned = 13, kind = FastKind.SUNNAH),
        fast(4, daysAgo = 45, hours = 18), // outside 30 days, inside 90
        Fast(id = 5, startAt = now.minus(Duration.ofHours(2)), targetEndAt = now.plus(Duration.ofHours(14)), protocolId = "window_16_8"), // active
    )

    private val checkins = listOf(
        SymptomCheckin(1, fastId = 2, at = fasts[1].startAt.plus(Duration.ofHours(8)), answers = setOf(Symptom.HEADACHE, Symptom.PALPITATIONS), redFlag = true),
        SymptomCheckin(2, fastId = 1, at = fasts[0].startAt.plus(Duration.ofHours(8)), answers = emptySet(), redFlag = false),
        SymptomCheckin(3, fastId = 4, at = fasts[3].startAt.plus(Duration.ofHours(8)), answers = setOf(Symptom.FATIGUE), redFlag = false),
    )

    private val weights = listOf(
        WeightLog(1, now.minus(Duration.ofDays(20)), 80.0, WeightUnit.KG),
        WeightLog(2, now.minus(Duration.ofDays(2)), 79.0, WeightUnit.KG),
        WeightLog(3, now.minus(Duration.ofDays(60)), 82.0, WeightUnit.KG),
    )

    private val screening = ScreeningRecord(
        answeredAt = now.minus(Duration.ofDays(10)),
        answers = ScreeningAnswers(52, false, false, false, setOf(Medication.SULFONYLUREA, Medication.BLOOD_PRESSURE), 170.0, 80.0),
        gateResult = GateResult.SOFT,
        clinicianAcknowledged = true,
    )

    private fun assemble(days: Int = 30, hideWeight: Boolean = false, unit: WeightUnit = WeightUnit.KG) =
        DoctorReports.assemble(days, now, zone, fasts, checkins, weights, unit, hideWeight, screening)

    @Test fun periodAndStats() {
        val r = assemble()
        assertEquals(LocalDate.of(2026, 10, 24), r.from)
        assertEquals(LocalDate.of(2026, 11, 22), r.to)
        // Ended fasts that started in the period, oldest first; the active fast is left out.
        assertEquals(listOf(13L, 12L, 16L), r.fasts.map { it.length.toHours() })
        assertEquals(3, r.fastCount)
        assertEquals(Duration.ofHours(41), r.total)
        assertEquals(Duration.ofHours(41).dividedBy(3), r.average)
        assertEquals(Duration.ofHours(16), r.longest)
        assertEquals(FastKind.SUNNAH, r.fasts.first().kind)
        assertEquals(FastStatus.ENDED_EARLY, r.fasts[1].status)
    }

    @Test fun ninetyDaysIncludesOlderFasts() {
        val r = assemble(days = 90)
        assertEquals(4, r.fastCount)
        assertEquals(Duration.ofHours(18), r.longest)
        assertEquals(3, r.checkins.size)
        assertEquals(1, r.redFlagCount)
    }

    @Test fun checkinsWithRedFlagsFirst() {
        val r = assemble()
        assertEquals(2, r.checkins.size) // the 45-day-old one is out
        val red = r.checkins.first { it.redFlag }
        assertEquals(listOf(Symptom.PALPITATIONS, Symptom.HEADACHE), red.symptoms)
        assertEquals(1, r.redFlagCount)
        assertTrue(r.checkins.zipWithNext().all { (a, b) -> !a.at.isAfter(b.at) })
    }

    @Test fun weightTrendInTheUsersUnit() {
        val r = assemble(unit = WeightUnit.LB)
        val w = assertNotNull(r.weight)
        assertEquals(2, w.points.size)
        assertEquals(80.0 * 2.2046226, w.first!!.value, 0.01)
        assertEquals(-2.2046226, w.change!!, 0.01)
    }

    @Test fun hideWeightOmitsWeightAndBmi() {
        val r = assemble(hideWeight = true)
        assertNull(r.weight)
        assertNull(r.screening!!.bmi)
    }

    @Test fun screeningAnswersMedicationsAndReasons() {
        val s = assertNotNull(assemble().screening)
        assertEquals(GateResult.SOFT, s.gate)
        assertEquals(listOf(Medication.SULFONYLUREA, Medication.BLOOD_PRESSURE), s.medications)
        assertEquals(listOf(GateReason.GLUCOSE_LOWERING_MEDICATION, GateReason.BLOOD_PRESSURE_OR_DIURETIC), s.reasons)
        assertEquals(52, s.ageYears)
        assertTrue(s.clinicianAcknowledged)
        assertEquals(80.0 / (1.7 * 1.7), s.bmi!!, 0.001)
    }

    @Test fun emptyReport() {
        val r = DoctorReports.assemble(30, now, zone, emptyList(), emptyList(), emptyList(), WeightUnit.KG, false, null)
        assertEquals(0, r.fastCount)
        assertEquals(Duration.ZERO, r.average)
        assertNull(r.screening)
        assertTrue(r.weight!!.points.isEmpty())
    }

    @Test fun pdfHasAtLeastOnePage() {
        val res = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val out = ByteArrayOutputStream()
        val pages = DoctorReportPdf.render(res, assemble(days = 90), out)
        assertTrue(pages >= 1, "pages: $pages")
    }

    @Test fun longReportsBreakOntoMorePages() {
        val many = (1L..80L).map { fast(100 + it, daysAgo = it, hours = 14) }
        val r = DoctorReports.assemble(90, now, zone, many, emptyList(), emptyList(), WeightUnit.KG, true, screening)
        val res = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        assertTrue(DoctorReportPdf.render(res, r, ByteArrayOutputStream()) >= 2)
    }
}
