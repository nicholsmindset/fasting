package com.fastingshift.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.export.DoctorReportPdf
import com.fastingshift.app.export.DoctorReports
import com.fastingshift.app.share.ShareCard
import com.fastingshift.app.share.ShareCards
import com.fastingshift.app.ui.history.CalendarCard
import com.fastingshift.app.ui.history.HistoryState
import com.fastingshift.app.ui.history.WeekRow
import com.fastingshift.app.ui.plus.BodySignalsSection
import com.fastingshift.app.ui.plus.BodySignalsState
import com.fastingshift.app.ui.safety.RamadanSafetyContent
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.insights.BodySignalsReport
import com.fastingshift.domain.insights.GlucoseComparison
import com.fastingshift.domain.insights.RestingHeartRateComparison
import com.fastingshift.domain.insights.SleepComparison
import com.fastingshift.domain.insights.WeeklyRestingHeartRate
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.WeightUnit
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture the actual health cards and exported layouts with synthetic values only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
class ComponentMoatsCaptureTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val res get() = ApplicationProvider.getApplicationContext<Application>().resources
    private val monday = LocalDate.of(2026, 11, 16)

    @Before fun requireScreenshots() {
        assumeTrue("captures require -Pscreenshots", System.getProperty("fastingshift.screenshotDir") != null)
    }

    private fun captureCard(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            FastingShiftTheme(darkTheme = darkTheme) {
                Surface(Modifier.fillMaxWidth().testTag("capture"), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.padding(16.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
        val file = moatsOutput("en").resolve("$name.png")
        if (file.exists()) check(file.delete())
        compose.onNodeWithTag("capture").captureRoboImage(file.absolutePath)
        check(file.length() > 0) { "Missing screenshot: $file" }
    }

    @Test fun lightCalendarLabelContrast() = verifyCalendarLabelContrast(dark = false)

    @Test fun darkCalendarLabelContrast() = verifyCalendarLabelContrast(dark = true)

    private fun verifyCalendarLabelContrast(dark: Boolean) {
        val start = monday.atStartOfDay(moatsZone).toInstant()
        fun fast(day: Long): Fast {
            val from = start.plus(Duration.ofDays(day))
            return Fast(startAt = from, targetEndAt = from.plus(Duration.ofHours(16)), protocolId = "window_16_8")
        }
        val completed = fast(0).let { it.end(it.targetEndAt) }
        val endedEarly = fast(1).let { it.end(it.startAt.plus(Duration.ofHours(12))) }
        val active = fast(2)
        val now = active.startAt.plus(Duration.ofHours(13))
        val segments = HistoryStats.weekSegments(listOf(completed, endedEarly, active), moatsZone, monday, now)
        val state = HistoryState(month = YearMonth.from(monday), today = monday.plusDays(2), weeks = listOf(WeekRow(monday, segments)))
        val mode = if (dark) "dark" else "light"
        captureCard("history-calendar-contrast-$mode", darkTheme = dark) {
            CalendarCard(state, onShift = {}, onEditFast = {})
        }
        for (hours in listOf(16L, 12L, 13L)) {
            val label = res.getString(R.string.duration_h, hours)
            val file = temporary.root.resolve("$hours.png")
            compose.onNodeWithText(label, useUnmergedTree = true).captureRoboImage(file.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
            try {
                // The middle third contains solid glyphs over the bar fill, clear of rounded
                // corners and the active bar's fading end. Compare ink and fill in the same column.
                val contrast = (bitmap.width / 3 until 2 * bitmap.width / 3).maxOf { x ->
                    val luminances = (0 until bitmap.height).map { y -> Color.luminance(bitmap.getPixel(x, y)) }
                    (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
                }
                check(contrast >= 4.5f) { "$mode calendar $label contrast was $contrast:1" }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun bodySignals() {
        val weeks = listOf(62.0, 61.0, 59.0, 60.0, 63.0, 61.0).mapIndexed { i, bpm ->
            WeeklyRestingHeartRate(monday.minusWeeks(5L - i), bpm, i % 2 == 0)
        }
        val data = BodySignalsReport(
            sleep = SleepComparison(6, 9, Duration.ofMinutes(445), Duration.ofMinutes(435)),
            restingHeartRate = RestingHeartRateComparison(3, 3, 61.3, 60.7, weeks),
            glucose = GlucoseComparison(6, 7, 5.2, 5.6),
        )
        captureCard("body-signals") {
            BodySignalsSection(BodySignalsState(healthAvailable = true, granted = true, report = data), onGrant = {})
        }
    }

    @Test fun ramadanSafety() = captureCard("ramadan-safety") { RamadanSafetyContent() }

    private fun writePng(name: String, bitmap: Bitmap) {
        moatsOutput("en").resolve("$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode $name" }
        }
    }

    private fun finished(daysAgo: Long, hours: Long): Fast {
        val start = moatsNow.minus(Duration.ofDays(daysAgo))
        return Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8")
            .end(start.plus(Duration.ofHours(hours)))
    }

    @Test fun shareImages() {
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", monday, 7, "A week with friends")
        val cards = mapOf(
            "share-fast" to ShareCards.forFast(res, finished(3, 16), moatsZone),
            "share-week" to ShareCard.Week(monday, Duration.ofHours(62), 4),
            "share-challenge" to ShareCard.ChallengeProgress("A week with friends", 5, 7, ChallengeLinks.link(invite)),
        )
        cards.forEach { (name, card) ->
            val bitmap = ShareCards.render(res, card)
            try {
                check(bitmap.width == ShareCards.WIDTH && bitmap.height == ShareCards.HEIGHT)
                writePng(name, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun doctorReportFirstPage() {
        val report = DoctorReports.assemble(
            30, moatsNow, moatsZone, listOf(finished(4, 16), finished(2, 14)), emptyList(), emptyList(),
            WeightUnit.KG, hideWeight = true, screening = null,
        )
        // Draw through the PDF writer at 2 pixels per PostScript point. This exercises the same
        // wrapping, pagination, fonts and footer as the file the app shares on Android.
        val scale = 2
        val paper = Bitmap.createBitmap(DoctorReportPdf.PAGE_WIDTH * scale, DoctorReportPdf.PAGE_HEIGHT * scale, Bitmap.Config.ARGB_8888)
        try {
            var pageNumber = 0
            val count = DoctorReportPdf.draw(res, report, object : DoctorReportPdf.Pages {
                override fun start(number: Int): Canvas {
                    pageNumber = number
                    paper.eraseColor(Color.WHITE)
                    return Canvas(paper).apply { scale(scale.toFloat(), scale.toFloat()) }
                }

                override fun finish() {
                    if (pageNumber == 1) writePng("doctor-report-page-1", paper)
                }
            })
            check(count >= 1)
        } finally {
            paper.recycle()
        }
    }
}
