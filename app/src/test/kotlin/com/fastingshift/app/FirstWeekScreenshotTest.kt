package com.fastingshift.app

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import java.io.File
import java.time.Duration
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
 * Design review captures of the onboarding and first-week screens (not store screenshots).
 * Runs only with -Pscreenshots; writes to /tmp/fastingshift-firstweek.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class, qualifiers = "w411dp-h822dp-notnight-xxhdpi")
class FirstWeekScreenshotTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var fasts: FastRepository

    private val outDir = File("/tmp/fastingshift-firstweek")

    @Before fun setUp() {
        assumeTrue("captures run only with -Pscreenshots", System.getProperty("fastingshift.screenshotDir") != null)
        hilt.inject()
        outDir.mkdirs()
    }

    @After fun tearDown() {
        runBlocking { settings.setOnboardingComplete(false) }
    }

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitFor(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }
    private fun field(label: String, value: String) {
        val node = compose.onNode(hasSetTextAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
        node.performScrollTo()
        node.performTextInput(value)
    }
    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(outDir, "$name.png").path)
    }

    @Test fun onboarding() {
        waitFor(s(R.string.onb_get_started))
        shoot("01-welcome")
        click(s(R.string.onb_get_started))
        waitFor(s(R.string.onb_intro_timer_title))
        shoot("02-intro-1")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_intro_safety_title))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_intro_privacy_title))
        shoot("03-intro-3")
        click(s(R.string.onb_continue))
        waitFor(s(R.string.onb_goal_title))
        click(s(R.string.goal_long))
        shoot("04-goal")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_screen_title))
        shoot("05-screening")
        field(s(R.string.onb_age), "40")
        field(s(R.string.onb_height), "175")
        field(s(R.string.onb_weight), "70")
        click(s(R.string.med_bp))
        shoot("06-screening-filled")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_soft_title))
        shoot("07-result-soft")
        click(s(R.string.onb_soft_ack))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_protocol_title))
        shoot("08-protocol")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_notif_title))
        shoot("09-notifications")
        click(s(R.string.onb_not_now))
        waitFor(s(R.string.onb_offer_title))
        shoot("10-offer")
        click(s(R.string.onb_offer_free))
        waitFor(s(R.string.timer_start))
        waitFor(s(R.string.first_fast_title))
        shoot("11-timer-first-fast")
        click(s(R.string.first_fast_start_now))
        waitFor(s(R.string.timer_end))
        click(s(R.string.timer_end))
        waitFor(s(R.string.summary_mood_title))
        click(s(R.string.mood_4))
        shoot("12-summary")
    }

    @Test fun coachingTip() {
        runBlocking {
            val now = Instant.now()
            screening.save(ScreeningRecord(now, ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
            settings.setOnboardingComplete(true)
            settings.setCoaching(true)
            val start = now.minus(Duration.ofHours(4))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
        }
        waitFor(s(R.string.coach_card_label))
        compose.onNodeWithText(s(R.string.coach_card_label)).performScrollTo()
        shoot("13-coaching")
    }

    private fun seedOk() = runBlocking {
        screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
        settings.setOnboardingComplete(true)
    }

    @Test fun safetyScreens() {
        seedOk()
        runBlocking {
            // A past fast, so the first-fast card stays out of the way.
            val start = Instant.now().minus(Duration.ofDays(3))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8").end(start.plus(Duration.ofHours(16))))
        }
        waitFor(s(R.string.timer_start))
        click("36h")
        click(s(R.string.timer_start))
        waitFor(s(R.string.checklist_title))
        shoot("20-checklist")
        click(s(R.string.checklist_confirm))
        waitFor(s(R.string.timer_check_in))
        click(s(R.string.timer_check_in))
        waitFor(s(R.string.checkin_body))
        click(s(R.string.sym_headache))
        shoot("21-checkin")
        click(s(R.string.sym_fainting))
        click(s(R.string.checkin_submit))
        waitFor(s(R.string.stop_title))
        shoot("22-stop")
    }

    @Test fun refeeding() {
        seedOk()
        runBlocking {
            val start = Instant.now().minus(Duration.ofHours(50))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(48)), protocolId = "duration_48"))
        }
        waitFor(s(R.string.timer_end))
        click(s(R.string.timer_end))
        waitFor(s(R.string.refeed_intro))
        shoot("23-refeeding")
    }
}
