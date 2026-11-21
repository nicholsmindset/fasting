package com.fastingshift.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.log.LogViewModel
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class LogViewModelTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var health: HealthConnectManager
    @Inject lateinit var clock: Clock

    @Before fun setUp() = hilt.inject()

    @Test fun showsTodaysWaterAndRecentWeights() = runBlocking {
        val now = Instant.now()
        logs.addWater(WaterLog(at = now, value = 500.0, unit = VolumeUnit.ML))
        logs.addWeight(WeightLog(at = now, value = 80.0, unit = WeightUnit.KG))
        val vm = LogViewModel(logs, settings, health, clock)
        // A subscriber, as the screen would be (state uses WhileSubscribed).
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { vm.state.collect {} }
        val state = withTimeout(10_000) {
            var s = vm.state.value
            while (s.weights.isEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                s = vm.state.value
                kotlinx.coroutines.delay(50)
            }
            s
        }
        collector.cancel()
        assertEquals(500.0, state.waterTodayMl)
        assertEquals(1, state.weights.size)
    }
}
