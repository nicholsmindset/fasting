package com.fastingshift.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.BackupRepository
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.data.repo.RoomScreeningRepository
import com.fastingshift.data.repo.RoomStreakFreezeRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.domain.backup.BackupCodec
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.service.ScreeningRecord
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.fastingshift.data.repo.ChallengeRepository
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.data.repo.QadaRepository
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import com.fastingshift.domain.model.QadaDebt
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** PRD F8 acceptance: export, wipe ("reinstall"), restore loses no data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRepositoryTest {
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FastingDatabase::class.java)
        .addCallback(FastingDatabase.Constraints).allowMainThreadQueries().build()

    @Test fun exportRestoreRoundTripLosesNothing() = runTest {
        val db = newDb()
        val fasts = RoomFastRepository(db.fastDao())
        val f1 = fasts.insert(Fast(startAt = t0, targetEndAt = t0.plus(Duration.ofHours(36)), protocolId = "duration_36").let { it })
        fasts.update(f1.end(t0.plus(Duration.ofHours(30))))
        fasts.insert(Fast(startAt = t0.plus(Duration.ofDays(2)), targetEndAt = t0.plus(Duration.ofDays(2)).plus(Duration.ofHours(16)), protocolId = "window_16_8", note = "a, \"b\""))
        val logs = LogRepository(db.logDao())
        logs.addWeight(WeightLog(at = t0, value = 80.5, unit = WeightUnit.KG))
        logs.addWater(WaterLog(at = t0, value = 500.0, unit = VolumeUnit.ML))
        logs.addElectrolyte(ElectrolyteLog(at = t0, electrolyte = Electrolyte.SODIUM, milligrams = 1000.0))
        assertTrue(SymptomCheckinRepository(db.symptomCheckinDao()).record(f1.id, t0.plus(Duration.ofHours(8)), setOf(Symptom.CHEST_PAIN)))
        RoomScreeningRepository(db.screeningDao()).save(
            ScreeningRecord(t0, ScreeningAnswers(40, false, false, false, setOf(Medication.GLP1), 175.0, 75.0), GateResult.SOFT, true),
        )
        ScheduleRepository(db.scheduleDao()).save(Schedule(protocolId = "weekly_5_2", days = setOf(DayOfWeek.MONDAY), startTime = LocalTime.of(20, 30)))
        ProgramRepository(db.programProgressDao()).completeDay("fasting_shift_21", 1, t0)
        RoomStreakFreezeRepository(db.streakFreezeDao()).addFrozenDay(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20))
        fasts.insert(
            Fast(startAt = t0.minus(Duration.ofDays(3)), targetEndAt = t0.minus(Duration.ofDays(3)).plus(Duration.ofHours(14)), protocolId = "window_16_8", kind = FastKind.QADA)
                .end(t0.minus(Duration.ofDays(3)).plus(Duration.ofHours(14))),
        )
        MetricRepository(db.metricDao()).add(MetricLog(at = t0, type = MetricType.GLUCOSE, value = 92.0, unit = MetricUnit.MG_DL))
        MetricRepository(db.metricDao()).add(MetricLog(at = t0, type = MetricType.KETONE, value = 0.8, unit = MetricUnit.MMOL_L, note = "morning"))
        QadaRepository(db.qadaDao()).setOwed(QadaDebt(1447, 6))
        ChallengeRepository(db.challengeDao()).join(
            Challenge(type = ChallengeType.SUNNAH_MON_THU, startDate = LocalDate.of(2026, 11, 23), lengthDays = 28, name = "Family", joinedAt = t0),
        )

        val transferTime = t0.plus(Duration.ofHours(49))
        val exported = BackupRepository(db).export(transferTime)
        val text = BackupCodec.encode(exported)
        db.close()

        val fresh = newDb()
        BackupRepository(fresh).restore(BackupCodec.decode(text), restoredAt = transferTime)
        val again = BackupRepository(fresh).export(transferTime)
        // Screening ids are regenerated; everything else must match exactly.
        assertEquals(exported, again)
        assertEquals(3, RoomFastRepository(fresh.fastDao()).getAll().size)
        assertEquals(FastKind.QADA, RoomFastRepository(fresh.fastDao()).getAll().single { it.kind != FastKind.REGULAR }.kind)
        assertEquals(2, again.metrics.size)
        assertEquals(listOf(com.fastingshift.domain.backup.QadaDebtRecord(1447, 6)), again.qadaDebts)
        assertEquals(1, again.challenges.size)
        assertEquals(1, RoomFastRepository(fresh.fastDao()).getAll().count { it.isActive })
        fresh.close()
    }
}
