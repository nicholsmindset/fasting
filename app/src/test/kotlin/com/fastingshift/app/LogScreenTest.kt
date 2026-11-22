package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.log.LogActions
import com.fastingshift.app.ui.log.LogScreen
import com.fastingshift.app.ui.log.LogState
import com.fastingshift.app.ui.log.weightTrendPoints
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Log screen's weight trend: shown normally, gone in hide-weight mode, 90 days only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class LogScreenTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-21T10:00:00Z")
    private val weights = listOf(83.0, 82.4, 82.1).mapIndexed { i, kg ->
        WeightLog(id = i + 1L, at = now.minus(Duration.ofDays(10L - i * 3)), value = kg, unit = WeightUnit.KG)
    }

    private fun show(hideWeight: Boolean) = compose.setContent {
        FastingShiftTheme(darkTheme = false) {
            LogScreen(LogState(hideWeight = hideWeight, weights = weights, trend = weightTrendPoints(weights, now), now = now), LogActions())
        }
    }

    @Test fun weightChartIsShown() {
        show(hideWeight = false)
        compose.onNodeWithTag("weight_chart").assertExists()
    }

    @Test fun weightChartIsHiddenInHideWeightMode() {
        show(hideWeight = true)
        assertEquals(0, compose.onAllNodesWithTag("weight_chart").fetchSemanticsNodes().size)
    }

    @Test fun trendKeepsTheLast90DaysOldestFirst() {
        val old = WeightLog(id = 9, at = now.minus(Duration.ofDays(120)), value = 90.0, unit = WeightUnit.KG)
        val points = weightTrendPoints(weights.reversed() + old, now)
        assertEquals(listOf(83.0, 82.4, 82.1), points.map { it.value })
    }
}
