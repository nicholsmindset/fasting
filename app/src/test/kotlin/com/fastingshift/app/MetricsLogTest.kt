package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.log.LogActions
import com.fastingshift.app.ui.log.LogScreen
import com.fastingshift.app.ui.log.LogState
import com.fastingshift.app.ui.log.MetricsRoute
import com.fastingshift.app.ui.log.MetricsViewModel
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Logging a glucose reading on the Log screen with the real repository (in-memory Room).
 * Health Connect is not installed under Robolectric, so the sync path is a guarded no-op.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class MetricsLogTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Inject lateinit var metrics: MetricRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var health: HealthConnectManager
    @Inject lateinit var clock: Clock

    private val savedLocale = Locale.getDefault()

    @Before fun setUp() {
        hilt.inject()
        Locale.setDefault(Locale.US) // US → mg/dL by default
    }

    @After fun tearDown() = Locale.setDefault(savedLocale)

    @Test fun addingAGlucoseReadingListsIt() {
        val vm = MetricsViewModel(metrics, clock)
        compose.setContent {
            FastingShiftTheme(darkTheme = false) { LogScreen(LogState(), LogActions(), metrics = { MetricsRoute(vm) }) }
        }
        compose.onNodeWithTag("metric_input").performScrollTo().performTextInput("95")
        compose.onNodeWithTag("metric_add").performScrollTo().performClick()

        compose.waitUntil(10_000) { compose.onAllNodesWithText("95 mg/dL").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, compose.onAllNodesWithTag("metric_row").fetchSemanticsNodes().size)
        val saved = runBlocking { metrics.since(java.time.Instant.EPOCH) }.single()
        assertEquals(MetricType.GLUCOSE, saved.type)
        assertEquals(MetricUnit.MG_DL, saved.unit)
        assertEquals(95.0, saved.value)
    }

    @Test fun outOfRangeReadingIsNotSaved() {
        val vm = MetricsViewModel(metrics, clock)
        compose.setContent {
            FastingShiftTheme(darkTheme = false) { LogScreen(LogState(), LogActions(), metrics = { MetricsRoute(vm) }) }
        }
        compose.onNodeWithTag("metric_input").performScrollTo().performTextInput("900")
        compose.onNodeWithTag("metric_add").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("metric_row").fetchSemanticsNodes().size)
        assertEquals(0, runBlocking { metrics.since(java.time.Instant.EPOCH) }.size)
    }
}
