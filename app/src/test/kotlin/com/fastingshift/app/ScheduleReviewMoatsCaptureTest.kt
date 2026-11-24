package com.fastingshift.app

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.plus.InsightsScreen
import com.fastingshift.app.ui.plus.InsightsState
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.history.ScheduleFit
import com.fastingshift.domain.insights.PlanCoverage
import com.fastingshift.domain.insights.ReviewWeek
import com.fastingshift.domain.insights.WeeklyConsistency
import com.fastingshift.domain.model.WeightUnit
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
        capture("schedule-review-example")
        compose.onNodeWithText(app.getString(R.string.review_open_week)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.plan_see_plus)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, plans); assertEquals(1, offers) }
    }

    @Test fun personalReviewIncludesOnlyRecordedReflectionsAndHonorsHiddenWeight() {
        val week = LocalDate.of(2026, 11, 16)
        val weeks = listOf(
            ReviewWeek(WeeklyConsistency(week, 3, 4), PlanCoverage.PLANNED, movedDates = 1, reflection = ScheduleFit.PARTLY),
            ReviewWeek(WeeklyConsistency(week.minusWeeks(1), 0, 0), PlanCoverage.WEEK_OFF),
            ReviewWeek(WeeklyConsistency(week.minusWeeks(2), 0, 2), PlanCoverage.PLANNED, skippedDates = 1),
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
