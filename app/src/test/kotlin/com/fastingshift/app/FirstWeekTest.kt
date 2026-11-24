package com.fastingshift.app

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.firstweek.FastSummaryViewModel
import com.fastingshift.app.ui.firstweek.FirstFastPlanner
import com.fastingshift.app.ui.firstweek.FirstWeekRules
import com.fastingshift.app.ui.onboarding.OnboardingViewModel
import com.fastingshift.app.ui.onboarding.recommendedProtocol
import com.fastingshift.app.ui.onboarding.Step
import com.fastingshift.app.ui.onboarding.stepProgress
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.onboarding.Goal
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** First-week rules (first fast card, coaching tips, undo, onboarding helpers) and their view models. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class FirstWeekTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var service: FastService
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var schedules: ScheduleRepository
    @Inject lateinit var settings: com.fastingshift.data.settings.SettingsRepository
    @Inject lateinit var planner: FirstFastPlanner
    @Inject lateinit var clock: Clock
    @Inject lateinit var screening: ScreeningRepository

    @Before fun setUp() = hilt.inject()

    private fun <T> await(read: () -> T?): T {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        error("timed out")
    }

    private val t0 = Instant.parse("2026-11-20T06:00:00Z")
    private fun fast(hoursIn: Long, planned: Long = 16, now: Instant = t0) =
        now.minus(Duration.ofHours(hoursIn)).let { Fast(id = 9, startAt = it, targetEndAt = it.plus(Duration.ofHours(planned)), protocolId = "window_16_8") }

    @Test fun firstFastCardOnlyForSomeoneWhoHasNeverFastedAndMayStart() {
        assertTrue(FirstWeekRules.showFirstFastCard(false, GateResult.OK, false, Presets.W16_8))
        assertFalse(FirstWeekRules.showFirstFastCard(true, GateResult.OK, false, Presets.W16_8), "already fasted")
        assertFalse(FirstWeekRules.showFirstFastCard(false, null, false, Presets.W16_8), "not screened")
        assertFalse(FirstWeekRules.showFirstFastCard(false, GateResult.BLOCK, false, Presets.W16_8), "education only")
        assertFalse(FirstWeekRules.showFirstFastCard(false, GateResult.SOFT, false, Presets.W16_8), "needs doctor first")
        assertTrue(FirstWeekRules.showFirstFastCard(false, GateResult.SOFT, true, Presets.W16_8))
        assertFalse(FirstWeekRules.showFirstFastCard(false, GateResult.SOFT, true, Presets.D36), "default longer than allowed")
    }

    @Test fun coachingTipFollowsSettingAndFirstFasts() {
        val f = fast(hoursIn = 4)
        assertEquals(CoachingTip.HUNGER_WAVES, FirstWeekRules.coachingTip(true, 0, t0.minus(Duration.ofDays(1)), f, t0))
        assertEquals(CoachingTip.HUNGER_WAVES, FirstWeekRules.coachingTip(true, 2, null, f, t0))
        assertNull(FirstWeekRules.coachingTip(false, 0, null, f, t0), "tips hidden")
        assertNull(FirstWeekRules.coachingTip(true, 3, null, f, t0), "past the coached fasts")
        assertNull(FirstWeekRules.coachingTip(true, 0, t0.minus(Duration.ofDays(20)), f, t0), "past the first two weeks")
        assertNull(FirstWeekRules.coachingTip(true, 0, null, fast(hoursIn = 1), t0), "too early for a tip")
        assertEquals(CoachingTip.HYDRATE, FirstWeekRules.coachingTip(true, 0, null, fast(hoursIn = 7), t0))
        assertNull(FirstWeekRules.coachingTip(true, 0, null, f.end(t0), t0), "ended fasts show nothing")
    }

    @Test fun fastsStartedBeforeCountsOnlyEarlierFasts() {
        val f = fast(hoursIn = 2)
        val earlier = Fast(id = 1, startAt = f.startAt.minus(Duration.ofDays(1)), targetEndAt = f.startAt.minus(Duration.ofHours(10)), protocolId = "x")
        assertEquals(1, FirstWeekRules.fastsStartedBefore(listOf(earlier, f), f))
    }

    @Test fun nextEveningIsTonightOrTomorrow() {
        val zone = ZoneId.of("Asia/Singapore")
        val afternoon = LocalDate.of(2026, 11, 20).atTime(15, 0).atZone(zone).toInstant()
        assertEquals(LocalDate.of(2026, 11, 20).atTime(20, 0).atZone(zone), FirstWeekRules.nextEvening(afternoon, zone))
        val late = LocalDate.of(2026, 11, 20).atTime(21, 30).atZone(zone).toInstant()
        assertEquals(LocalDate.of(2026, 11, 21).atTime(20, 0).atZone(zone), FirstWeekRules.nextEvening(late, zone))
    }

    @Test fun undoOnlyInsideTheWindow() {
        val ended = fast(hoursIn = 5).end(t0)
        assertTrue(FirstWeekRules.canUndo(ended, t0.plus(Duration.ofMinutes(14))))
        assertFalse(FirstWeekRules.canUndo(ended, t0.plus(Duration.ofMinutes(16))))
        assertFalse(FirstWeekRules.canUndo(fast(hoursIn = 5), t0), "active fast")
    }

    @Test fun onboardingSuggestsAProtocolPerGoalAndCountsSteps() {
        assertEquals(Presets.W14_10, recommendedProtocol(Goal.CONSISTENCY))
        assertEquals(Presets.W16_8, recommendedProtocol(Goal.WEIGHT))
        assertEquals(Presets.W16_8, recommendedProtocol(Goal.METABOLIC_HEALTH))
        assertEquals(Presets.W16_8, recommendedProtocol(Goal.LONG_FASTS))
        assertNull(stepProgress(Step.WELCOME, rerun = false))
        assertNull(stepProgress(Step.INTRO, rerun = false))
        assertEquals(1 to 5, stepProgress(Step.GOAL, rerun = false))
        assertEquals(5 to 5, stepProgress(Step.NOTIFICATIONS, rerun = false))
        assertNull(stepProgress(Step.COMPLETE, rerun = false))
        assertEquals(1 to 3, stepProgress(Step.SCREENING, rerun = true))
        assertEquals(3 to 3, stepProgress(Step.PROTOCOL, rerun = true))
    }

    @Test fun optionalTourAndDirectSetupKeepTheirOwnBackPathAndGoal() {
        val handle = SavedStateHandle(mapOf("step" to Step.WELCOME.name))
        val vm = OnboardingViewModel(screening, settings, clock, handle)
        vm.openGoal()
        assertEquals(Step.GOAL, vm.state.value.step)
        vm.continueFromGoal()
        assertEquals(Step.GOAL, vm.state.value.step)
        vm.setGoal(Goal.CONSISTENCY)
        vm.continueFromGoal()
        assertEquals(Step.SCREENING, vm.state.value.step)
        vm.back()
        assertEquals(Goal.CONSISTENCY, vm.state.value.goal)
        shadowOf(Looper.getMainLooper()).idle()
        val restored = OnboardingViewModel(screening, settings, clock, SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        restored.back()
        assertEquals(Step.WELCOME, restored.state.value.step)
        restored.openTour()
        assertEquals(Step.INTRO, restored.state.value.step)
        restored.openGoal()
        assertEquals(Goal.CONSISTENCY, restored.state.value.goal)
        restored.back()
        assertEquals(Step.INTRO, restored.state.value.step)
        restored.back()
        assertEquals(Step.WELCOME, restored.state.value.step)
    }

    @Test fun restoredPaidOfferFinishesSetupAndMissingAnswersReturnToScreening() {
        fun restored(step: String, weight: String = "70"): OnboardingViewModel {
            val handle = SavedStateHandle(mapOf("step" to step, "age" to "40", "height" to "175", "weight" to weight))
            return OnboardingViewModel(screening, settings, clock, handle)
        }
        assertEquals(Step.COMPLETE, restored("OFFER").state.value.step)
        assertEquals(Step.SCREENING, restored("OFFER", weight = "").state.value.step)
        val notifications = restored(Step.NOTIFICATIONS.name)
        notifications.completeNotifications()
        notifications.completeNotifications()
        assertEquals(Step.COMPLETE, notifications.state.value.step)
    }

    @Test fun summarySavesMoodAndNote() {
        val start = Instant.now().minus(Duration.ofHours(17))
        val saved = runBlocking {
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8").end(Instant.now()))
        }
        val vm = FastSummaryViewModel(logs, service, clock)
        vm.bind(saved)
        vm.setMood(4)
        vm.setNote("  Felt calm  ")
        var done = false
        vm.save { done = true }
        await { done.takeIf { it } }
        val stored = runBlocking { fasts.getById(saved.id) }!!
        assertEquals(4, stored.mood)
        assertEquals("Felt calm", stored.note)
    }

    @Test fun tappingTheSameMoodClearsIt() {
        val vm = FastSummaryViewModel(logs, service, clock)
        vm.setMood(2)
        vm.setMood(2)
        assertNull(vm.mood.value)
    }

    @Test fun startTonightIsAOneOffScheduleRemovedOnceAFastStarts() = runBlocking {
        settings.setOnboardingComplete(true)
        planner.cancel()
        val plan = planner.planTonight(Presets.W16_8)
        val schedule = schedules.getAll().single { it.id == plan.scheduleId }
        assertEquals(LocalTime.of(20, 0), schedule.startTime)
        assertEquals(1, schedule.days.size)
        assertEquals(plan.date.dayOfWeek, schedule.days.single())
        assertTrue(schedule.days.single() in DayOfWeek.entries)
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        val history = settings.current().planHistory
        assertTrue((0L..6L).all { history.isPlanned(today.plusDays(it)) })
        // Nothing started yet: the plan stays.
        planner.cleanUp()
        assertEquals(plan, planner.planned())
        // The first fast starts: the one-off schedule goes.
        val start = Instant.now().minus(Duration.ofHours(1))
        fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
        planner.cleanUp()
        assertNull(planner.planned())
        assertTrue(schedules.getAll().none { it.id == plan.scheduleId })
    }
}
