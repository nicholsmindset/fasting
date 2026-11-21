package com.fastingshift.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LogRepositoryTest {
    @Test fun logsAreObservable() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FastingDatabase::class.java).addCallback(FastingDatabase.Constraints).allowMainThreadQueries().build()
        val repo = LogRepository(db.logDao())
        val now = Instant.parse("2026-11-20T12:00:00Z")
        repo.addWeight(WeightLog(at = now, value = 80.0, unit = WeightUnit.KG))
        repo.addWater(WaterLog(at = now, value = 500.0, unit = VolumeUnit.ML))
        repo.addWater(WaterLog(at = now.minus(Duration.ofDays(2)), value = 900.0, unit = VolumeUnit.ML))
        repo.addElectrolyte(ElectrolyteLog(at = now, electrolyte = Electrolyte.SODIUM, milligrams = 1000.0))
        val since = now.minus(Duration.ofHours(1))
        assertEquals(1, repo.observeWeights().first().size)
        assertEquals(listOf(500.0), repo.observeWaterSince(since).first().map { it.value })
        assertEquals(1, repo.observeElectrolytesSince(since).first().size)
        db.close()
    }
}
