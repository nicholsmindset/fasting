package com.fastingshift.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
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
            if (::settings.isInitialized) runBlocking { settings.setOnboardingComplete(false) }
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

    private fun shoot(name: String) {
        compose.waitForIdle()
        val file = output.resolve("$name.png")
        // A stale successful run must never mask a skipped capture.
        if (file.exists()) check(file.delete())
        captureScreenRoboImage(file.absolutePath)
        check(file.length() > 0) { "Missing screenshot: $file" }
    }

    private fun back() { requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun scroll(id: Int) {
        // The Qada ledger and other sections load independently of the screen's heading.
        val target = hasText(s(id)) and hasAnyAncestor(hasScrollAction())
        compose.waitUntil(20_000) { compose.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        compose.onNode(target).performScrollTo().assertIsDisplayed()
    }

    private fun hub() {
        click(s(R.string.tab_settings))
        waitFor(s(R.string.settings_plan))
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
        waitFor(s(R.string.settings_plan))
        scroll(R.string.report_row)
        shoot("settings-report")
        scroll(R.string.trust_row)
        click(s(R.string.trust_row))
        waitFor(s(R.string.trust_title))
        shoot("trust-1")
        scroll(R.string.trust_delete)
        shoot("trust-2")
        back()

        click(s(R.string.tab_history))
        waitFor(s(R.string.history_stat_fasts))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(s(R.string.history_challenges)))
        shoot("history-challenges-row")
        click(s(R.string.history_challenges))
        waitFor("Our first week together")
        shoot("challenges")
        click("Our first week together")
        waitFor(s(R.string.challenge_invite))
        compose.onNodeWithTag("challenge_grid").assertExists()
        shoot("challenge-detail")
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
