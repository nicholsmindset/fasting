package com.fastingshift.app

import android.Manifest
import android.app.NotificationManager
import android.provider.Settings
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.program.GuidedPrograms
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
import org.robolectric.Shadows.shadowOf
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
    @Inject lateinit var settings: SettingsRepository

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

    @Test fun routineReminderDisclosureReflectsTogglesAndKeepsItsStateWhileOpen() {
        val original = runBlocking { settings.current() }
        runBlocking { settings.setOnboardingComplete(true) }
        try {
            waitFor(s(R.string.tab_settings))
            click(s(R.string.tab_settings))
            compose.onNodeWithTag("settings_open_reminders").performScrollTo().performClick()
            val disclosure = s(R.string.settings_routine_reminders)
            val water = s(R.string.settings_hydration)
            waitFor(disclosure)
            compose.onNodeWithText(water).assertDoesNotExist()
            click(disclosure)
            click(water)
            val previous = original.reminders.hydrationEnabled
            val count = listOf(!previous, original.coachingEnabled, original.weeklyRecapEnabled).count { it }
            val updated = hasText(disclosure) and hasText(s(R.string.settings_alerts_enabled, count))
            compose.waitUntil(5_000) { compose.onAllNodes(updated).fetchSemanticsNodes().size == 1 }
            click(disclosure)
            compose.onNodeWithText(water).assertDoesNotExist()
            assertEquals(!previous, runBlocking { settings.current().reminders.hydrationEnabled })
        } finally {
            runBlocking {
                settings.setHydrationNudges(original.reminders.hydrationEnabled)
                settings.setOnboardingComplete(original.onboardingComplete)
            }
        }
    }

    @Test fun reminderPreferencesOfferAndroidSettingsWhenDeliveryIsBlocked() {
        val application = compose.activity.application
        val systemNotifications = shadowOf(application.getSystemService(NotificationManager::class.java))
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        systemNotifications.setNotificationsEnabled(false)
        runBlocking { settings.setOnboardingComplete(true) }
        try {
            waitFor(s(R.string.tab_settings))
            click(s(R.string.tab_settings))
            compose.onNodeWithTag("settings_open_reminders").performScrollTo().performClick()
            val enable = s(R.string.rel_notifications_fix)
            waitFor(enable)
            click(enable)
            val intent = shadowOf(application).nextStartedActivity
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            systemNotifications.setNotificationsEnabled(true)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(enable).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(s(R.string.settings_start_alert)).assertExists()
        } finally {
            runBlocking { settings.setOnboardingComplete(false) }
        }
    }

    @Test fun freeReaderCanOpenReflectionEvenWithAnOldEnrollment() {
        runBlocking {
            settings.setActiveProgram(GuidedPrograms.FASTING_SHIFT_21)
            settings.setOnboardingComplete(true)
        }
        try {
            waitFor(s(R.string.tab_settings))
            click(s(R.string.tab_settings))
            compose.onNodeWithTag("settings_open_plan").performScrollTo().performClick()
            waitFor(s(R.string.plan_see_plus))
            click(s(R.string.plan_see_plus))
            waitFor(s(R.string.shift_example_review))
            click(s(R.string.shift_example_review))
            waitFor(s(R.string.review_example_label))
            compose.onNodeWithContentDescription(s(R.string.navigate_up)).performClick()
            waitFor(s(R.string.program_read_sample))
            click(s(R.string.program_read_sample))
            waitFor(s(R.string.program_sample_prompt))
            compose.onNodeWithText(s(R.string.program_sample_title)).assertExists()
            compose.onNodeWithText(s(R.string.program_start)).assertDoesNotExist()
            compose.onNodeWithText(s(R.string.plan_see_plus)).assertDoesNotExist()
        } finally {
            runBlocking {
                settings.setActiveProgram(null)
                settings.setOnboardingComplete(false)
            }
        }
    }

    @Test fun incompleteScreeningShowsTheFirstInvalidFieldBesideNext() {
        waitFor(s(R.string.onb_get_started))
        click(s(R.string.onb_get_started))
        waitFor(s(R.string.onb_goal_title))
        click(s(R.string.goal_consistency))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_screen_title))
        val liveError = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

        click(s(R.string.onb_next))
        val ageError = s(R.string.onb_form_error, s(R.string.onb_age), 1, 120)
        compose.onNode(hasText(ageError) and liveError).assertIsDisplayed()

        field(s(R.string.onb_age), "40")
        field(s(R.string.onb_height), "261")
        click(s(R.string.onb_next))
        val heightError = s(R.string.onb_form_error, s(R.string.onb_height), 50, 260)
        compose.onNode(hasText(heightError) and liveError).assertIsDisplayed()

        field(s(R.string.onb_height), "175")
        field(s(R.string.onb_weight), "70,5")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_ok_title))
    }

    @Test fun blockedUserThenUpdatedAnswersThenStartAndEndAFast() {
        // Onboarding with a hard-block answer (under 18).
        waitFor(s(R.string.onb_get_started))
        click(s(R.string.onb_get_started))
        waitFor(s(R.string.onb_goal_title))
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor(s(R.string.onb_open_tour))
        click(s(R.string.onb_open_tour))
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
        compose.onNodeWithTag("settings_open_safety").performScrollTo().performClick()
        waitFor(s(R.string.settings_rerun_screening))
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
        compose.onNodeWithContentDescription(s(R.string.navigate_up)).performClick()
        waitFor(s(R.string.tab_timer))
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
