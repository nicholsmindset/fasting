package com.fastingshift.app

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.components.WeekFormat
import com.fastingshift.app.ui.plus.InsightsScreen
import com.fastingshift.app.ui.plus.InsightsState
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.history.ScheduleFit
import com.fastingshift.domain.insights.PlanCoverage
import com.fastingshift.domain.insights.ReviewWeek
import com.fastingshift.domain.insights.WeeklyConsistency
import com.fastingshift.domain.model.WeightUnit
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.fastingshift.domain.schedule.DayOverride
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.LocalTime
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleReviewMoatsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.waitForIdle()
        val file = moatsOutput("en").resolve("$name.png")
        if (file.exists()) check(file.delete())
        captureScreenRoboImage(file.absolutePath)
        check(file.length() > 0)
    }

    @Test fun freeExampleIsLabeledAndBothNavigationActionsAreAvailable() {
        var plans = 0
        var offers = 0
        compose.setContent {
            FastingShiftTheme(darkTheme = false) {
                InsightsScreen(InsightsState.Example, {}, { offers++ }, { plans++ }) { Text("Personal signals fixture") }
            }
        }
        compose.onNodeWithTag("schedule-review-example").assertExists()
        compose.onNodeWithTag("schedule-review-personal").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.review_example_label)).assertIsDisplayed()
        compose.onNodeWithText("Personal signals fixture").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.plan_repeat_count, "Tuesday", 2)).assertExists()
        capture("schedule-review-example")
        compose.onNodeWithText(app.getString(R.string.plan_repeat_count, "Tuesday", 2)).performScrollTo().performClick()
        val format = com.fastingshift.app.ui.components.WeekFormat(app.resources.configuration.locales[0],
            android.text.format.DateFormat.is24HourFormat(app))
        listOf(LocalTime.of(21, 0), LocalTime.of(21, 30)).forEach { time ->
            compose.onNodeWithText(app.getString(R.string.plan_moved_to, format.time.format(time))).assertExists()
        }
        capture("schedule-review-dated-changes")
        compose.onNodeWithText(app.getString(R.string.settings_done)).performClick()
        compose.onNodeWithText(app.getString(R.string.review_open_week)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.plan_see_plus)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, plans); assertEquals(1, offers) }
    }

    @Test
    @Config(qualifiers = "ms-rMY-w320dp-h640dp-night-xxhdpi")
    fun compactMalayReviewPairsEachDateWithItsChangeAtDoubleFontSize() {
        val config = app.resources.configuration
        val locale = config.locales[0]
        assertEquals("ms", locale.language)
        assertEquals(320, config.screenWidthDp)
        val monday = LocalDate.of(2026, 11, 16)
        val older = monday.minusWeeks(1)
        val moved = LocalTime.of(21, 30)
        val weeks = listOf(
            ReviewWeek(WeeklyConsistency(monday, 2, 3), PlanCoverage.PLANNED, changes = mapOf(monday to DayOverride(moved))),
            ReviewWeek(WeeklyConsistency(older, 0, 0), PlanCoverage.NO_PLANNED_DAYS, changes = mapOf(older to DayOverride())),
        )
        val state = InsightsState.Personal(weeks, weeks.map { it.consistency }.reversed(), emptyMap(), null, WeightUnit.KG)
        val nextMonday = monday.plusWeeks(1)
        var openedDate: LocalDate? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                FastingShiftTheme(darkTheme = true) {
                    InsightsScreen(state, {}, {}, { openedDate = it }, mapOf(DayOfWeek.MONDAY to nextMonday)) {}
                }
            }
        }
        val weekday = DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, locale)
        compose.onNodeWithText(app.getString(R.string.plan_repeat_count, weekday, 2)).performScrollTo().assertIsDisplayed().performClick()
        val dates = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale)
        val time = WeekFormat(locale, DateFormat.is24HourFormat(app)).time.format(moved)
        compose.onNode(hasText(monday.format(dates)) and hasText(app.getString(R.string.plan_moved_to, time)))
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(older.format(dates)) and hasText(app.getString(R.string.week_skipped)))
            .performScrollTo().assertIsDisplayed()
        val nextDateLabel = WeekFormat(locale, DateFormat.is24HourFormat(app)).date.format(nextMonday)
        compose.onNodeWithText(app.getString(R.string.review_open_date, nextDateLabel)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(nextMonday, openedDate) }
    }

    @Test fun personalReviewIncludesOnlyRecordedReflectionsAndHonorsHiddenWeight() {
        val week = LocalDate.of(2026, 11, 16)
        val weeks = listOf(
            ReviewWeek(WeeklyConsistency(week, 3, 4), PlanCoverage.PLANNED, changes = mapOf(week to DayOverride(LocalTime.NOON)), reflection = ScheduleFit.PARTLY),
            ReviewWeek(WeeklyConsistency(week.minusWeeks(1), 0, 0), PlanCoverage.WEEK_OFF),
            ReviewWeek(WeeklyConsistency(week.minusWeeks(2), 0, 2), PlanCoverage.PLANNED, changes = mapOf(week.minusWeeks(2) to DayOverride())),
            ReviewWeek(WeeklyConsistency(week.minusWeeks(3), 0, 0), PlanCoverage.BEFORE_TRACKING),
        )
        val state = InsightsState.Personal(weeks, weeks.map { it.consistency }.reversed(), emptyMap(), null, WeightUnit.KG)
        compose.setContent {
            FastingShiftTheme(darkTheme = true) {
                InsightsScreen(state, {}, {}, {}) { Text("Personal signals fixture") }
            }
        }
        compose.onNodeWithTag("schedule-review-personal").assertExists()
        compose.onNodeWithTag("schedule-review-example").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.review_fit, app.getString(R.string.recap_fit_partly))).assertExists()
        compose.onNodeWithText(app.getString(R.string.review_untracked)).assertExists()
        compose.onNodeWithText(app.getString(R.string.insights_weight)).assertDoesNotExist()
        compose.onNodeWithText("Personal signals fixture").assertExists()
        capture("schedule-review-personal")
    }
}
