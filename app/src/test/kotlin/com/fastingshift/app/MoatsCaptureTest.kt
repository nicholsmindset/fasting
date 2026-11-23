package com.fastingshift.app

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.ChallengeRepository
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual check of the moats screens (writes to /tmp/moats-captures, never docs/screenshots).
 * Run with: gradle :app:testDebugUnitTest -Pscreenshots --tests '*MoatsCaptureTest'
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class)
class MoatsCaptureTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var metrics: MetricRepository
    @Inject lateinit var challenges: ChallengeRepository

    private var outDir: String? = System.getProperty("fastingshift.screenshotDir")?.let { "/tmp/moats-captures" }

    @Before fun setUp() {
        assumeTrue("screenshots run only with -Pscreenshots", outDir != null)
        hilt.inject()
        runBlocking {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            settings.setOnboardingComplete(true)
            settings.setRamadanCity("sg_singapore", PrayerMethod.MUIS)
            screening.save(ScreeningRecord(now, ScreeningAnswers(42, false, false, false, emptySet(), 178.0, 82.0), GateResult.OK, false))
            val today = LocalDate.now(zone)
            for (back in 12 downTo 1) {
                val start = today.minusDays(back.toLong()).atTime(20, 0).atZone(zone).toInstant()
                val kind = when (back) { 3 -> FastKind.SUNNAH; 7 -> FastKind.QADA; else -> FastKind.REGULAR }
                val f = fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8", kind = kind))
                fasts.update(f.end(start.plus(Duration.ofHours(16)).plusSeconds(600)))
            }
            challenges.join(Challenge(type = ChallengeType.DAILY_PROTOCOL, protocolId = "window_16_8", startDate = today.minusDays(5), lengthDays = 7, joinedAt = now))
            metrics.add(MetricLog(at = now.minus(Duration.ofHours(3)), type = MetricType.GLUCOSE, value = 5.2, unit = MetricUnit.MMOL_L))
            metrics.add(MetricLog(at = now.minus(Duration.ofHours(1)), type = MetricType.KETONE, value = 0.8, unit = MetricUnit.MMOL_L))
        }
    }

    @After fun tearDown() {
        if (outDir == null) return
        runBlocking { settings.setOnboardingComplete(false) }
    }

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(outDir, "$name.png").path)
    }
    private fun scrollTo(text: String) = compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    private fun back() = compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { println("CAPTURE STEP FAILED: $name: ${it.message?.take(200)}") }
    }

    private fun run() {
        waitFor(s(R.string.tab_settings))
        step("hub") {
            compose.onNodeWithText(s(R.string.tab_settings)).performClick()
            waitFor(s(R.string.settings_plan))
            scrollTo(s(R.string.muslim_hub_entry))
            shoot("settings-sunnah")
            compose.onNodeWithText(s(R.string.muslim_hub_entry)).performClick()
            waitFor(s(R.string.muslim_upcoming))
            shoot("hub-1")
        }
        step("hub-bottom") {
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp(); swipeUp(); swipeUp(); swipeUp(); swipeUp() }
            shoot("hub-2")
        }
        step("trust") {
            back()
            waitFor(s(R.string.settings_plan))
            scrollTo(s(R.string.report_row))
            shoot("settings-report")
            scrollTo(s(R.string.trust_row))
            compose.onNodeWithText(s(R.string.trust_row)).performClick()
            waitFor(s(R.string.trust_title))
            shoot("trust-1")
            scrollTo(s(R.string.trust_delete))
            shoot("trust-2")
            back()
        }
        step("history") {
            compose.onNodeWithText(s(R.string.tab_history)).performClick()
            waitFor(s(R.string.history_recent))
            compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(s(R.string.history_challenges)))
            shoot("history-challenges-row")
            compose.onNodeWithText(s(R.string.history_challenges)).performClick()
            compose.waitForIdle()
            shoot("challenges")
        }
        step("challenge-detail") {
            compose.onAllNodes(hasClickAction()).filter(hasText("16:8", substring = true)).onFirst().performClick()
            compose.waitForIdle()
            shoot("challenge-detail")
            back(); back()
        }
        step("history-kinds") {
            compose.onNodeWithText(s(R.string.tab_history)).performClick()
            waitFor(s(R.string.history_recent))
            compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(s(R.string.fast_kind_qada)))
            shoot("history-kinds")
        }
        step("log") {
            compose.onNodeWithText(s(R.string.tab_log)).performClick()
            waitFor(s(R.string.metrics_title))
            scrollTo(s(R.string.metrics_title))
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            shoot("log-metrics")
        }
    }

    @Config(qualifiers = "w411dp-h822dp-notnight-xxhdpi")
    @Test fun light() { outDir = "$outDir/en"; run() }

    @Config(qualifiers = "ms-w411dp-h822dp-notnight-xxhdpi")
    @Test fun malay() { outDir = "$outDir/ms"; run() }
}
