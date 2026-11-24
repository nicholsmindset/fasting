package com.fastingshift.app

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.di.ClockModule
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.protocol.Presets
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
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.TimeZone
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(ClockModule::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
class HistoryDayMoatsCaptureTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @BindValue @JvmField val clock: Clock = Clock.fixed(moatsNow, moatsZone)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val originalZone = TimeZone.getDefault()
    private var initialFontScale: Float? = null
    private val recordedDay = LocalDate.of(2026, 11, 20)
    private lateinit var overnight: Fast
    private lateinit var afternoon: Fast
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(moatsZone))
        hilt.inject()
        runBlocking {
            settings.setOnboardingComplete(true)
            settings.recordSchedule(recordedDay.minusWeeks(1), PlanHistory.EVERY_DAY)
            screening.save(ScreeningRecord(moatsNow, ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
            val start = recordedDay.minusDays(1).atTime(18, 0).atZone(moatsZone).toInstant()
            val target = recordedDay.atTime(10, 0).atZone(moatsZone).toInstant()
            overnight = fasts.insert(Fast(startAt = start, targetEndAt = target, protocolId = Presets.W16_8.id).end(target))
            val later = recordedDay.atTime(15, 0).atZone(moatsZone).toInstant()
            afternoon = fasts.insert(Fast(startAt = later, targetEndAt = later.plus(Duration.ofHours(16)), protocolId = Presets.W16_8.id)
                .end(later.plus(Duration.ofMinutes(90))))
        }
    }

    @After fun tearDown() {
        try {
            scenario?.close()
            if (::settings.isInitialized) runBlocking { settings.clearAll() }
        } finally {
            initialFontScale?.let { RuntimeEnvironment.setFontScale(it) }
            TimeZone.setDefault(originalZone)
        }
    }

    private fun label(id: Int, vararg args: Any) = hasText(app.getString(id, *args))

    private fun awaitNode(target: SemanticsMatcher) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        } catch (error: Throwable) {
            val roots = compose.onAllNodes(isRoot())
            for (index in roots.fetchSemanticsNodes().indices) println(roots[index].printToString())
            throw AssertionError("Waiting for ${target.description}", error)
        }
    }

    private fun tap(target: SemanticsMatcher, scroll: Boolean = false) {
        awaitNode(target)
        val node = compose.onNode(target)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    private fun openDay(day: LocalDate) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        tap(label(R.string.tab_history))
        awaitNode(hasTestTag("history-list"))
        compose.onNodeWithTag("history-list").performScrollToKey("history-calendar")
        val dateLabel = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(app.resources.configuration.locales[0]).format(day)
        tap(hasTestTag("history-day-$day") and hasContentDescription(dateLabel) and hasClickAction())
        awaitNode(hasTestTag("history-day-sheet"))
    }

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.waitForIdle()
        val file = moatsOutput("en").resolve("$name.png")
        if (file.exists()) check(file.delete())
        captureScreenRoboImage(file.absolutePath)
        check(file.length() > 0)
    }

    @Test fun correctOneOfTwoFastsOnAnOvernightDate() {
        openDay(recordedDay)
        awaitNode(hasTestTag("history-day-fast-${overnight.id}"))
        val selected = hasTestTag("history-day-fast-${afternoon.id}")
        awaitNode(selected)
        awaitNode(label(R.string.history_day_planned))
        // Only 10h of the 16h overnight fast belongs to Friday.
        awaitNode(label(R.string.history_day_total, app.getString(R.string.duration_hm, 11L, 30L)))
        capture("history-day-filled")
        tap(selected)
        tap(label(R.string.edit_end), scroll = true)
        requireNotNull(scenario).onActivity {
            (ShadowDialog.getLatestDialog() as DatePickerDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        shadowOf(Looper.getMainLooper()).idle()
        requireNotNull(scenario).onActivity {
            val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(16, 45)
            picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        tap(label(R.string.save), scroll = true)
        awaitNode(label(R.string.history_day_total, app.getString(R.string.duration_hm, 11L, 45L)))
        val corrected = runBlocking { fasts.getById(afternoon.id) }
        assertEquals(recordedDay.atTime(16, 45).atZone(moatsZone).toInstant(), corrected?.endedAt)
        assertEquals(overnight, runBlocking { fasts.getById(overnight.id) })
    }

    @Test
    @Config(qualifiers = "en-rSG-w360dp-h640dp-night-xxhdpi")
    fun dayActionsStayReachableWithLargeTextInDarkMode() {
        initialFontScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(2f)
        // Several fasts on this date force the sheet to scroll on a narrow phone.
        runBlocking {
            for (hour in listOf(17, 19, 21)) {
                val start = recordedDay.atTime(hour, 0).atZone(moatsZone).toInstant()
                fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = Presets.W16_8.id)
                    .end(start.plus(Duration.ofMinutes(30))))
            }
        }
        openDay(recordedDay)
        requireNotNull(scenario).onActivity { activity ->
            val config = activity.resources.configuration
            assertEquals(2f, config.fontScale)
            assertEquals(360, config.screenWidthDp)
            assertEquals(Configuration.UI_MODE_NIGHT_YES, config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        }
        val sheet = compose.onNodeWithTag("history-day-sheet")
        compose.waitForIdle()
        val scroll = sheet.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.maxValue() > 0f)
        capture("history-day-large")
        compose.onNodeWithTag("history-day-add").performScrollTo().assertIsDisplayed()
        compose.onNode(label(R.string.settings_done)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("history-day-sheet")).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun addFromEmptyDaySavesTheTappedDate() {
        val emptyDay = recordedDay.minusDays(3)
        openDay(emptyDay)
        awaitNode(label(R.string.history_day_empty))
        capture("history-day-empty")
        tap(hasTestTag("history-day-add"))
        awaitNode(label(R.string.edit_end))
        tap(label(R.string.save), scroll = true)
        awaitNode(hasTestTag("history-day-sheet"))
        val added = runBlocking { fasts.getAll().single { it.id != overnight.id && it.id != afternoon.id } }
        awaitNode(hasTestTag("history-day-fast-${added.id}"))
        assertEquals(emptyDay, requireNotNull(added.endedAt).atZone(moatsZone).toLocalDate())
    }
}
