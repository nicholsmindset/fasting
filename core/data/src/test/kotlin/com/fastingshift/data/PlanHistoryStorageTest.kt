package com.fastingshift.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.backup.SettingsRecord
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.WeekChoice
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlanHistoryStorageTest {
    @Test fun recordsSurviveRestoreAndLegacyBackupsResetTheDenominator() = runBlocking {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        val monday = LocalDate.of(2026, 11, 16)
        val wed = monday.plusDays(2)
        val nextWeek = monday.plusWeeks(1)
        settings.restore(SettingsRecord(onboardingComplete = true))
        settings.setOnboardedAt(wed.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(wed, settings.current().planHistory.trackedFrom)

        settings.recordSchedule(wed.plusDays(1), 1)
        settings.setNextWeekChoice(nextWeek, WeekChoice.BREAK, wed)
        val record = settings.toRecord()
        assertEquals(mapOf(wed.toString() to PlanHistory.EVERY_DAY, wed.plusDays(1).toString() to 1), record.planRevisions)
        settings.setNextWeekChoice(nextWeek, WeekChoice.KEEP, nextWeek.plusDays(2))
        assertEquals(WeekChoice.BREAK, settings.current().planHistory.choices[nextWeek])
        settings.setNextWeekChoice(nextWeek, WeekChoice.KEEP, wed)
        assertTrue(settings.current().planHistory.isPlanned(nextWeek))

        settings.restore(record)
        val restored = settings.current().planHistory
        assertFalse(restored.isPlanned(monday))
        assertTrue(restored.isPlanned(wed))
        assertTrue(restored.isBreak(nextWeek))
        assertTrue(restored.isPlanned(nextWeek.plusWeeks(1)))

        settings.restore(SettingsRecord(onboardingComplete = true))
        assertNull(settings.current().planHistory.trackedFrom)
        assertTrue(settings.current().planHistory.choices.isEmpty())
        settings.recordSchedule(nextWeek, PlanHistory.EVERY_DAY)
        assertFalse(settings.current().planHistory.isPlanned(wed))
    }
}
