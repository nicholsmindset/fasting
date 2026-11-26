package com.fastingshift.app.ui.revival

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.BackupRepository
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.data.repo.RoomScreeningRepository
import com.fastingshift.data.repo.RoomStreakFreezeRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.service.ScreeningRecord
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RevivalEventFlowTest {
    @Test fun joiningAndRestoringAreRecordedOnceWithoutAlteringFastingHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, FastingDatabase::class.java).allowMainThreadQueries().build()
        val prefs = SettingsRepository(context)
        val fasts = RoomFastRepository(db.fastDao())
        val screening = RoomScreeningRepository(db.screeningDao())
        val zone = ZoneId.systemDefault()
        fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour,0).atZone(zone).toInstant()
        fun during(date: String) = RevivalEvents(db, fasts, RoomStreakFreezeRepository(db.streakFreezeDao()),
            prefs, screening, Clock.fixed(at(date, 10), zone))
        suspend fun completed(date: String) {
            val start = at(date, 12)
            val end = start.plusSeconds(3600)
            fasts.insert(Fast(startAt = start, targetEndAt = end, protocolId = "window_16_8").end(end))
        }
        try {
            prefs.clearAll()
            prefs.setOnboardingComplete(true)
            prefs.recordSchedule(LocalDate.of(2026, 12, 1), PlanHistory.EVERY_DAY)
            val opening = during("2027-01-01")
            val answers = ScreeningAnswers(40, false, false, false, emptySet(), 174.0, 80.0)
            val screened = ScreeningRecord(at("2026-12-01", 10), answers, GateResult.OK, false)
            // No transmission, enrollment or invitation for a missing screen, or no past streak.
            assertFalse(opening.claimStartAlert("january-2027"))
            screening.save(screened)
            assertFalse(opening.claimStartAlert("january-2027"))
            (8..11).forEach { completed("2026-12-%02d".format(it)) }
            val scheduled = during("2026-12-31").nextAlert(null)
            assertEquals("january-2027" to at("2027-01-01", 10), scheduled)
            assertFalse(during("2027-01-02").claimStartAlert("january-2027"))
            assertFalse(opening.claimStartAlert("post-eid-2027"))
            assertTrue(opening.claimStartAlert("january-2027"))
            assertFalse(opening.claimStartAlert("january-2027"))
            opening.join("january-2027")
            opening.join("january-2027")
            val enrolled = db.revivalDao().all().single { it.id == "january-2027" }
            assertEquals(4, enrolled.baselineBest)
            assertNull(enrolled.restored)
            completed("2027-01-02")
            completed("2027-01-03")
            completed("2027-01-04")
            val original = fasts.getAll()
            // Launching after the window still recognizes the days; the invitation stays off-screen.
            val earned = withTimeout(10_000) { during("2027-01-17").observe().first { it.completedCount == 1 } }
            assertNull(earned.event)
            assertEquals(1, earned.joinedCount)
            assertEquals(4, earned.award?.restoredDays)
            assertNull(earned.currentRevived)
            assertEquals(original, fasts.getAll())
            val backupRepo = BackupRepository(db, prefs)
            val backup = backupRepo.export(at("2027-01-17", 12))
            assertEquals(1, backup.revivalEvents.size)
            backupRepo.restore(backup, at("2027-01-18", 12))
            assertEquals(4, db.revivalDao().all().single().restoredDays)
            // A custom Room event spanning Eid cannot invite, enroll or prompt on a prohibited day.
            val eidEvent = enrolled.copy(id = "spring-week", starts = at("2027-03-10", 0).toEpochMilli(),
                ends = at("2027-03-17", 0).toEpochMilli(), joined = null, baselineBest = null, notified = false)
            db.revivalDao().seed(listOf(eidEvent))
            val eid = during("2027-03-10")
            assertFalse(eid.claimStartAlert(eidEvent.id))
            eid.join(eidEvent.id)
            assertNull(db.revivalDao().all().single { it.id == eidEvent.id }.joined)
            val onEid = withTimeout(10_000) { eid.observe().first { it.joinedCount == 1 } }
            assertNull(onEid.event)

            // Dismissing an invitation before its reminder runs must also block subsequent joins.
            val dismissed = eidEvent.copy(id = "spring-dismissed",
                starts = at("2027-04-05", 0).toEpochMilli(), ends = at("2027-04-10", 0).toEpochMilli())
            db.revivalDao().seed(listOf(dismissed))
            val openingDay = during("2027-04-05")
            openingDay.dismiss(dismissed.id)
            assertFalse(openingDay.claimStartAlert(dismissed.id))
            openingDay.join(dismissed.id)
            val record = db.revivalDao().all().single { it.id == dismissed.id }
            assertTrue(record.dismissed)
            assertFalse(record.notified)
            assertNull(record.joined)
        } finally {
            prefs.clearAll()
            db.close()
        }
    }
}
