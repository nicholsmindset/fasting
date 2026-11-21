package com.fastingshift.app

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.timer.TimerViewModel
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
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

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
        }
    }

    @After fun tearDown() {
        runBlocking { settings.setPendingRefeeding(null) }
    }

    private fun vm() = TimerViewModel(fasts, screening, settings, programs, billing, logs, health, service, clock)

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
}
