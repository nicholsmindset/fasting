package com.fastingshift.app

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.di.ClockModule
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.ChallengeRepository
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
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
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun moatsOutput(language: String): File {
    val storeShots = File(requireNotNull(System.getProperty("fastingshift.screenshotDir")))
    val directory = storeShots.parentFile.parentFile.resolve("app/build/moats-captures/$language")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
    return directory
}

internal val moatsZone: ZoneId = ZoneId.of("Asia/Singapore")
internal val moatsNow: Instant = Instant.parse("2026-11-23T06:00:00Z")

/** Real navigation and Room-backed screens, with a fixed clock and a synthetic local history. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(ClockModule::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class)
class MoatsCaptureTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @BindValue @JvmField val clock: Clock = Clock.fixed(moatsNow, moatsZone)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var metrics: MetricRepository
    @Inject lateinit var challenges: ChallengeRepository

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val today = moatsNow.atZone(moatsZone).toLocalDate()
    private val oldZone = TimeZone.getDefault()
    private lateinit var output: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        assumeTrue("captures require -Pscreenshots", System.getProperty("fastingshift.screenshotDir") != null)
        hilt.inject()
        TimeZone.setDefault(TimeZone.getTimeZone(moatsZone.id))
        output = moatsOutput(app.resources.configuration.locales[0].language)
        runBlocking {
            settings.setOnboardingComplete(true)
            settings.setRamadanCity("sg_singapore", PrayerMethod.MUIS)
            screening.save(ScreeningRecord(moatsNow, ScreeningAnswers(42, false, false, false, emptySet(), 178.0, 82.0), GateResult.OK, false))
            for (back in 12 downTo 1) {
                val start = today.minusDays(back.toLong()).atTime(20, 0).atZone(moatsZone).toInstant()
                val kind = when (back) {
                    3 -> FastKind.SUNNAH
                    7 -> FastKind.QADA
                    else -> FastKind.REGULAR
                }
                val target = start.plus(Duration.ofHours(16))
                val fast = fasts.insert(Fast(startAt = start, targetEndAt = target, protocolId = "window_16_8", kind = kind))
                fasts.update(fast.end(target.plusSeconds(600)))
            }
            challenges.join(Challenge(type = ChallengeType.DAILY_PROTOCOL, name = "Our first week together", protocolId = "window_16_8", startDate = today.minusDays(5), lengthDays = 7, joinedAt = moatsNow))
            metrics.add(MetricLog(at = moatsNow.minus(Duration.ofHours(3)), type = MetricType.GLUCOSE, value = 5.2, unit = MetricUnit.MMOL_L))
            metrics.add(MetricLog(at = moatsNow.minus(Duration.ofHours(1)), type = MetricType.KETONE, value = 0.8, unit = MetricUnit.MMOL_L))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() {
        scenario?.close()
        try {
            if (::settings.isInitialized) runBlocking { settings.clearAll() }
        } finally {
            TimeZone.setDefault(oldZone)
        }
    }

    private fun s(id: Int) = app.getString(id)

    private fun waitFor(text: String) =
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun click(text: String) {
        waitFor(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun shoot(name: String): File {
        compose.waitForIdle()
        val file = output.resolve("$name.png")
        // A stale successful run must never mask a skipped capture.
        if (file.exists()) check(file.delete())
        captureScreenRoboImage(file.absolutePath)
        check(file.length() > 0) { "Missing screenshot: $file" }
        return file
    }

    @Config(qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
    @Test fun dailyLayoutLight() = dailyLayout("light")

    @Config(qualifiers = "en-rSG-w411dp-h822dp-night-xxhdpi")
    @Test fun dailyLayoutDark() = dailyLayout("dark")

    /** Render the same real destinations with exactly the same records, clock and viewport. */
    private fun dailyLayout(mode: String) {
        runBlocking {
            // Leave enough time after the previous fast for both the idle and active fixtures.
            fasts.delete(fasts.getAll().maxBy { it.startAt }.id)
            settings.recordSchedule(today.minusDays(14), PlanHistory.EVERY_DAY)
        }
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(s(R.string.timer_start))
        shoot("layout-ready-$mode")

        runBlocking {
            val start = moatsNow.minus(Duration.ofHours(13)).minus(Duration.ofMinutes(40))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
        }
        waitFor(s(R.string.timer_end))
        shoot("layout-active-$mode")

        compose.onNodeWithTag("tab_history").performClick()
        click(s(R.string.history_period_month))
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription(s(R.string.history_fasted_month), substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        shoot("layout-history-$mode")

        compose.onNodeWithTag("tab_log").performClick()
        waitFor(s(R.string.log_water))
        shoot("layout-log-$mode")
        compose.onNodeWithTag("tab_settings").performClick()
        waitFor(s(R.string.appearance_title))
        shoot("layout-settings-$mode")
    }

    @Config(qualifiers = "en-rSG-w320dp-h720dp-notnight-xxhdpi")
    @Test fun changeTimerViewAtDoubleFontSize() {
        val originalScale = RuntimeEnvironment.getFontScale()
        scenario?.close()
        try {
            RuntimeEnvironment.setFontScale(2f)
            runBlocking {
                val start = moatsNow.minus(Duration.ofHours(1))
                fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            requireNotNull(scenario).onActivity { activity ->
                val configuration = activity.resources.configuration
                check(configuration.fontScale == 2f && configuration.screenWidthDp == 320)
            }
            val changeView = compose.onNode(hasText(s(R.string.timer_choose_view)) and hasClickAction())
            waitFor(s(R.string.timer_choose_view))
            changeView.performScrollTo().assertIsDisplayed().performClick()
            val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            val elapsed = radio and hasText(s(R.string.timer_view_elapsed))
            val remaining = radio and hasText(s(R.string.timer_view_remaining))
            compose.onNode(elapsed and isSelected()).assertIsDisplayed()
            compose.onNode(remaining).performClick()
            val remainingLabel = app.getString(R.string.a11y_mode_remaining, "").trimEnd(' ', ':')
            val timer = hasClickAction() and hasContentDescription(remainingLabel, substring = true)
            compose.waitUntil(20_000) { compose.onAllNodes(timer).fetchSemanticsNodes().size == 1 }
            compose.onNode(timer).performScrollTo().assertIsDisplayed()
            shoot("timer-large-text")
            changeView.performScrollTo().performClick()
            compose.onNode(remaining and isSelected()).assertIsDisplayed()
        } finally {
            scenario?.close()
            scenario = null
            RuntimeEnvironment.setFontScale(originalScale)
        }
    }

    @Config(qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
    @Test fun chooseDarkOnALightDevice() = verifyAppearance(R.string.appearance_dark, "dark", expectLight = false)

    @Config(qualifiers = "en-rSG-w411dp-h822dp-night-xxhdpi")
    @Test fun chooseLightOnADarkDevice() = verifyAppearance(R.string.appearance_light, "light", expectLight = true)

    private fun verifyAppearance(choice: Int, name: String, expectLight: Boolean) {
        fun waitForOne(target: SemanticsMatcher) = compose.waitUntil(20_000) {
            compose.onAllNodes(target).fetchSemanticsNodes().size == 1
        }
        click(s(R.string.tab_settings))
        val appearance = hasText(s(R.string.appearance_title)) and hasClickAction()
        waitForOne(appearance)
        compose.onNode(appearance).performScrollTo().performClick()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        waitForOne(radio and hasText(s(R.string.appearance_system)) and isSelected())
        val option = radio and hasText(s(choice))
        compose.onNode(option).performClick()
        val updatedRow = appearance and hasText(s(choice))
        waitForOne(updatedRow)
        compose.onNode(updatedRow).performClick()
        waitForOne(option and isSelected())
        shoot("appearance-$name")

        // Open the actual first-run Welcome screen with the saved override still in place.
        scenario?.close()
        runBlocking { settings.setOnboardingComplete(false) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(s(R.string.onb_get_started))
        val welcome = shoot("welcome-$name")
        val options = BitmapFactory.Options().apply { inSampleSize = 16 }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(welcome.absolutePath, options))
        try {
            // A downsample of the whole welcome screen avoids depending on one text or logo pixel.
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val lightPixels = pixels.count { Color.luminance(it) > 0.5f }
            check((lightPixels > pixels.size / 2) == expectLight) { "Welcome did not render in $name mode" }
        } finally {
            bitmap.recycle()
        }
    }

    private fun back() { requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun scroll(id: Int) {
        // The Qada ledger and other sections load independently of the screen's heading.
        val target = hasText(s(id)) and hasAnyAncestor(hasScrollAction())
        compose.waitUntil(20_000) { compose.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        compose.onNode(target).performScrollTo().assertIsDisplayed()
    }

    private fun openSettingsSection(name: String) {
        val section = hasTestTag("settings_open_$name")
        compose.waitUntil(20_000) { compose.onAllNodes(section).fetchSemanticsNodes().size == 1 }
        compose.onNode(section).performScrollTo().performClick()
    }

    private fun hub() {
        click(s(R.string.tab_settings))
        openSettingsSection("fasting")
        scroll(R.string.muslim_hub_entry)
        shoot("settings-sunnah")
        click(s(R.string.muslim_hub_entry))
        waitFor(s(R.string.muslim_upcoming))
        shoot("hub-1")
    }

    @Config(qualifiers = "ms-rMY-w411dp-h822dp-notnight-xxhdpi")
    @Test fun malayHub() = hub()

    @Config(qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
    @Test fun englishScreens() {
        hub()
        scroll(R.string.qada_manage)
        shoot("hub-2")
        back()
        waitFor(s(R.string.muslim_hub_entry))
        back()
        openSettingsSection("safety")
        scroll(R.string.report_row)
        shoot("settings-report")
        back()
        openSettingsSection("privacy")
        scroll(R.string.trust_row)
        click(s(R.string.trust_row))
        waitFor(s(R.string.trust_title))
        shoot("trust-1")
        scroll(R.string.trust_delete)
        shoot("trust-2")
        back()
        waitFor(s(R.string.trust_row))
        back()

        click(s(R.string.tab_history))
        waitFor(s(R.string.history_stat_fasts))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(s(R.string.recap_fit_question)))
        scroll(R.string.recap_fit_skip)
        shoot("weekly-schedule-fit")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(s(R.string.history_challenges)))
        shoot("history-challenges-row")
        click(s(R.string.history_challenges))
        waitFor("Our first week together")
        shoot("challenges")
        click("Our first week together")
        waitFor(s(R.string.challenge_invite))
        compose.onNodeWithTag("challenge_grid").assertExists()
        shoot("challenge-detail")
        scroll(R.string.friend_own_plan)
        click(s(R.string.friend_own_plan))
        waitFor(s(R.string.friend_personal_count))
        scroll(R.string.week_title)
        shoot("friend-personal-plan")
        back()
        waitFor("Our first week together")
        back()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(s(R.string.fast_kind_qada)))
        shoot("history-kinds")

        click(s(R.string.tab_log))
        waitFor(s(R.string.metrics_title))
        scroll(R.string.metrics_ranges_note)
        scroll(R.string.metrics_title)
        compose.onNodeWithTag("metric_input").assertExists()
        shoot("log-metrics")

        // Launch a real join intent, independent of the seeded, already joined challenge.
        requireNotNull(scenario).close()
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", today.plusDays(1), 7, "A week with friends")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ChallengeLinks.link(invite))).setClass(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)
        waitFor("A week with friends")
        compose.onNodeWithTag("join_card").assertExists()
        shoot("challenge-join")
    }
}
