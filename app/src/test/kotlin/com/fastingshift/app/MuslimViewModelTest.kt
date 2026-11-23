package com.fastingshift.app

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.ramadan.MuslimUiState
import com.fastingshift.app.ui.ramadan.MuslimViewModel
import com.fastingshift.app.ui.ramadan.TodayFast
import com.fastingshift.data.repo.QadaRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Starting Sunnah/qada fasts until maghrib, with a fixed clock (Singapore). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class MuslimViewModelTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var qada: QadaRepository

    /** Monday 23 Nov 2026, 11:00 in Singapore (13 Jumada al-Akhirah: a white day and a Monday). */
    private val morning = Instant.parse("2026-11-23T03:00:00Z")
    private val city = Cities.byId("sg_singapore")!!

    @Before fun setUp() {
        hilt.inject()
        runBlocking { settings.setRamadanCity(city.id, PrayerMethod.MUIS) }
    }

    private fun screen(gate: GateResult) = runBlocking {
        screening.save(ScreeningRecord(morning, ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), gate, false))
    }

    private fun vm(at: Instant): MuslimViewModel {
        val clock = Clock.fixed(at, ZoneOffset.UTC)
        return MuslimViewModel(settings, fasts, FastService(fasts, screening, clock), clock)
    }

    private fun <T> await(read: () -> T?): T {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            read()?.let { return it }
            Thread.sleep(20)
        }
        error("timed out")
    }

    private fun awaitState(vm: MuslimViewModel): MuslimUiState {
        val job = vm.state.launchIn(MainScope())
        try {
            return await { vm.state.value }
        } finally {
            job.cancel()
        }
    }

    @Test fun hubUsesSelectedCityDateAcrossMidnight() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val state = awaitState(vm(Instant.parse("2026-11-22T22:00:00Z")))
            assertEquals(LocalDate.of(2026, 11, 23), state.today)
            assertTrue(FastKind.SUNNAH in state.startKinds)
            val expected = RamadanPlanner.day(city, PrayerMethod.MUIS, state.today).maghrib
            assertEquals(expected, (state.todayFast as TodayFast.Ready).maghrib)
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    @Test fun rejectsKindThatIsIneligibleAtStartTime() {
        screen(GateResult.OK)
        val vm = vm(morning)
        vm.start(FastKind.RAMADAN)
        await { vm.events.value.invalidStart.takeIf { it } }
        assertNull(runBlocking { fasts.getActive() })
    }

    @Test fun startsAQadaFastThatEndsAtMaghrib() {
        screen(GateResult.OK)
        val vm = vm(morning)
        val state = awaitState(vm)
        assertTrue(state.todayFast is TodayFast.Ready)
        vm.start(FastKind.QADA)
        await { vm.events.value.started.takeIf { it } }
        val active = await { runBlocking { fasts.getActive() } }
        val maghrib = RamadanPlanner.day(city, PrayerMethod.MUIS, LocalDate.of(2026, 11, 23)).maghrib
        assertEquals(FastKind.QADA, active.kind)
        assertEquals(morning, active.startAt)
        assertEquals(maghrib, active.targetEndAt)
    }

    @Test fun sunnahStartIsBlockedForEducationOnly() {
        screen(GateResult.BLOCK)
        val vm = vm(morning)
        vm.start(FastKind.SUNNAH)
        assertEquals(BlockReason.EDUCATION_ONLY, await { vm.events.value.blocked })
        assertNull(runBlocking { fasts.getActive() })
    }

    @Test fun afterMaghribNoFastStarts() {
        screen(GateResult.OK)
        val evening = Instant.parse("2026-11-23T12:30:00Z") // 20:30 in Singapore
        val vm = vm(evening)
        assertTrue(awaitState(vm).todayFast is TodayFast.TooLate)
        vm.start(FastKind.QADA)
        await { vm.events.value.invalidStart.takeIf { it } }
        assertNull(runBlocking { fasts.getActive() })
    }
}
