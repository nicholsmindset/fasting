package com.fastingshift.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.log.LogActions
import com.fastingshift.app.ui.log.LogScreen
import com.fastingshift.app.ui.log.LogState
import com.fastingshift.app.ui.log.weightTrendPoints
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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

    @Test fun mineralSelectedInTheTotalsListReceivesTheEnteredAmount() {
        var saved: Pair<Electrolyte, Double>? = null
        compose.setContent {
            FastingShiftTheme(darkTheme = false) {
                LogScreen(
                    LogState(hideWeight = true),
                    LogActions(addElectrolyte = { type, mg -> saved = type to mg }),
                )
            }
        }

        val add = compose.onNodeWithText("Add")
        add.assertIsNotEnabled()
        val potassium = compose.onNodeWithText("Potassium")
        potassium.performScrollTo().performClick()
        potassium.assertIsSelected()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("125")
        add.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Electrolyte.POTASSIUM to 125.0, saved) }
    }

    @Test fun waterHistoryShowsLatestInlineAndOlderEntriesInTheSheet() {
        val entries = listOf(400, 325, 175).mapIndexed { index, amount ->
            WaterLog(id = index + 1L, at = now.minusSeconds(index * 3600L), value = amount.toDouble(), unit = VolumeUnit.ML)
        }
        compose.setContent {
            FastingShiftTheme {
                LogScreen(LogState(hideWeight = true, waterEntries = entries), LogActions())
            }
        }
        compose.onNodeWithText("400 ml").assertExists()
        compose.onNodeWithText("175 ml").assertDoesNotExist()
        compose.onNodeWithText("Water entries (3)").performScrollTo().performClick()
        compose.onNodeWithText("175 ml").performScrollTo().assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("175 ml").assertDoesNotExist()
    }

    @Test fun trendKeepsTheLast90DaysOldestFirst() {
        val old = WeightLog(id = 9, at = now.minus(Duration.ofDays(120)), value = 90.0, unit = WeightUnit.KG)
        val points = weightTrendPoints(weights.reversed() + old, now)
        assertEquals(listOf(83.0, 82.4, 82.1), points.map { it.value })
    }
}
