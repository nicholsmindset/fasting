package com.fastingshift.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.data.repo.RoomScreeningRepository
import com.fastingshift.data.repo.RoomStreakFreezeRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Exercises the Room schema and repositories. Uses an unencrypted in-memory database
 * because SQLCipher's native library is not available under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoomRepositoryTest {
    private lateinit var db: FastingDatabase
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FastingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    @Test fun fastRoundTripsAndActiveFlowUpdates() = runTest {
        val repo = RoomFastRepository(db.fastDao())
        repo.observeActive().test {
            assertNull(awaitItem())
            val saved = repo.insert(Fast(startAt = t0, targetEndAt = t0.plus(Duration.ofHours(16)), protocolId = "window_16_8", note = "first"))
            assertEquals(saved, awaitItem())
            assertEquals(saved, repo.getById(saved.id))

            val ended = saved.end(t0.plus(Duration.ofHours(17)))
            repo.update(ended)
            assertNull(awaitItem())
            assertEquals(FastStatus.COMPLETED, repo.getById(saved.id)!!.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun latestScreeningWins() = runTest {
        val repo = RoomScreeningRepository(db.screeningDao())
        val answers = ScreeningAnswers(40, false, false, false, setOf(Medication.GLP1, Medication.DIURETIC), 175.0, 75.0)
        repo.save(ScreeningRecord(t0, answers, GateResult.SOFT, clinicianAcknowledged = false))
        repo.save(ScreeningRecord(t0.plusSeconds(60), answers.copy(medications = emptySet()), GateResult.OK, false))
        val latest = repo.getLatest()!!
        assertEquals(GateResult.OK, latest.gateResult)
        assertEquals(emptySet(), latest.answers.medications)
    }

    @Test fun medicationsSurviveConversion() = runTest {
        val repo = RoomScreeningRepository(db.screeningDao())
        val meds = setOf(Medication.GLP1, Medication.INSULIN)
        repo.save(ScreeningRecord(t0, ScreeningAnswers(40, false, false, false, meds, 175.0, 75.0), GateResult.SOFT, true))
        assertEquals(meds, repo.getLatest()!!.answers.medications)
    }

    @Test fun streakFreezesAreUnique() = runTest {
        val repo = RoomStreakFreezeRepository(db.streakFreezeDao())
        val day = LocalDate.of(2026, 11, 19)
        repo.addFrozenDay(day)
        repo.addFrozenDay(day)
        assertEquals(setOf(day), repo.getFrozenDays())
    }
}
