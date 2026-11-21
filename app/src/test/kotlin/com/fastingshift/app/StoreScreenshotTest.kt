package com.fastingshift.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
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
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import com.fastingshift.domain.timer.TimerDisplayMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Play Store screenshots from the real app with realistic seeded data.
 * Run with: ./gradlew :app:testDebugUnitTest -Pscreenshots  (writes PNGs to docs/screenshots)
 *
 * 411x822dp at xxhdpi = 1233x2466 px, within Play's 2:1 aspect-ratio limit. API 30 so the
 * brand colour scheme is used rather than the device's dynamic colours.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class)
class StoreScreenshotTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var logs: LogRepository

    private val outDir: String? = System.getProperty("fastingshift.screenshotDir")

    @Before fun setUp() {
        assumeTrue("screenshots run only with -Pscreenshots", outDir != null)
        hilt.inject()
        seed()
    }

    private fun seed() = runBlocking {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        settings.setOnboardingComplete(true)
        settings.setTimerMode(TimerDisplayMode.ELAPSED)
        settings.setMilestoneHours(setOf(12, 16))
        screening.save(ScreeningRecord(now, ScreeningAnswers(42, false, false, false, emptySet(), 178.0, 82.0), GateResult.OK, false))
        // An active fast, 13h 40m in.
        val activeStart = now.minus(Duration.ofHours(13)).minus(Duration.ofMinutes(40))
        // Three weeks of mostly-kept 16:8 days (one a day, leading up to the active fast), one
        // shorter day, one longer weekend fast. Relative to the active start so nothing overlaps.
        for (back in 21 downTo 1) {
            if (back == 9) continue // a day off: streaks are about consistency, not perfection
            val long = back == 6
            val start = activeStart.minus(Duration.ofDays(back.toLong())).minus(Duration.ofHours(if (long) 4 else 0))
            val planned = if (long) 24L else 16L
            val f = fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(planned)), protocolId = if (long) "duration_24" else "window_16_8"))
            val ended = Duration.ofHours(if (back == 13) 13L else planned).plus(Duration.ofMinutes(if (long) 0 else 12))
            fasts.update(f.end(start.plus(ended)))
        }
        fasts.insert(Fast(startAt = activeStart, targetEndAt = activeStart.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
        listOf(83.4, 83.0, 82.7, 82.5, 82.1).forEachIndexed { i, kg ->
            logs.addWeight(WeightLog(at = now.minus(Duration.ofDays((5 - i) * 4L)), value = kg, unit = WeightUnit.KG))
        }
        logs.addWater(WaterLog(at = now.minus(Duration.ofMinutes(3)), value = 750.0, unit = VolumeUnit.ML))
        logs.addWater(WaterLog(at = now.minus(Duration.ofMinutes(2)), value = 500.0, unit = VolumeUnit.ML))
        logs.addElectrolyte(ElectrolyteLog(at = now.minus(Duration.ofMinutes(2)), electrolyte = Electrolyte.SODIUM, milligrams = 1000.0))
        logs.addElectrolyte(ElectrolyteLog(at = now.minus(Duration.ofMinutes(2)), electrolyte = Electrolyte.MAGNESIUM, milligrams = 200.0))
    }

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(part: String) =
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription(part, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @After fun tearDown() {
        if (outDir == null) return
        runBlocking {
            settings.setOnboardingComplete(false)
            settings.setTimerMode(TimerDisplayMode.ELAPSED)
        }
    }

    private fun shoot(name: String, mode: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(outDir, "$name-$mode.png").path)
    }

    private fun captureAll(mode: String) {
        waitFor(s(R.string.timer_end))
        shoot("1-timer", mode)
        // Tapping the ring flips it to time left, then to when the fast can be broken.
        val ring = compose.onNode(hasContentDescription(s(R.string.a11y_mode_elapsed), substring = true))
        ring.performClick()
        waitForDescription(compose.activity.getString(R.string.a11y_mode_remaining, "").trimEnd(' ', ':'))
        shoot("1b-timer-left", mode)
        compose.onNode(hasClickAction() and hasContentDescription("Fasted ", substring = true)).performClick()
        waitForDescription(compose.activity.getString(R.string.a11y_mode_break_at, "").trimEnd(' ', ':'))
        shoot("1c-timer-break-at", mode)
        compose.onNode(hasClickAction() and hasContentDescription("Fasted ", substring = true)).performClick()
        waitForDescription(s(R.string.a11y_mode_elapsed))
        compose.onNodeWithText(s(R.string.tab_history)).performClick()
        val averagePrefix = compose.activity.getString(R.string.history_average, "", 0).substringBefore(" ")
        compose.waitUntil(15_000) { compose.onAllNodesWithText(averagePrefix, substring = true).fetchSemanticsNodes().isNotEmpty() }
        shoot("2-history", mode)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(compose.activity.getString(R.string.history_streak_title)))
        shoot("2b-calendar", mode)
        compose.onNodeWithText(s(R.string.tab_log)).performClick()
        // Wait for the seeded data itself (today's water and the weight list), so the capture
        // can't race the database. Logs are seeded minutes ago so they're always "today".
        waitFor(compose.activity.getString(R.string.log_water_today, 1250))
        compose.waitUntil(15_000) { compose.onAllNodesWithText("82.1 kg", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shoot("3-log", mode)
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        waitFor(s(R.string.settings_plan))
        shoot("4-settings", mode)
    }

    @Config(qualifiers = "w411dp-h822dp-notnight-xxhdpi")
    @Test fun light() = captureAll("light")

    @Config(qualifiers = "w411dp-h822dp-night-xxhdpi")
    @Test fun dark() = captureAll("dark")
}
