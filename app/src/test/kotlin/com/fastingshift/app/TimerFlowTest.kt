package com.fastingshift.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.timer.TimerDisplayMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Timer screen behaviour on the real app: the tappable ring, undo after ending, education-only mode. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class TimerFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository

    @Before fun setUp() = hilt.inject()

    /** DataStore is a process-wide singleton under Robolectric: leave it as the next test expects. */
    @After fun tearDown() {
        runBlocking {
            settings.setOnboardingComplete(false)
            settings.setTimerMode(TimerDisplayMode.ELAPSED)
            settings.setPendingRefeeding(null)
        }
    }

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun waitFor(text: String) = try {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        println("DBG>> " + compose.onRoot().printToString().lines().joinToString("\nDBG>> "))
        throw e
    }
    private fun waitUntil(condition: () -> Boolean) = compose.waitUntil(15_000) { condition() }

    private fun seed(gate: GateResult, activeHoursIn: Long?) = runBlocking {
        val now = Instant.now()
        val age = if (gate == GateResult.BLOCK) 16 else 40
        screening.save(ScreeningRecord(now, ScreeningAnswers(age, false, false, false, emptySet(), 175.0, 70.0), gate, false))
        settings.setOnboardingComplete(true)
        activeHoursIn?.let {
            val start = now.minus(Duration.ofHours(it))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
        }
    }

    private val ring get() = compose.onNode(hasContentDescription("Fasted ", substring = true))

    private fun waitForRing(descriptionPart: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasContentDescription(descriptionPart, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun tappingTheTimerCyclesWhatItShows() {
        seed(GateResult.OK, activeHoursIn = 2)
        // The visible label sits inside the ring's single TalkBack node, so check its description.
        waitForRing(s(R.string.a11y_mode_elapsed))
        ring.performClick()
        waitForRing(s(R.string.a11y_mode_remaining, "").trimEnd(' ', ':'))
        ring.performClick()
        waitForRing(s(R.string.a11y_mode_break_at, "").trimEnd(' ', ':'))
        ring.performClick()
        waitForRing(s(R.string.a11y_mode_elapsed))
        assertEquals(TimerDisplayMode.ELAPSED, runBlocking { settings.current().timerMode })
    }

    @Test fun educationOnlyShowsNoStartButton() {
        seed(GateResult.BLOCK, activeHoursIn = null)
        waitFor(s(R.string.timer_education_only))
        assertEquals(0, compose.onAllNodesWithText(s(R.string.timer_start)).fetchSemanticsNodes().size)
        assertNull(runBlocking { fasts.getActive() })
    }
}
