package com.fastingshift.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.safety.Symptom
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
import kotlinx.coroutines.flow.first
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

    @Inject lateinit var checkins: SymptomCheckinRepository
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
        compose.onNodeWithText(s(R.string.timer_choose_view)).performClick()
        waitFor(s(R.string.timer_view_end))
        compose.onNodeWithText(s(R.string.timer_view_end)).performClick()
        waitForRing(s(R.string.a11y_mode_break_at, "").trimEnd(' ', ':'))
        assertEquals(TimerDisplayMode.BREAK_AT, runBlocking { settings.current().timerMode })
    }

    @Test fun changingTheSelectedFastUpdatesTheSingleStartAction() {
        runBlocking { settings.setDefaultProtocol("window_16_8") }
        seed(GateResult.OK, activeHoursIn = null)
        waitFor(s(R.string.timer_start))
        compose.onNodeWithText(s(R.string.timer_pick_protocol)).performClick()
        val alternative = hasText("14:10") and hasClickAction()
        waitUntil { compose.onAllNodes(alternative).fetchSemanticsNodes().size == 1 }
        compose.onNode(alternative).performScrollTo().performClick()
        waitUntil { compose.onAllNodesWithText(s(R.string.timer_custom)).fetchSemanticsNodes().isEmpty() }
        assertEquals(1, compose.onAllNodesWithText(s(R.string.timer_start)).fetchSemanticsNodes().size)
        compose.onNodeWithText(s(R.string.timer_start)).performClick()
        waitFor(s(R.string.timer_end))
        assertEquals("window_14_10", runBlocking { fasts.getActive()?.protocolId })
    }

    @Test fun optionalFeelingLogAndEarlyCorrectionUseTheSameSavedFast() {
        seed(GateResult.OK, activeHoursIn = 2)
        val active = assertNotNull(runBlocking { fasts.getActive() })
        waitFor(s(R.string.today_log_feeling))
        compose.onNodeWithText(s(R.string.today_log_feeling)).performScrollTo().performClick()
        waitFor(s(R.string.sym_hunger))
        compose.onNodeWithText(s(R.string.sym_hunger)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.checkin_submit)).performScrollTo().performClick()
        waitFor(s(R.string.timer_end))
        assertEquals(setOf(Symptom.HUNGER), runBlocking { checkins.observeForFast(active.id).first().single().answers })
        compose.onNodeWithText(s(R.string.timer_end)).performScrollTo().performClick()
        waitFor(s(R.string.summary_title_early))
        compose.onNodeWithTag("summary-start").assertExists()
        compose.onNodeWithTag("summary-end").assertExists()
        compose.onNodeWithText(s(R.string.today_back)).assertExists()
        compose.onNodeWithText(s(R.string.mood_4)).performScrollTo().performClick()
        compose.onNodeWithTag("partner_reflection_invite").assertDoesNotExist()
        compose.onNodeWithText(s(R.string.share)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.timer_edit)).performScrollTo().performClick()
        waitFor(s(R.string.edit_title))
        val saved = assertNotNull(runBlocking { fasts.getById(active.id) })
        assertEquals(FastStatus.ENDED_EARLY, saved.status)
        assertEquals(active.startAt, saved.startAt)
        assertEquals(4, saved.mood)
        assertNull(runBlocking { fasts.getActive() })
    }

    @Test fun educationOnlyShowsNoStartButton() {
        seed(GateResult.BLOCK, activeHoursIn = null)
        waitFor(s(R.string.timer_education_only))
        assertEquals(0, compose.onAllNodesWithText(s(R.string.timer_start)).fetchSemanticsNodes().size)
        assertNull(runBlocking { fasts.getActive() })
    }
}
