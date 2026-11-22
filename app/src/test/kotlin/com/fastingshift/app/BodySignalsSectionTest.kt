package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.plus.BodySignalsSection
import com.fastingshift.app.ui.plus.BodySignalsState
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.insights.BodySignalsReport
import com.fastingshift.domain.insights.GlucoseComparison
import com.fastingshift.domain.insights.SleepComparison
import com.fastingshift.domain.model.MetricUnit
import java.time.Duration
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Shift+ body signals: an empty state that asks for permissions, and comparisons once granted. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class BodySignalsSectionTest {
    @get:Rule val compose = createComposeRule()
    private fun s(id: Int) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id)

    @Test fun notGrantedShowsTheEmptyStateWithAGrantButton() {
        var asked = 0
        compose.setContent {
            FastingShiftTheme(darkTheme = false) { BodySignalsSection(BodySignalsState(healthAvailable = true, granted = false), onGrant = { asked++ }) }
        }
        compose.onNodeWithTag("body_signals_empty").assertExists()
        compose.onNodeWithText(s(R.string.body_connect_button)).performClick()
        assertEquals(1, asked)
        // Without permission only glucose (logged in the app) is listed.
        assertEquals(0, compose.onAllNodesWithText(s(R.string.body_sleep)).fetchSemanticsNodes().size)
    }

    @Test fun grantedShowsComparisonsAndNoEmptyState() {
        val report = BodySignalsReport(
            sleep = SleepComparison(6, 9, Duration.ofMinutes(450), Duration.ofHours(7)),
            glucose = GlucoseComparison(5, 7, 4.5, 5.5),
        )
        compose.setContent {
            FastingShiftTheme(darkTheme = false) {
                BodySignalsSection(BodySignalsState(healthAvailable = true, granted = true, report = report, glucoseUnit = MetricUnit.MMOL_L), onGrant = {})
            }
        }
        assertEquals(0, compose.onAllNodesWithTag("body_signals_empty").fetchSemanticsNodes().size)
        compose.onNodeWithText(s(R.string.body_sleep)).assertExists()
        compose.onNodeWithText("4.5 vs 5.5 mmol/L").assertExists()
        compose.onNodeWithText(s(R.string.body_correlation_note)).assertExists()
    }
}
