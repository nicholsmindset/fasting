package com.fastingshift.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.db.FastEntity
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.BackupRepository
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.data.repo.RoomScreeningRepository
import com.fastingshift.data.repo.RoomStreakFreezeRepository
import com.fastingshift.data.settings.AppAppearance
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.backup.Backup
import com.fastingshift.domain.backup.BackupCodec
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.backup.FastRecord
import com.fastingshift.domain.backup.ScreeningRecordDto
import com.fastingshift.domain.backup.SettingsRecord
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.timer.TimerDisplayMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DataHardeningTest {
    private lateinit var db: FastingDatabase
    private val t0 = Instant.parse("2026-11-20T12:00:00Z")
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FastingDatabase::class.java).addCallback(FastingDatabase.Constraints).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun active(start: Instant) = Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8")

    @Test fun onlyOneActiveFastCanExist() = runTest {
        val repo = RoomFastRepository(db.fastDao())
        assertNotNull(repo.insertIfNoActive(active(t0)))
        assertNull(repo.insertIfNoActive(active(t0.plusSeconds(5))))
        // Even a raw insert that skips the check is refused by the database itself.
        assertFailsWith<android.database.sqlite.SQLiteConstraintException> {
            db.fastDao().insert(FastEntity(0, t0, t0.plusSeconds(3600), null, "p", FastStatus.ACTIVE, null))
        }
        assertEquals(1, repo.getAll().count { it.isActive })
    }

    @Test fun restoreRecomputesTheSafetyGate() = runTest {
        // A hand-edited backup claiming "OK" for a 16-year-old must still be hard-blocked.
        val tampered = ScreeningRecordDto(t0.toEpochMilli(), 16, false, false, false, emptyList(), 175.0, 70.0, "OK", true)
        BackupRepository(db).restore(Backup(exportedAt = 1, screenings = listOf(tampered)))
        val latest = RoomScreeningRepository(db.screeningDao()).getLatest()!!
        assertEquals(GateResult.BLOCK, latest.gateResult)
        assertFalse(latest.clinicianAcknowledged)
    }

    @Test fun corruptRowsAbortRestoreAndKeepExistingData() = runTest {
        val repo = RoomFastRepository(db.fastDao())
        repo.insert(active(t0))
        // ACTIVE status with an end time: the app could never read this back.
        val bad = FastRecord(9, t0.toEpochMilli(), t0.toEpochMilli() + 3_600_000, t0.toEpochMilli() + 1_000, "p", "ACTIVE")
        assertFailsWith<BackupFormatException> { BackupRepository(db).restore(Backup(exportedAt = 1, fasts = listOf(bad))) }
        assertEquals(1, repo.getAll().size)
    }

    @Test fun restoreEndsAnActiveFastTheScreeningWouldNotAllow() = runTest {
        val blocked = ScreeningRecordDto(t0.toEpochMilli(), 16, false, false, false, emptyList(), 175.0, 70.0, "BLOCK", false)
        val active = FastRecord(1, t0.toEpochMilli(), t0.plusSeconds(16 * 3600).toEpochMilli(), null, "window_16_8", "ACTIVE")
        BackupRepository(db).restore(Backup(exportedAt = t0.plusSeconds(3600).toEpochMilli(), fasts = listOf(active), screenings = listOf(blocked)))
        val repo = RoomFastRepository(db.fastDao())
        assertNull(repo.getActive())
        assertEquals(t0.plusSeconds(3600), repo.getAll().single().endedAt)
    }

    @Test fun restoreEndsAnActiveFastOlderThanTheLongestAllowed() = runTest {
        val ok = ScreeningRecordDto(t0.toEpochMilli(), 40, false, false, false, emptyList(), 175.0, 75.0, "OK", false)
        val active = FastRecord(1, t0.toEpochMilli(), t0.plusSeconds(16 * 3600).toEpochMilli(), null, "window_16_8", "ACTIVE")
        BackupRepository(db).restore(Backup(exportedAt = t0.plusSeconds(7 * 24 * 3600).toEpochMilli(), fasts = listOf(active), screenings = listOf(ok)))
        val restored = RoomFastRepository(db.fastDao()).getAll().single()
        assertFalse(restored.isActive)
        assertEquals(t0.plusSeconds(16 * 3600), restored.endedAt)
    }

    @Test fun delayedRestoreDoesNotRestartAnExpiredFast() = runTest {
        val ok = ScreeningRecordDto(t0.toEpochMilli(), 40, false, false, false, emptyList(), 175.0, 75.0, "OK", false)
        val active = FastRecord(1, t0.toEpochMilli(), t0.plusSeconds(16 * 3600).toEpochMilli(), null, "window_16_8", "ACTIVE")
        val exported = t0.plusSeconds(3600)
        val backup = Backup(exportedAt = exported.toEpochMilli(), fasts = listOf(active), screenings = listOf(ok))
        val repo = RoomFastRepository(db.fastDao())
        BackupRepository(db).restore(backup, restoredAt = exported)
        assertNotNull(repo.getActive())
        BackupRepository(db).restore(backup, restoredAt = t0.plus(Duration.ofDays(10)))
        assertNull(repo.getActive())
        assertEquals(exported, repo.getAll().single().endedAt)
    }

    @Test fun restoreKeepsWeightHiddenAndClearsPendingRefeeding() = runTest {
        val settings = SettingsRepository(context)
        settings.setHideWeight(true)
        settings.setPendingRefeeding(42)
        BackupRepository(db, settings).restore(Backup(exportedAt = 1, settings = SettingsRecord(hideWeight = false)))
        assertTrue(settings.current().hideWeight)
        assertNull(settings.current().pendingRefeedingFastId)
        settings.setHideWeight(false)
    }

    @Test fun settingsTravelWithTheBackup() = runTest {
        val settings = SettingsRepository(context)
        settings.setHideWeight(true)
        settings.setTimerMode(TimerDisplayMode.BREAK_AT)
        settings.setMilestoneHours(setOf(12, 18))
        val backup = BackupRepository(db, settings).export(t0)
        assertEquals(SettingsRecord::class, backup.settings!!::class)
        settings.setHideWeight(false)
        settings.setTimerMode(TimerDisplayMode.ELAPSED)
        BackupRepository(db, settings).restore(backup)
        val s = settings.current()
        assertTrue(s.hideWeight)
        assertEquals(TimerDisplayMode.BREAK_AT, s.timerMode)
        assertEquals(listOf(12L, 18L), s.reminders.milestones.map { it.toHours() })
    }

    @Test fun appearancePersistsAndRoundTripsThroughBackup() = runTest {
        val settings = SettingsRepository(context)
        assertEquals(AppAppearance.SYSTEM, settings.current().appearance)
        for (mode in listOf(AppAppearance.LIGHT, AppAppearance.DARK, AppAppearance.SYSTEM)) {
            settings.setAppearance(mode)
            assertEquals(mode, SettingsRepository(context).current().appearance)
            val backup = BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, settings = settings.toRecord())))
            assertEquals(mode.name, backup.settings?.appearance)
            settings.setAppearance(if (mode == AppAppearance.DARK) AppAppearance.LIGHT else AppAppearance.DARK)
            settings.restore(requireNotNull(backup.settings))
            assertEquals(mode, settings.current().appearance)
        }
    }

    @Test fun restoringMissingOrUnknownAppearanceKeepsCurrentPreference() = runTest {
        val settings = SettingsRepository(context)
        settings.setAppearance(AppAppearance.LIGHT)
        val older = BackupCodec.decode("""{"version":5,"exported_at":1,"settings":{}}""")
        settings.restore(requireNotNull(older.settings))
        assertEquals(AppAppearance.LIGHT, settings.current().appearance)
        settings.restore(SettingsRecord(appearance = "unknown"))
        assertEquals(AppAppearance.LIGHT, settings.current().appearance)
        settings.setAppearance(AppAppearance.SYSTEM)
    }

    @Test fun undoWorksThroughTheRealDatabase() = runTest {
        // Room stores milliseconds; an end time with nanoseconds must still match on undo.
        val repo = RoomFastRepository(db.fastDao())
        val clock = object : java.time.Clock() {
            var now: Instant = t0.plusNanos(123_456_789)
            override fun getZone() = java.time.ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        val screening = RoomScreeningRepository(db.screeningDao())
        screening.save(com.fastingshift.domain.service.ScreeningRecord(t0, com.fastingshift.domain.safety.ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 75.0), GateResult.OK, false))
        val service = com.fastingshift.domain.service.FastService(repo, screening, clock)
        service.start(com.fastingshift.domain.protocol.Presets.W16_8)
        clock.now = clock.now.plus(Duration.ofHours(2)).plusNanos(987_654)
        val ended = service.endActive()!!.fast
        assertTrue(service.undoEnd(ended))
        assertNotNull(repo.getActive())
    }

    @Test fun freezesAreLimitedInTheRepositoryToo() = runTest {
        val repo = RoomStreakFreezeRepository(db.streakFreezeDao())
        val today = LocalDate.of(2026, 11, 20)
        assertFalse(repo.addFrozenDay(today.minusDays(30), today))
        assertTrue(repo.addFrozenDay(today.minusDays(1), today))
        assertFalse(repo.addFrozenDay(today.minusDays(2), today))
    }
}
