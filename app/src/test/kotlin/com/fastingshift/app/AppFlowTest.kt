package com.fastingshift.app

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.domain.service.FastRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end run of the real app (real Hilt graph, navigation and screens) under Robolectric:
 * a hard-blocked user gets education-only mode with no protocols; after updating their answers
 * in Settings they can start a fast, and ending it takes one tap.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class AppFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var fasts: FastRepository

    @Before fun setUp() = hilt.inject()

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() } // not every node is in a scrolling container
        node.performClick()
    }
    private fun field(label: String, value: String) {
        val node = compose.onNode(hasSetTextAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
        node.performScrollTo()
        node.performTextClearance()
        node.performTextInput(value)
    }
    private fun waitFor(text: String) = try {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        println("DBG>> " + compose.onRoot().printToString().lines().joinToString("\nDBG>> "))
        throw e
    }

    @Test fun blockedUserThenUpdatedAnswersThenStartAndEndAFast() {
        // Onboarding with a hard-block answer (under 18).
        waitFor(s(R.string.onb_get_started))
        click(s(R.string.onb_get_started))
        // Intro pages: one Next, then Skip the rest.
        waitFor(s(R.string.onb_intro_timer_title))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_intro_safety_title))
        click(s(R.string.onb_skip))
        waitFor(s(R.string.onb_goal_title))
        click(s(R.string.goal_consistency))
        click(s(R.string.onb_next))
        field(s(R.string.onb_age), "16")
        field(s(R.string.onb_height), "175")
        field(s(R.string.onb_weight), "70")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_block_title))
        click(s(R.string.onb_continue))

        // Education-only: no protocols and no start button.
        waitFor(s(R.string.timer_education_only))
        assertEquals(0, compose.onAllNodesWithText(s(R.string.timer_start)).fetchSemanticsNodes().size)

        // Update answers from Settings.
        click(s(R.string.tab_settings))
        click(s(R.string.settings_rerun_screening))
        waitFor(s(R.string.onb_screen_title))
        field(s(R.string.onb_age), "40")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_ok_title))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_protocol_title))
        click(s(R.string.onb_next))

        // Finishing a re-run returns to Settings, which now shows the new result.
        waitFor(s(R.string.screening_ok))
        click(s(R.string.tab_timer))

        // Start the plan suggested for a consistency goal (14:10), then end it with one tap.
        waitFor(s(R.string.timer_start))
        click(s(R.string.timer_start))
        waitFor(s(R.string.timer_end))
        val active = runBlocking { fasts.getActive() }
        assertNotNull(active)
        assertEquals(14, active.plannedDuration.toHours().toInt())
        // TalkBack reads the ring as one node with elapsed, goal and percent.
        compose.onNode(hasContentDescription("Fasted ", substring = true)).assertExists()

        click(s(R.string.timer_end))
        waitFor(s(R.string.timer_start))
        assertNull(runBlocking { fasts.getActive() })
        // Ended early: neutral confirmation, no refeeding guidance for a short fast.
        assertEquals(0, compose.onAllNodesWithText(s(R.string.refeed_title)).fetchSemanticsNodes().size)
    }
}
