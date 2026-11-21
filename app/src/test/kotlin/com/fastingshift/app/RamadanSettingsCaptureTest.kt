package com.fastingshift.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import java.time.Instant
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
 * Developer captures of the Ramadan card and the redesigned Settings, for visual review only.
 * Run with: gradle :app:testDebugUnitTest -Pscreenshots --tests '*RamadanSettingsCaptureTest'
 * (writes PNGs to the system temp dir, fastingshift-captures/). Skipped in normal test runs.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class)
class RamadanSettingsCaptureTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository

    private val outDir = File(System.getProperty("java.io.tmpdir"), "fastingshift-captures")

    @Before fun setUp() {
        assumeTrue("captures run only with -Pscreenshots", System.getProperty("fastingshift.screenshotDir") != null)
        hilt.inject()
        outDir.mkdirs()
        runBlocking {
            settings.setOnboardingComplete(true)
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(42, false, false, false, emptySet(), 178.0, 82.0), GateResult.OK, false))
            settings.setRamadan(true)
            settings.setRamadanCity("sg_singapore", PrayerMethod.MUIS)
        }
    }

    @After fun tearDown() {
        runBlocking {
            settings.setOnboardingComplete(false)
            settings.setRamadan(false)
        }
    }

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun shoot(name: String, mode: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(outDir, "$name-$mode.png").path)
    }

    private fun scrollTo(text: String) { compose.onNodeWithText(text).performScrollTo() }

    private fun captureAll(mode: String) {
        waitFor("Ramadan mode · Singapore")
        shoot("r1-timer", mode)
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        waitFor(s(R.string.settings_title))
        shoot("r2-settings-top", mode)
        scrollTo(s(R.string.settings_water_goal))
        shoot("r3-settings-reminders", mode)
        scrollTo(s(R.string.ramadan_today_iftar))
        shoot("r4-settings-ramadan", mode)
        scrollTo(s(R.string.settings_app_language))
        shoot("r5-settings-data", mode)
        scrollTo(s(R.string.settings_privacy_policy))
        shoot("r6-settings-safety", mode)
        scrollTo(s(R.string.ramadan_city))
        compose.onNodeWithText(s(R.string.ramadan_city)).performClick()
        waitFor(s(R.string.ramadan_search))
        shoot("r7-city-picker", mode)
        compose.onNodeWithText(s(R.string.settings_done)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(R.string.ramadan_search)).fetchSemanticsNodes().isEmpty() }
        scrollTo(s(R.string.settings_water_goal))
        compose.onNodeWithText(s(R.string.settings_water_goal)).performClick()
        waitFor(s(R.string.settings_water_use_auto))
        shoot("r8-water-sheet", mode)
    }

    @Config(qualifiers = "w411dp-h822dp-notnight-xxhdpi")
    @Test fun light() = captureAll("light")

    @Config(qualifiers = "w411dp-h822dp-night-xxhdpi")
    @Test fun dark() = captureAll("dark")
}
