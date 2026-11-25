package com.fastingshift.app

import android.app.Application
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.text.format.DateFormat
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.di.ClockModule
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.app.ui.components.WeekFormat
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@HiltAndroidTest
@UninstallModules(ClockModule::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
class ThursdayJourneyScreenshotTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @BindValue @JvmField val clock: Clock = Clock.fixed(java.time.Instant.parse("2026-11-26T13:40:00Z"), moatsZone)
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var schedules: ScheduleRepository

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val originalZone = TimeZone.getDefault()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var controlActivations = 0

    @Before fun setUp() {
        assumeTrue(System.getProperty("fastingshift.screenshotDir") != null)
        TimeZone.setDefault(TimeZone.getTimeZone(moatsZone))
        hilt.inject()
        runBlocking {
            settings.setOnboardingComplete(true)
            settings.setDefaultProtocol(Presets.W16_8.id)
            screening.save(ScreeningRecord(clock.instant(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
        }
    }

    @After fun tearDown() {
        try {
            scenario?.close()
            if (::settings.isInitialized) runBlocking { settings.clearAll() }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    private fun label(id: Int, vararg args: Any) = hasText(app.getString(id, *args))

    private fun waitFor(target: SemanticsMatcher) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        } catch (error: Throwable) {
            val roots = compose.onAllNodes(isRoot())
            for (index in roots.fetchSemanticsNodes().indices) println(roots[index].printToString())
            throw AssertionError("Waiting for ${target.description}", error)
        }
    }

    private fun tap(target: SemanticsMatcher, scroll: Boolean = false) {
        waitFor(target)
        val node = compose.onNode(target)
        if (scroll) node.performScrollTo()
        node.performClick()
        controlActivations++
    }

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.waitForIdle()
        val shot = moatsOutput("thursday").resolve("$name.png")
        if (shot.exists()) check(shot.delete())
        captureScreenRoboImage(shot.absolutePath)
        check(shot.length() > 0)
    }


    private fun visibleNode(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    @Test fun thursdayLight() = journey("light", 1f)

    @Test
    @Config(qualifiers = "en-rSG-w411dp-h822dp-night-xxhdpi")
    fun thursdayDark() = journey("dark", 1f)

    @Test
    @Config(qualifiers = "en-rSG-w320dp-h640dp-night-xxhdpi")
    fun compactLargeText() = journey("compact-200pct", 2f)

    private fun journey(mode: String, scale: Float) {
        val previousScale = RuntimeEnvironment.getFontScale()
        try {
            RuntimeEnvironment.setFontScale(scale)
            val day = LocalDate.now(clock)
            val usual = LocalTime.of(20, 0)
            val moved = LocalTime.of(22, 0)
            val format = WeekFormat(app.resources.configuration.locales[0], DateFormat.is24HourFormat(app))
            runBlocking {
                settings.setFastStartReminder(true)
                schedules.save(Schedule(protocolId = Presets.W16_8.id,
                    days = setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), startTime = usual))
                settings.setOnboardingComplete(false)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor(label(R.string.onb_get_started))
            capture("$mode-01-welcome")
            scenario?.close()
            runBlocking { settings.setOnboardingComplete(true) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val nextAction = label(R.string.week_timer_change)
            val direct = hasTestTag("timer-move-missed-start")
            compose.waitUntil(15_000) { visibleNode(nextAction) || visibleNode(direct) }
            val directRoute = visibleNode(direct)
            // Compile the same test against both revisions. An updated build must actually expose
            // its new route and review, even if fallback navigation would also reach Thursday.
            val previewBuild = app.resources.getIdentifier("plan_date_save", "string", app.packageName) != 0
            if (previewBuild) check(directRoute) { "Timer must expose today's missed planned start" }
            val action = if (directRoute) direct else nextAction
            compose.onNode(action).performScrollTo().assertIsDisplayed()
            capture("$mode-02-timer")
            if (directRoute) {
                tap(direct)
            } else {
                tap(hasTestTag("tab_settings"))
                tap(label(R.string.settings_schedules_title), scroll = true)
                tap(hasTestTag("week-day-$day"), scroll = true)
            }
            val adjust = label(R.string.week_change_time)
            waitFor(adjust)
            capture("$mode-03-date-editor")
            tap(adjust, scroll = true)
            requireNotNull(scenario).onActivity {
                val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
                picker.updateTime(moved.hour, moved.minute)
                picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                controlActivations++
            }
            val save = hasTestTag("plan-date-save")
            val changed = hasTestTag("week-day-$day") and label(R.string.week_changed)
            compose.waitUntil(15_000) { visibleNode(save) || visibleNode(changed) }
            val hasPreview = visibleNode(save)
            if (previewBuild) check(hasPreview) { "Choosing a time must leave it uncommitted for review" }
            if (hasPreview) {
                assertEquals(null, runBlocking { settings.current().planHistory.dayOverrides[day] })
                waitFor(hasTestTag("plan-date-preview"))
                val start = day.atTime(moved).atZone(moatsZone)
                waitFor(hasText(format.dateTime.format(start.plusHours(16))))
                val previousReminder = day.plusDays(1).atTime(usual).atZone(moatsZone)
                val changedReminder = hasText(format.dateTime.format(previousReminder), substring = true) and
                    hasText(format.dateTime.format(start), substring = true)
                waitFor(changedReminder)
                capture("$mode-04-picker-result")
                tap(save, scroll = true)
            }
            waitFor(changed)
            assertEquals(moved, runBlocking { settings.current().planHistory.dayOverrides[day]?.startTime })
            val friday = hasTestTag("week-day-${day.plusDays(1)}") and hasText(format.time.format(usual))
            waitFor(friday)
            if (!hasPreview) capture("$mode-04-picker-result")
            capture("$mode-05-saved")
            // Count the confirmed UI activations. Setting the test time is one input action,
            // excluded here because taps differ by keyboard vs clock and Android version.
            moatsOutput("thursday").resolve("$mode-results.txt").writeText(
                "Route: " + (if (directRoute) "Timer > move today" else "Timer > Settings > My week > Thursday") +
                "\nAction counts excluding time entry: $controlActivations\nPreview before save: $hasPreview" +
                "\nThu 21:40, selected 22:00. Friday routine confirmed at 20:00.\n"
            )
            if (hasPreview) {
                tap(hasText("Undo"))
                waitFor(hasTestTag("week-day-$day") and hasText(format.time.format(usual)))
                assertEquals(null, runBlocking { settings.current().planHistory.dayOverrides[day] })
                waitFor(friday)
            }
            requireNotNull(scenario).close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            tap(hasTestTag("tab_history"))
            waitFor(hasTestTag("tab_history"))
            compose.waitForIdle()
            capture("$mode-06-empty-history")
            tap(hasTestTag("tab_settings"))
            waitFor(hasTestTag("settings_overview"))
            capture("$mode-07-settings")
            tap(hasTestTag("settings_open_plan"), scroll = true)
            tap(label(R.string.plan_see_plus), scroll = true)
            compose.waitForIdle()
            capture("$mode-08-shift-plus")
        } finally {
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }
}
