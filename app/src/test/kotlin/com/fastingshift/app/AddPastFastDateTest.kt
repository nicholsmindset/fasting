package com.fastingshift.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.fast.AddPastFastViewModel
import com.fastingshift.app.ui.fast.canAddPastFastEndingOn
import com.fastingshift.app.ui.fast.pastFastEndOn
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class AddPastFastDateTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var service: FastService
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var screening: ScreeningRepository
    @Before fun setUp() = hilt.inject()

    @Test fun localNoonRespectsSpringAndFallTransitions() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-11-24T12:20:00Z")
        assertEquals(Instant.parse("2026-03-08T16:00:00Z"), pastFastEndOn(LocalDate.parse("2026-03-08"), zone, now))
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), pastFastEndOn(LocalDate.parse("2026-11-01"), zone, now))
    }

    @Test fun aSelectedDayMustContainARecordableMinute() {
        val zone = ZoneId.of("Asia/Singapore")
        val today = LocalDate.parse("2026-11-24")
        val morning = Instant.parse("2026-11-23T20:15:50Z")
        assertEquals(Instant.parse("2026-11-23T20:15:00Z"), pastFastEndOn(today, zone, morning))
        assertFalse(canAddPastFastEndingOn(today.plusDays(1), zone, morning))
        assertFalse(canAddPastFastEndingOn(today.minusDays(367), zone, morning))
        assertFalse(canAddPastFastEndingOn(today, zone, today.atStartOfDay(zone).toInstant()))
        val nearMidnight = today.atTime(23, 30).atZone(zone).toInstant()
        assertFalse(canAddPastFastEndingOn(today.minusDays(366), zone, nearMidnight))
    }

    @Test fun datedDraftKeepsEndWhenSelectingAProtocolAndSurvivesRecreation() {
        val clock = Clock.fixed(Instant.parse("2026-11-24T12:40:00Z"), ZoneOffset.UTC)
        val day = LocalDate.parse("2026-11-19")
        val handle = SavedStateHandle(mapOf("date" to day.toString()))
        val vm = AddPastFastViewModel(service, handle, clock)
        val noon = assertNotNull(pastFastEndOn(day, ZoneId.systemDefault(), clock.instant()))
        assertEquals(noon, vm.state.value.end)
        vm.select(Presets.W14_10)
        assertEquals(noon.minus(Duration.ofHours(14)), vm.state.value.start)
        assertEquals(noon, vm.state.value.end)
        vm.setEnd(noon.minusSeconds(900))
        assertEquals(vm.state.value, AddPastFastViewModel(service, handle, clock).state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun invalidDraftCanRetryAndRepeatedSaveInsertsAndNavigatesOnce() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-11-24T12:40:00Z"), ZoneOffset.UTC)
        val releaseRead = CompletableDeferred<Unit>()
        val inserted = mutableListOf<Fast>()
        var reads = 0
        val repository = object : FastRepository by fasts {
            override suspend fun getAll(): List<Fast> {
                reads++
                val snapshot = inserted.toList()
                releaseRead.await()
                return snapshot
            }

            override suspend fun insert(fast: Fast): Fast =
                fast.copy(id = inserted.size.toLong() + 1).also { inserted.add(it) }
        }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AddPastFastViewModel(FastService(repository, screening, clock), SavedStateHandle(), clock)
            val end = vm.state.value.end
            var navigations = 0
            vm.setEnd(vm.state.value.start)
            vm.save { navigations++ }
            runCurrent()
            assertTrue(vm.state.value.error)
            assertFalse(vm.state.value.saving)

            vm.setEnd(end)
            vm.save { navigations++ }
            vm.save { navigations++ }
            runCurrent()
            assertTrue(vm.state.value.saving)
            assertEquals(1, reads)
            vm.save { navigations++ }
            releaseRead.complete(Unit)
            runCurrent()
            vm.save { navigations++ }
            runCurrent()
            assertEquals(1, inserted.size)
            assertEquals(1, navigations)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun undatedFormKeepsItsUsualDefaultsAndInvalidLinksClose() {
        val clock = Clock.fixed(Instant.parse("2026-11-24T12:40:12Z"), ZoneOffset.UTC)
        val vm = AddPastFastViewModel(service, SavedStateHandle(), clock)
        val start = vm.state.value.start
        assertEquals(Instant.parse("2026-11-24T10:40:00Z"), vm.state.value.end)
        vm.select(Presets.W14_10)
        assertEquals(start.plus(Duration.ofHours(14)), vm.state.value.end)
        listOf("2028-01-01", "invalid").forEach { raw ->
            assertTrue(AddPastFastViewModel(service, SavedStateHandle(mapOf("date" to raw)), clock).unavailableDate)
        }
    }
}
