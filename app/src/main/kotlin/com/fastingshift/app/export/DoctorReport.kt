package com.fastingshift.app.export

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fastingshift.app.R
import com.fastingshift.app.share.FileSharing
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.SymptomCheckin
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningEvaluator
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.app.ui.safety.symptomLabel
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/*
 * "Report for your doctor" (moat B): a one- or few-page A4 PDF the user can bring to an
 * appointment. Generated on the device, offline; it leaves the phone only if the user shares it.
 * Assembly ([DoctorReports.assemble]) is pure and unit-tested; [DoctorReportPdf] only draws.
 */

/** Periods offered in Settings. */
enum class ReportPeriod(val days: Int) { DAYS_30(30), DAYS_90(90) }

data class ReportFast(val startAt: Instant, val length: Duration, val status: FastStatus, val kind: FastKind, val protocolId: String)

data class ReportCheckin(val at: Instant, val symptoms: List<Symptom>, val redFlag: Boolean)

data class ReportWeight(val at: Instant, val value: Double)

data class WeightTrend(val unit: WeightUnit, val points: List<ReportWeight>) {
    val first: ReportWeight? get() = points.firstOrNull()
    val last: ReportWeight? get() = points.lastOrNull()
    val change: Double? get() = if (points.size >= 2) points.last().value - points.first().value else null
}

data class ReportScreening(
    val answeredAt: Instant,
    val gate: GateResult,
    val reasons: List<GateReason>,
    val medications: List<Medication>,
    val ageYears: Int,
    val pregnantOrBreastfeeding: Boolean,
    val eatingDisorderHistory: Boolean,
    val type1Diabetes: Boolean,
    /** Null when the user hides weight. */
    val bmi: Double?,
    val clinicianAcknowledged: Boolean,
)

data class DoctorReportData(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val generatedAt: Instant,
    val zone: ZoneId,
    val fasts: List<ReportFast>,
    val checkins: List<ReportCheckin>,
    /** Null when the user hides weight (SR-6): the section is left out entirely. */
    val weight: WeightTrend?,
    val screening: ReportScreening?,
) {
    val fastCount: Int get() = fasts.size
    val total: Duration get() = fasts.fold(Duration.ZERO) { a, f -> a + f.length }
    val average: Duration get() = if (fasts.isEmpty()) Duration.ZERO else total.dividedBy(fasts.size.toLong())
    val longest: Duration get() = fasts.maxOfOrNull { it.length } ?: Duration.ZERO
    val redFlagCount: Int get() = checkins.count { it.redFlag }
}

object DoctorReports {
    /**
     * Builds the report for the [days] ending today (local). Only fasts that STARTED in the
     * period and have ended are listed; check-ins are those of the listed fasts. Weight is
     * converted to [weightUnit] and omitted when [hideWeight] is on.
     */
    fun assemble(
        days: Int,
        now: Instant,
        zone: ZoneId,
        fasts: List<Fast>,
        checkins: List<SymptomCheckin>,
        weights: List<WeightLog>,
        weightUnit: WeightUnit,
        hideWeight: Boolean,
        screening: ScreeningRecord?,
    ): DoctorReportData {
        require(days in 1..366) { "period must be 1..366 days" }
        val to = now.atZone(zone).toLocalDate()
        val from = to.minusDays(days - 1L)
        val start = from.atStartOfDay(zone).toInstant()
        fun inPeriod(at: Instant) = !at.isBefore(start) && !at.isAfter(now)

        val listed = fasts.filter { !it.isActive && inPeriod(it.startAt) }.sortedBy { it.startAt }
        val ids = listed.map { it.id }.toSet()
        return DoctorReportData(
            from = from,
            to = to,
            days = days,
            generatedAt = now,
            zone = zone,
            fasts = listed.map { ReportFast(it.startAt, it.actualDuration ?: Duration.ZERO, it.status, it.kind, it.protocolId) },
            checkins = checkins.filter { it.fastId in ids }.sortedBy { it.at }
                .map { c -> ReportCheckin(c.at, c.answers.sortedWith(compareBy({ !it.isRedFlag }, { it.ordinal })), c.redFlag) },
            weight = if (hideWeight) null else WeightTrend(
                weightUnit,
                // One entry per day (the day's last), oldest first.
                weights.filter { inPeriod(it.at) }.sortedBy { it.at }
                    .associateBy { it.at.atZone(zone).toLocalDate() }.values
                    .map { ReportWeight(it.at, convertWeight(it.value, it.unit, weightUnit)) },
            ),
            screening = screening?.let { r ->
                val a = r.answers
                ReportScreening(
                    answeredAt = r.answeredAt,
                    gate = r.gateResult,
                    reasons = ScreeningEvaluator.evaluate(a).reasons.sortedBy { it.ordinal },
                    medications = a.medications.sortedBy { it.ordinal },
                    ageYears = a.ageYears,
                    pregnantOrBreastfeeding = a.pregnantOrBreastfeeding,
                    eatingDisorderHistory = a.eatingDisorderHistory,
                    type1Diabetes = a.type1Diabetes,
                    bmi = if (hideWeight) null else a.bmi,
                    clinicianAcknowledged = r.clinicianAcknowledged,
                )
            },
        )
    }
}

/** Gathers the report from the repositories. */
@Singleton
class DoctorReportBuilder @Inject constructor(
    private val fasts: FastRepository,
    private val checkins: SymptomCheckinRepository,
    private val logs: LogRepository,
    private val screening: ScreeningRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun build(period: ReportPeriod, zone: ZoneId = ZoneId.systemDefault()): DoctorReportData = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val all = fasts.getAll()
        val now = clock.instant()
        val since = now.minus(Duration.ofDays(period.days + 1L))
        val recent = all.filter { !it.startAt.isBefore(since) && !it.isActive }
        val checks = recent.flatMap { checkins.observeForFast(it.id).first() }
        DoctorReports.assemble(
            days = period.days,
            now = now,
            zone = zone,
            fasts = recent,
            checkins = checks,
            weights = if (s.hideWeight) emptyList() else logs.getWeights(),
            weightUnit = s.weightUnit,
            hideWeight = s.hideWeight,
            screening = screening.getLatest(),
        )
    }
}

/** Builds, renders and shares the PDF. */
@Singleton
class DoctorReportExporter @Inject constructor(private val builder: DoctorReportBuilder) {
    /** Writes the PDF for [period] into the shareable cache folder. Uses [context]'s language. */
    suspend fun write(context: Context, period: ReportPeriod): File {
        val data = builder.build(period)
        return withContext(Dispatchers.IO) {
            val file = FileSharing.newFile(context, "fasting-shift-report-${data.to}.pdf")
            file.outputStream().use { DoctorReportPdf.render(context.resources, data, it) }
            file
        }
    }

    suspend fun share(context: Context, period: ReportPeriod) {
        val file = write(context, period)
        FileSharing.share(context, file, "application/pdf", subject = context.getString(R.string.report_title))
    }
}

/** Draws [DoctorReportData] as an A4 PDF with [PdfDocument]: one column, clean type, auto page breaks. */
object DoctorReportPdf {
    const val PAGE_WIDTH = 595 // A4 in PostScript points
    const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val FOOTER = 28f

    private val ink = Color.rgb(28, 28, 30)
    private val muted = Color.rgb(99, 99, 102)
    private val accent = Color.rgb(0, 122, 110)
    private val rule = Color.rgb(209, 209, 214)
    private val red = Color.rgb(196, 30, 58)
    private val redTint = Color.rgb(253, 236, 238)

    /** Renders [data] to [out] and returns the number of pages. */
    fun render(res: Resources, data: DoctorReportData, out: OutputStream): Int {
        val doc = PdfDocument()
        try {
            draw(res, data, PdfPages(doc))
            doc.writeTo(out)
            return doc.pages.size
        } finally {
            // Never let close() hide the real error from a half-written page.
            runCatching { doc.close() }
        }
    }

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Where pages are drawn: the PDF, or bitmaps in a layout preview test. */
    interface Pages {
        /** Starts page [number] (1-based) and returns its canvas. */
        fun start(number: Int): Canvas
        fun finish()
    }

    private class PdfPages(private val doc: PdfDocument) : Pages {
        private var page: PdfDocument.Page? = null
        override fun start(number: Int): Canvas {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
            return doc.startPage(info).also { page = it }.canvas
        }
        override fun finish() {
            page?.let(doc::finishPage)
            page = null
        }
    }

    /** Draws every page of [data] onto [pages]; returns the page count. */
    fun draw(res: Resources, data: DoctorReportData, pages: Pages): Int {
        val w = Writer(pages, res)
        w.content(data)
        w.finish()
        return w.pageCount
    }

    private class Writer(private val pages: Pages, private val res: Resources) {
        private val locale: Locale = res.configuration.locales[0] ?: Locale.getDefault()
        private val dateF = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        private val dateTimeF = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)

        private val title = paint(22f, bold = true)
        private val h2 = paint(13f, accent, bold = true)
        private val body = paint(10f)
        private val bodyBold = paint(10f, bold = true)
        private val small = paint(8.5f, muted)
        private val redText = paint(10f, red, bold = true)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 0.6f }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private val width = PAGE_WIDTH - 2 * MARGIN
        private var page: Canvas? = null
        var pageCount = 0
            private set
        private var y = 0f
        private val canvas: Canvas get() = page!!
        private lateinit var zone: ZoneId

        private fun s(id: Int, vararg args: Any) = res.getString(id, *args)
        private fun date(at: Instant) = dateF.format(at.atZone(zone))
        private fun dateTime(at: Instant) = dateTimeF.format(at.atZone(zone))
        private fun len(d: Duration) = d.hoursMinutes(res)

        fun content(d: DoctorReportData) {
            zone = d.zone
            newPage()
            text(s(R.string.report_title), title)
            y += 4f
            text(s(R.string.report_period, dateF.format(d.from), dateF.format(d.to), d.days), body)
            text(s(R.string.report_generated, dateTime(d.generatedAt)), small)
            y += 10f

            section(s(R.string.report_summary))
            row(listOf(s(R.string.report_fasts_count) to d.fastCount.toString(), s(R.string.report_total) to len(d.total)))
            row(listOf(s(R.string.report_average) to len(d.average), s(R.string.report_longest) to len(d.longest)))
            if (d.redFlagCount > 0) text(res.getQuantityString(R.plurals.report_red_flags_count, d.redFlagCount, d.redFlagCount), redText)

            section(s(R.string.report_fasts))
            if (d.fasts.isEmpty()) {
                text(s(R.string.report_no_fasts), body)
            } else {
                val cols = floatArrayOf(0f, 0.30f, 0.48f, 0.76f)
                tableRow(cols, listOf(s(R.string.report_col_date), s(R.string.report_col_length), s(R.string.report_col_status), s(R.string.report_col_kind)), bodyBold)
                d.fasts.forEach { f ->
                    tableRow(cols, listOf(date(f.startAt), len(f.length), statusLabel(f.status), kindLabel(f.kind)), body)
                }
            }

            section(s(R.string.report_checkins))
            if (d.checkins.isEmpty()) {
                text(s(R.string.report_no_checkins), body)
            } else {
                d.checkins.forEach { c -> checkin(c) }
            }

            d.weight?.let { wt ->
                section(s(R.string.report_weight))
                val first = wt.first
                val last = wt.last
                if (first == null || last == null) {
                    text(s(R.string.report_no_weights), body)
                } else {
                    val change = wt.change
                    text(s(R.string.report_weight_range, weight(first.value, wt.unit), date(first.at), weight(last.value, wt.unit), date(last.at)), body)
                    if (change != null) text(s(R.string.report_weight_change, signedWeight(change, wt.unit), wt.points.size), body)
                    val cols = floatArrayOf(0f, 0.30f)
                    wt.points.forEach { p -> tableRow(cols, listOf(date(p.at), weight(p.value, wt.unit)), body) }
                }
            }

            section(s(R.string.report_screening))
            val sc = d.screening
            if (sc == null) {
                text(s(R.string.report_no_screening), body)
            } else {
                text(s(R.string.report_screening_date, date(sc.answeredAt)), small)
                labeled(s(R.string.report_gate), gateLabel(sc.gate, sc.clinicianAcknowledged))
                if (sc.reasons.isNotEmpty()) labeled(s(R.string.report_reasons), sc.reasons.joinToString("; ") { s(reasonLabel(it)) })
                labeled(
                    s(R.string.report_medications),
                    if (sc.medications.isEmpty()) s(R.string.report_none) else sc.medications.joinToString("; ") { s(medicationLabel(it)) },
                )
                labeled(s(R.string.report_age), sc.ageYears.toString())
                labeled(s(R.string.report_pregnant), yesNo(sc.pregnantOrBreastfeeding))
                labeled(s(R.string.report_ed), yesNo(sc.eatingDisorderHistory))
                labeled(s(R.string.report_t1d), yesNo(sc.type1Diabetes))
                sc.bmi?.let { labeled(s(R.string.report_bmi), String.format(locale, "%.1f", it)) }
                labeled(s(R.string.report_clinician_ack), yesNo(sc.clinicianAcknowledged))
            }

            y += 14f
            ensure(60f)
            canvas.drawLine(MARGIN, y, MARGIN + width, y, line)
            y += 8f
            text(s(R.string.report_disclaimer), small)
        }

        fun finish() {
            if (page != null) { footer(); pages.finish() }
            page = null
        }

        private fun newPage() {
            if (page != null) { footer(); pages.finish() }
            pageCount++
            page = pages.start(pageCount)
            y = MARGIN
        }

        private fun footer() {
            val text = s(R.string.report_footer, pageCount)
            canvas.drawText(text, MARGIN, PAGE_HEIGHT - MARGIN / 2f, small)
        }

        /** Starts a new page if [h] points don't fit. */
        private fun ensure(h: Float) {
            if (y + h > PAGE_HEIGHT - MARGIN - FOOTER) newPage()
        }

        private fun layout(t: String, p: TextPaint, w: Float): StaticLayout =
            StaticLayout.Builder.obtain(t, 0, t.length, p, w.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1f)
                .build()

        private fun text(t: String, p: TextPaint, indent: Float = 0f) {
            val l = layout(t, p, width - indent)
            ensure(l.height.toFloat())
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height + 3f
        }

        private fun section(t: String) {
            y += 12f
            ensure(48f) // keep a heading with at least a couple of lines after it
            text(t, h2)
            canvas.drawLine(MARGIN, y, MARGIN + width, y, line)
            y += 6f
        }

        private fun labeled(label: String, value: String) {
            val labelW = width * 0.38f
            val a = layout(label, small, labelW - 8f)
            val b = layout(value, body, width - labelW)
            val h = maxOf(a.height, b.height).toFloat()
            ensure(h)
            draw(a, MARGIN, y + 1f)
            draw(b, MARGIN + labelW, y)
            y += h + 4f
        }

        private fun row(pairs: List<Pair<String, String>>) {
            val colW = width / pairs.size
            val labels = pairs.map { layout(it.first, small, colW - 8f) }
            val values = pairs.map { layout(it.second, paint(15f, bold = true), colW - 8f) }
            val h = labels.maxOf { it.height } + values.maxOf { it.height } + 2f
            ensure(h)
            pairs.indices.forEach { i ->
                draw(labels[i], MARGIN + i * colW, y)
                draw(values[i], MARGIN + i * colW, y + labels[i].height + 1f)
            }
            y += h + 8f
        }

        private fun tableRow(cols: FloatArray, cells: List<String>, p: TextPaint) {
            val layouts = cells.mapIndexed { i, c ->
                val next = if (i + 1 < cols.size) cols[i + 1] else 1f
                layout(c, p, (next - cols[i]) * width - 6f)
            }
            val h = layouts.maxOf { it.height }.toFloat()
            ensure(h + 4f)
            layouts.forEachIndexed { i, l -> draw(l, MARGIN + cols[i] * width, y + 2f) }
            y += h + 4f
            canvas.drawLine(MARGIN, y, MARGIN + width, y, line)
        }

        private fun checkin(c: ReportCheckin) {
            val names = if (c.symptoms.isEmpty()) s(R.string.report_felt_fine) else c.symptoms.joinToString(", ") { s(symptomLabel(it)) }
            val head = layout(dateTime(c.at) + if (c.redFlag) "  ·  " + s(R.string.report_red_flag) else "", if (c.redFlag) redText else bodyBold, width - 16f)
            val list = layout(names, body, width - 16f)
            val h = head.height + list.height + 10f
            ensure(h)
            if (c.redFlag) {
                fill.color = redTint
                canvas.drawRoundRect(MARGIN, y, MARGIN + width, y + h, 6f, 6f, fill)
                fill.color = red
                canvas.drawRect(MARGIN, y, MARGIN + 3f, y + h, fill)
            }
            draw(head, MARGIN + 10f, y + 5f)
            draw(list, MARGIN + 10f, y + 5f + head.height)
            y += h + 4f
        }

        private fun draw(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        private fun yesNo(b: Boolean) = s(if (b) R.string.report_yes else R.string.report_no)

        private fun weight(v: Double, unit: WeightUnit) =
            s(if (unit == WeightUnit.KG) R.string.unit_kg_value else R.string.unit_lb_value, v)

        private fun signedWeight(v: Double, unit: WeightUnit) = (if (v > 0) "+" else "") + weight(v, unit)

        private fun statusLabel(st: FastStatus) = s(
            when (st) {
                FastStatus.COMPLETED -> R.string.status_completed
                FastStatus.ENDED_EARLY -> R.string.status_ended
                FastStatus.ACTIVE -> R.string.status_active
            },
        )

        private fun kindLabel(k: FastKind) = s(kindLabelRes(k))

        private fun gateLabel(g: GateResult, ack: Boolean) = s(
            when (g) {
                GateResult.OK -> R.string.report_gate_ok
                GateResult.SOFT -> if (ack) R.string.report_gate_soft_ack else R.string.report_gate_soft
                GateResult.BLOCK -> R.string.report_gate_block
            },
        )
    }
}

fun kindLabelRes(k: FastKind) = when (k) {
    FastKind.REGULAR -> R.string.report_kind_regular
    FastKind.RAMADAN -> R.string.report_kind_ramadan
    FastKind.SUNNAH -> R.string.report_kind_sunnah
    FastKind.QADA -> R.string.report_kind_qada
}

fun medicationLabel(m: Medication) = when (m) {
    Medication.INSULIN -> R.string.med_insulin
    Medication.SULFONYLUREA -> R.string.med_sulfonylurea
    Medication.SGLT2_INHIBITOR -> R.string.med_sglt2
    Medication.DIURETIC -> R.string.med_diuretic
    Medication.BLOOD_PRESSURE -> R.string.med_bp
    Medication.GLP1 -> R.string.med_glp1
}

fun reasonLabel(r: GateReason) = when (r) {
    GateReason.UNDER_18 -> R.string.report_reason_under_18
    GateReason.PREGNANT_OR_BREASTFEEDING -> R.string.report_reason_pregnant
    GateReason.EATING_DISORDER_HISTORY -> R.string.report_reason_ed
    GateReason.LOW_BMI -> R.string.report_reason_low_bmi
    GateReason.TYPE_1_DIABETES -> R.string.report_reason_t1d
    GateReason.GLUCOSE_LOWERING_MEDICATION -> R.string.report_reason_glucose
    GateReason.SGLT2_INHIBITOR -> R.string.report_reason_sglt2
    GateReason.BLOOD_PRESSURE_OR_DIURETIC -> R.string.report_reason_bp
    GateReason.GLP1 -> R.string.report_reason_glp1
}
