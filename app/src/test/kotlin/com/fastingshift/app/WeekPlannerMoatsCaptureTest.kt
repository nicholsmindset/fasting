package com.fastingshift.app

import android.app.Application
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.text.format.DateFormat
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.di.ClockModule
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.app.ui.settings.WeekFormat
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.protocol.Presets
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
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@HiltAndroidTest
@UninstallModules(ClockModule::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = HiltTestApplication::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
class WeekPlannerMoatsCaptureTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @BindValue @JvmField val clock: Clock = Clock.fixed(moatsNow, moatsZone)
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var schedules: ScheduleRepository

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val originalZone = TimeZone.getDefault()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
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

    private fun tap(target: SemanticsMatcher, scroll: Boolean = false) {
        compose.waitUntil(15_000) { compose.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        val node = compose.onNode(target)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.waitForIdle()
        val shot = moatsOutput("en").resolve("$name.png")
        if (shot.exists()) check(shot.delete())
        captureScreenRoboImage(shot.absolutePath)
        check(shot.length() > 0)
    }

    @Test fun weekdayRoutineCanMoveOneDayWhileAFastIsRunning() {
        val today = LocalDate.now(clock)
        val tomorrow = today.plusDays(1)
        val moved = LocalTime.of(21, 30)
        val running = runBlocking {
            val start = clock.instant().minus(Duration.ofHours(2))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = Presets.W16_8.id))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        tap(label(R.string.tab_settings))
        tap(label(R.string.settings_schedules_title), scroll = true)
        tap(label(R.string.week_create, Presets.W16_8.name), scroll = true)
        compose.waitUntil(15_000) { compose.onAllNodes(label(R.string.week_weekdays)).fetchSemanticsNodes().isNotEmpty() }
        tap(label(R.string.week_save), scroll = true)
        compose.waitUntil(15_000) { runBlocking { schedules.getAll().size == 1 } }
        val recurring = runBlocking { schedules.getAll().single() }
        assertEquals(Presets.W16_8.id, recurring.protocolId)
        assertEquals(DayOfWeek.entries.take(5).toSet(), recurring.days)
        assertEquals(LocalTime.of(20, 0), recurring.startTime)

        val row = hasTestTag("week-day-$tomorrow")
        tap(row, scroll = true)
        tap(label(R.string.week_change_time))
        requireNotNull(scenario).onActivity {
            val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(moved.hour, moved.minute)
            picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.waitUntil(15_000) {
            runBlocking { settings.current().planHistory.dayOverrides[tomorrow]?.startTime == moved }
        }
        // Reopen through the public route so the new time must be read from persistence.
        requireNotNull(scenario).close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        tap(label(R.string.tab_settings))
        tap(label(R.string.settings_schedules_title), scroll = true)
        val format = WeekFormat(app.resources.configuration.locales[0], DateFormat.is24HourFormat(app))
        val restoredRow = row and hasText(format.time.format(moved)) and label(R.string.week_changed)
        compose.waitUntil(15_000) { compose.onAllNodes(restoredRow).fetchSemanticsNodes().size == 1 }
        capture("my-week")

        tap(row, scroll = true)
        compose.waitUntil(15_000) { compose.onAllNodes(label(R.string.week_restore_day)).fetchSemanticsNodes().size == 1 }
        capture("my-week-one-day")
        tap(label(R.string.week_restore_day))
        compose.waitUntil(15_000) { runBlocking { tomorrow !in settings.current().planHistory.dayOverrides } }
        assertEquals(recurring, runBlocking { schedules.getAll().single() })
        assertEquals(running, runBlocking { fasts.getActive() })
    }
}
