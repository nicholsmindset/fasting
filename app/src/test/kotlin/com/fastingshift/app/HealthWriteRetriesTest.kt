package com.fastingshift.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.health.HealthWriteRetries
import com.fastingshift.app.health.RetryKey
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class HealthWriteRetriesTest {
    private val at = Instant.parse("2026-09-01T16:00:01.123456Z")
    private val water = WaterLog(7, at, 250.0, VolumeUnit.ML)
    private val weight = WeightLog(7, at, 76.0, WeightUnit.KG)
    private val unavailable = Result.failure<Unit>(IllegalStateException("unavailable"))

    private fun preferences(name: String) =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test fun failureSurvivesRecreationAndConcurrentRetriesWriteOnce() = runBlocking {
        val prefs = preferences("retry-after-recreation")
        val key = RetryKey.forWater(water)
        val original = HealthWriteRetries(prefs)
        repeat(2) { assertTrue(original.write(key) { unavailable }.isFailure) }
        val queue = HealthWriteRetries(prefs)
        assertEquals(1, queue.pendingCount.value)
        var writes = 0
        (1..3).map {
            async {
                queue.retry({ true }) { item ->
                    yield()
                    assertEquals(key, item)
                    writes++
                    Result.success(Unit)
                }
            }
        }.awaitAll()
        assertEquals(1, writes)
        assertEquals(0, HealthWriteRetries(prefs).pendingCount.value)
    }

    @Test fun disabledSyncKeepsTheFailureAndDeletedEntriesAreForgotten() = runBlocking {
        val queue = HealthWriteRetries(preferences("retry-consent"))
        val key = RetryKey.forWater(water)
        queue.write(key) { unavailable }
        queue.retry({ false }) { error("Disabled sync attempted a write") }
        assertEquals(1, queue.pendingCount.value)
        queue.retry({ true }) { unavailable }
        assertEquals(1, queue.pendingCount.value)
        queue.forget(key)
        assertEquals(0, queue.pendingCount.value)
    }

    @Test fun fingerprintMatchesRoomPrecisionAndRejectsRestoredRowsAndOtherTables() {
        val key = RetryKey.forWeight(weight)
        assertEquals(key, RetryKey.decode(key.encode()))
        assertEquals(key, RetryKey.forWeight(weight.copy(at = Instant.ofEpochMilli(at.toEpochMilli()))))
        assertNotEquals(key, RetryKey.forWater(water))
        assertNotEquals(key, RetryKey.forWeight(weight.copy(value = 75.0)))
        assertNotEquals(key, RetryKey.forWeight(weight.copy(at = at.plusSeconds(1))))
    }
}
