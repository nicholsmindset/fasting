package com.fastingshift.app

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.safety.SafetyViewModel
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** SR-5 on the red-flag path: stopping a long fast for a red flag still leads to refeeding guidance. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class SafetyViewModelTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var checkins: SymptomCheckinRepository
    @Inject lateinit var service: FastService
    @Inject lateinit var clock: Clock

    @Before fun setUp() = hilt.inject()

    @After fun tearDown() {
        runBlocking { settings.setPendingRefeeding(null) }
    }

    private fun stopFastAfter(hours: Long): Long {
        val start = Instant.now().minus(Duration.ofHours(hours))
        val f = runBlocking { fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(72)), protocolId = "duration_72")) }
        var done = false
        SafetyViewModel(checkins, service, clock, settings).endFast { done = true }
        repeat(200) {
            if (done) return f.id
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        error("timed out")
    }

    @Test fun redFlagStopOfALongFastSetsPendingRefeeding() {
        val id = stopFastAfter(50)
        assertEquals(id, runBlocking { settings.current().pendingRefeedingFastId })
    }

    @Test fun redFlagStopOfAShortFastNeedsNoRefeeding() {
        stopFastAfter(10)
        assertNull(runBlocking { settings.current().pendingRefeedingFastId })
    }
}
