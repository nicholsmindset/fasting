package com.fastingshift.app

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.text.format.DateFormat
import android.provider.Settings
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.app.ui.components.WeekFormat
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.FastRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var schedules: ScheduleRepository
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

    @Test fun firstReminderReflectsRoutineAndNotificationAvailabilityOnTimer() {
        val application = compose.activity.application
        val notifications = shadowOf(application.getSystemService(NotificationManager::class.java))
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.setNotificationsEnabled(true)
        val original = runBlocking { settings.toRecord() }
        runBlocking {
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
            settings.setFastStartReminder(true)
            settings.setOnboardingComplete(true)
        }
        val heading = s(R.string.first_fast_title)
        val offer = hasText(s(R.string.first_plan_choose_time))
        var routineId: Long? = null
        try {
            // First observe a real offer. An empty loading state must never pass as de-duplication.
            waitFor(heading)
            compose.onNode(offer).performScrollTo().assertIsDisplayed()
            routineId = runBlocking {
                schedules.save(Schedule(protocolId = Presets.W16_8.id, days = DayOfWeek.entries.toSet(), startTime = LocalTime.of(20, 0)))
            }
            waitFor(s(R.string.week_timer_reminder_on))
            compose.waitUntil(5_000) { compose.onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
            compose.onNode(offer).assertDoesNotExist()

            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            notifications.setNotificationsEnabled(false)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor(s(R.string.week_timer_reminder_off))
            compose.onNodeWithText(heading).assertDoesNotExist()
            compose.onNode(offer).assertDoesNotExist()
            click(s(R.string.settings_reminders))
            waitFor(s(R.string.settings_start_alert))
        } finally {
            notifications.setNotificationsEnabled(true)
            runBlocking {
                routineId?.let { schedules.delete(it) }
                settings.restore(original)
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

    @Test fun historyOpensTheReviewAvailableToThisBuildWithoutAPurchase() {
        val wasOnboarded = runBlocking { settings.current().onboardingComplete }
        runBlocking { settings.setOnboardingComplete(true) }
        try {
            waitFor(s(R.string.tab_history))
            click(s(R.string.tab_history))
            val insights = s(R.string.history_insights)
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("history-list").performScrollToNode(hasText(insights))
            click(insights)
            val reviewLabel = if (BuildConfig.INTERNAL_REVIEW_INSIGHTS) R.string.review_period else R.string.review_example_label
            waitFor(s(reviewLabel))
            if (BuildConfig.INTERNAL_REVIEW_INSIGHTS) {
                compose.onNodeWithText(s(R.string.insights_locked)).assertDoesNotExist()
            }
        } finally {
            runBlocking { settings.setOnboardingComplete(wasOnboarded) }
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
            val reviewAction = s(if (BuildConfig.INTERNAL_REVIEW_INSIGHTS) R.string.shift_open_review else R.string.shift_example_review)
            waitFor(reviewAction)
            click(reviewAction)
            waitFor(s(if (BuildConfig.INTERNAL_REVIEW_INSIGHTS) R.string.review_period else R.string.review_example_label))
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

    private fun reachFirstPlan(startNow: Boolean = false) {
        val intent = s(if (startNow) R.string.onb_intent_now else R.string.onb_get_started)
        waitFor(intent)
        click(intent)
        waitFor(s(R.string.onb_goal_title))
        click(s(R.string.goal_consistency))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_screen_title))
        field(s(R.string.onb_age), "40")
        field(s(R.string.onb_height), "175")
        field(s(R.string.onb_weight), "70")
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_ok_title))
        click(s(R.string.onb_next))
        waitFor(s(R.string.onb_protocol_title))
        click(s(R.string.onb_next))
        waitFor(s(if (startNow) R.string.first_plan_now_title else R.string.first_plan_title))
    }

    private fun chooseStartTime(hour: Int, minute: Int) {
        compose.onNodeWithTag("first-plan-clock-picker").performScrollTo().performClick()
        compose.runOnIdle {
            val picker = ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(hour, minute)
            picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
    }

    private fun captureFirstPlan(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.waitForIdle()
        val file = moatsOutput("first-plan").resolve("$name.png")
        captureScreenRoboImage(file.absolutePath)
        check(file.length() > 0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
    fun lateDinnerWindowSurvivesDecliningNotificationsAndCanBeEditedOnHome() {
        val original = runBlocking { settings.toRecord() }
        val application = compose.activity.application
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(application.getSystemService(NotificationManager::class.java))
        notifications.setNotificationsEnabled(false)
        runBlocking { settings.setFastStartReminder(true) }
        val format = WeekFormat(compose.activity.resources.configuration.locales[0], DateFormat.is24HourFormat(compose.activity)).dateTime
        try {
            reachFirstPlan()
            chooseStartTime(21, 30)
            val previewStart = compose.onNodeWithTag("first-plan-start").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            val previewFinish = compose.onNodeWithTag("first-plan-finish").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            captureFirstPlan("01-overnight-preview")
            compose.activityRule.scenario.recreate()
            waitFor(previewStart)
            waitFor(previewFinish)
            click(s(R.string.first_plan_schedule))
            waitFor(s(R.string.first_plan_saved_title))
            click(s(R.string.onb_not_now))
            waitFor(s(R.string.first_plan_home_title))
            waitFor(previewStart)
            waitFor(previewFinish)
            compose.onNodeWithTag("first-plan-saved").performScrollTo()
            captureFirstPlan("02-saved-home")

            val (id, day) = assertNotNull(schedules.firstEvening())
            val planned = runBlocking { schedules.getAll().single { it.id == id } }
            assertEquals(LocalTime.of(21, 30), planned.startTime)
            val expectedStart = day.atTime(21, 30).atZone(ZoneId.systemDefault())
            val expectedFinish = expectedStart.plusHours(14)
            assertEquals(day.plusDays(1), expectedFinish.toLocalDate())
            assertEquals(s(R.string.first_plan_starts_at, format.format(expectedStart)), previewStart)
            assertEquals(s(R.string.first_plan_finishes_at, format.format(expectedFinish)), previewFinish)
            assertNull(runBlocking { fasts.getActive() })

            click(s(R.string.first_plan_edit_time))
            chooseStartTime(22, 15)
            click(s(R.string.first_plan_save_window))
            val revisedStart = LocalTime.of(22, 15)
            compose.waitUntil(5_000) { runBlocking { schedules.getAll().singleOrNull()?.startTime == revisedStart } }
            val revised = assertNotNull(schedules.firstEvening()).second.atTime(revisedStart).atZone(ZoneId.systemDefault())
            waitFor(s(R.string.first_plan_starts_at, format.format(revised)))
            waitFor(s(R.string.first_plan_finishes_at, format.format(revised.plusHours(14))))
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        } finally {
            notifications.setNotificationsEnabled(true)
            runBlocking {
                schedules.cancelFirstEvening()
                settings.restore(original)
            }
        }
    }

    @Test fun firstPlanStartNowRunsTheChosenProtocolOnceWithoutSchedulingAReminder() {
        val original = runBlocking { settings.toRecord() }
        try {
            reachFirstPlan(startNow = true)
            assertNull(runBlocking { fasts.getActive() })
            compose.onNodeWithText(s(R.string.first_plan_schedule)).assertDoesNotExist()
            click(s(R.string.first_plan_start))
            waitFor(s(R.string.timer_end))
            val fast = assertNotNull(runBlocking { fasts.getActive() })
            assertEquals(14L, fast.plannedDuration.toHours())
            assertEquals(1, runBlocking { fasts.getAll().size })
            assertNull(schedules.firstEvening())
            compose.onNodeWithText(s(R.string.first_plan_saved_title)).assertDoesNotExist()
        } finally {
            runBlocking {
                fasts.getActive()?.let { fasts.delete(it.id) }
                settings.restore(original)
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

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [30], qualifiers = "ms-rMY-w320dp-h640dp-night-xxhdpi")
    fun compactMalayTourKeepsItsHeadingAndSetupActionAtDoubleType() {
        val originalScale = RuntimeEnvironment.getFontScale()
        try {
            RuntimeEnvironment.setFontScale(2f)
            compose.activityRule.scenario.recreate()
            waitFor(s(R.string.onb_open_tour))
            assertEquals(2f, compose.activity.resources.configuration.fontScale)
            assertEquals(320, compose.activity.resources.configuration.screenWidthDp)
            click(s(R.string.onb_open_tour))
            waitFor(s(R.string.onb_intro_heading))
            val heading = compose.onNode(hasText(s(R.string.onb_intro_heading)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val action = compose.onNodeWithText(s(R.string.onb_skip))
            val headingBounds = heading.assertIsDisplayed().getUnclippedBoundsInRoot()
            val actionBounds = action.assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(headingBounds.bottom <= actionBounds.top, "The heading and setup action should wrap onto separate rows")
            action.performClick()
            waitFor(s(R.string.onb_goal_title))
        } finally {
            RuntimeEnvironment.setFontScale(originalScale)
        }
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
        compose.onNodeWithText(s(R.string.first_plan_schedule)).assertDoesNotExist()
        assertNull(schedules.firstEvening())
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
