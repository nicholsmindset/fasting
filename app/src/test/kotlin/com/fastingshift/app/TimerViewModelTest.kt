package com.fastingshift.app

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.timer.TimerViewModel
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.service.StreakFreezeRepository
import com.fastingshift.app.ui.timer.NextFast
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.MainScope
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Timer's end / undo / refeeding logic, driven directly (no UI clock to race). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class TimerViewModelTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var billing: BillingManager
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var health: HealthConnectManager
    @Inject lateinit var service: FastService
    @Inject lateinit var clock: Clock
    @Inject lateinit var schedules: ScheduleRepository
    @Inject lateinit var freezes: StreakFreezeRepository

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
        }
    }

    @After fun tearDown() {
        runBlocking {
            settings.setPendingRefeeding(null)
            settings.setWaterGoalMl(null)
        }
    }

    private fun vm(at: Clock = clock) = TimerViewModel(fasts, screening, settings, programs, billing, logs, health, service, at, schedules, freezes)

    /** viewModelScope runs on the main looper, which the test thread must keep turning. */
    private fun <T> await(read: () -> T?): T {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        error("timed out")
    }

    private fun activeFast(hoursIn: Long, planned: Long = 16) = runBlocking {
        val start = Instant.now().minus(Duration.ofHours(hoursIn))
        fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(planned)), protocolId = "duration_$planned"))
    }

    @Test fun undoAfterEndingRestoresTheActiveFast() {
        val fast = activeFast(hoursIn = 3)
        val vm = vm()
        vm.end()
        val ended = await { vm.events.value.ended }
        assertNull(runBlocking { fasts.getActive() })
        vm.undoEnd(ended)
        val restored = await { runBlocking { fasts.getActive() } }
        assertEquals(fast.id, restored.id)
        assertNull(restored.endedAt)
    }

    @Test fun endingALongFastRoutesToRefeedingInsteadOfUndo() {
        val fast = activeFast(hoursIn = 50, planned = 48)
        val vm = vm()
        vm.end()
        val nav = await { vm.events.value.refeeding }
        assertEquals(RefeedingTier.STANDARD, nav.tier)
        assertEquals(fast.id, nav.fastId)
        assertNull(vm.events.value.ended)
    }

    @Test fun aLongFastEndedElsewhereShowsRefeedingOnceWhenTheTimerOpens() {
        val fast = activeFast(hoursIn = 80, planned = 72)
        val ended = runBlocking { service.endActive() }
        assertNotNull(ended)
        runBlocking { settings.setPendingRefeeding(fast.id) }
        val vm = vm()
        val nav = await { vm.events.value.refeeding }
        assertEquals(RefeedingTier.EXTENDED, nav.tier)
        await { runBlocking { settings.current() }.takeIf { it.pendingRefeedingFastId == null } }
    }

    /** Collects state like the screen would (it is shared WhileSubscribed). */
    private fun <T> awaitState(vm: TimerViewModel, read: (com.fastingshift.app.ui.timer.TimerUiState) -> T?): T {
        val job = vm.state.launchIn(MainScope())
        try {
            return await { vm.state.value?.let(read) }
        } finally {
            job.cancel()
        }
    }

    @Test fun todayCardUsesTheWeightBasedWaterGoalUntilOneIsSet() {
        runBlocking {
            settings.setWaterGoalMl(null)
            logs.addWeight(WeightLog(at = Instant.now(), value = 80.0, unit = WeightUnit.KG))
            logs.addWater(WaterLog(at = Instant.now(), value = 500.0, unit = VolumeUnit.ML))
        }
        val vm = vm()
        // 80 kg x 35 ml = 2800 ml.
        val today = awaitState(vm) { s -> s.today.takeIf { it.waterGoalMl == 2_800 && it.waterMl >= 500.0 } }
        assertEquals(NextFast.None, today.next)
        runBlocking { settings.setWaterGoalMl(3_000) }
        awaitState(vm) { s -> s.today.takeIf { it.waterGoalMl == 3_000 } }
    }

    @Test fun todayCardSaysFastingNowDuringAFast() {
        activeFast(hoursIn = 2)
        val vm = vm()
        awaitState(vm) { s -> s.today.next.takeIf { it == NextFast.FastingNow } }
    }

    @Test fun lateDinnerKeepsTodaysPlanReachableWithoutChangingTheNextReminder() {
        val thursday = LocalDate.of(2026, 11, 26)
        val friday = thursday.plusDays(1)
        val zone = ZoneId.systemDefault()
        fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant()
        val old = runBlocking { settings.current() }
        val id = runBlocking {
            settings.setFastStartReminder(true)
            schedules.save(Schedule(protocolId = "window_16_8", days = setOf(thursday.dayOfWeek, friday.dayOfWeek),
                startTime = LocalTime.of(20, 0)))
        }
        try {
            val vm = vm(Clock.fixed(at(thursday, 21, 40), zone))
            val late = awaitState(vm) { it.takeIf { state -> state.earlierPlannedStartToday == at(thursday, 20) } }
            assertEquals(at(friday, 20), late.plannedStart)
            assertEquals(at(friday, 20), late.startReminder)

            runBlocking { settings.setDayOverride(thursday, DayOverride(LocalTime.of(22, 15)), thursday) }
            val moved = awaitState(vm) { it.takeIf { state -> state.plannedStart == at(thursday, 22, 15) } }
            assertNull(moved.earlierPlannedStartToday)
            assertEquals(at(thursday, 22, 15), moved.startReminder)
            runBlocking { settings.setDayOverride(thursday, DayOverride(), thursday) }
            val skipped = awaitState(vm) { it.takeIf { state -> state.plannedStart == at(friday, 20) } }
            assertNull(skipped.earlierPlannedStartToday)
        } finally {
            runBlocking {
                settings.setDayOverride(thursday, old.planHistory.dayOverrides[thursday], thursday)
                settings.setFastStartReminder(old.reminders.fastStartEnabled)
                schedules.delete(id)
            }
        }
    }

    @Test fun changingOneStartUpdatesTimerAndReminderAndPreservesTheRecurringTime() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zone))
        val tomorrow = today.plusDays(1)
        val time = LocalTime.of(20, 0)
        val moved = LocalTime.of(21, 15)
        val original = tomorrow.atTime(time).atZone(zone).toInstant()
        val changed = tomorrow.atTime(moved).atZone(zone).toInstant()
        val oldSettings = runBlocking { settings.current() }
        val id = runBlocking { schedules.save(Schedule(protocolId = "window_16_8", days = setOf(tomorrow.dayOfWeek), startTime = time)) }
        try {
            runBlocking { settings.setFastStartReminder(true) }
            val vm = vm()
            awaitState(vm) { it.takeIf { s -> s.plannedStart == original && s.startReminder == original } }
            runBlocking { settings.setDayOverride(tomorrow, DayOverride(moved), today) }
            awaitState(vm) { it.takeIf { s -> s.plannedStart == changed && s.startReminder == changed } }
            runBlocking { settings.setFastStartReminder(false) }
            awaitState(vm) { it.takeIf { s -> s.plannedStart == changed && s.startReminder == null } }
            assertEquals(time, runBlocking { schedules.getAll().single { it.id == id }.startTime })
        } finally {
            runBlocking {
                settings.setDayOverride(tomorrow, oldSettings.planHistory.dayOverrides[tomorrow], today)
                settings.setFastStartReminder(oldSettings.reminders.fastStartEnabled)
                schedules.delete(id)
            }
        }
    }

    @Test fun todayCardShowsTheEatingWindowEndAsTheNextFast() {
        val start = Instant.now().minus(Duration.ofHours(18)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        runBlocking {
            val f = fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8"))
            fasts.update(f.end(start.plus(Duration.ofHours(16))))
        }
        val vm = vm()
        val next = awaitState(vm) { s -> s.today.next as? NextFast.At }
        // The 8h window after a 16:8 fast that ended 2h ago.
        assertEquals(start.plus(Duration.ofHours(24)), next.at)
    }
}
