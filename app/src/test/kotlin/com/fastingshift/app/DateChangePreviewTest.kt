package com.fastingshift.app

import com.fastingshift.app.ui.settings.PlannerState
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.ReminderSettings
import com.fastingshift.domain.schedule.DayOverride
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class DateChangePreviewTest {
    private fun plan(zone: String, now: String): PlannerState =
        PlannerState(
            schedules = listOf(Schedule(1, Presets.W16_8.id, DayOfWeek.entries.toSet(), LocalTime.of(20, 0))),
            settings = AppSettings(reminders = ReminderSettings(fastStartEnabled = true)), maxDuration = Duration.ofHours(24),
            now = Instant.parse(now), zone = ZoneId.of(zone), firstEvening = null, active = false, lastFast = null,
        )

    @Test fun lateThursdayPreviewKeepsFridayAndUsesTheSameAlertAsTheAlarmPlanner() {
        val plan = plan("Asia/Singapore", "2026-11-26T13:40:00Z")
        val day = plan.today
        val moved = plan.preview(day, DayOverride(LocalTime.of(22, 0)))
        assertEquals(Instant.parse("2026-11-26T14:00:00Z"), moved.start)
        assertEquals(Instant.parse("2026-11-27T06:00:00Z"), moved.finish)
        assertEquals(Instant.parse("2026-11-27T12:00:00Z"), moved.previousAlert)
        assertEquals(moved.start, moved.nextAlert)
        assertEquals(Instant.parse("2026-11-27T12:00:00Z"), plan.starts(day.plusDays(1)).single().second)
        val skip = plan.preview(day, DayOverride())
        assertNull(skip.start)
        assertNull(skip.finish)
        assertEquals(plan.starts(day.plusDays(1)).single().second, skip.nextAlert)
        assertNull(plan.settings.planHistory.dayOverrides[day])
    }

    @Test fun whenAnotherRoutineStartsFirstTheNextAlertStaysAtItsOwnTime() {
        val plan = plan("Asia/Singapore", "2026-11-26T13:40:00Z")
        val later = Schedule(2, Presets.W12_12.id, setOf(DayOfWeek.THURSDAY), LocalTime.of(21, 50))
        val both = plan.copy(schedules = plan.schedules + later)
        val preview = both.preview(plan.today, DayOverride(LocalTime.of(22, 0)))
        assertEquals(Instant.parse("2026-11-26T13:50:00Z"), preview.previousAlert)
        assertEquals(preview.previousAlert, preview.nextAlert)
        assertEquals(Instant.parse("2026-11-26T14:00:00Z"), preview.start)
        assertEquals(Instant.parse("2026-11-27T06:00:00Z"), preview.finish)
    }

    @Test fun projectedFinishUsesElapsedHoursAcrossTheSpringClockChange() {
        val plan = plan("America/New_York", "2026-03-08T02:40:00Z")
        val moved = plan.preview(LocalDate.of(2026, 3, 7), DayOverride(LocalTime.of(22, 0)))
        assertEquals(Instant.parse("2026-03-08T03:00:00Z"), moved.start)
        assertEquals(Instant.parse("2026-03-08T19:00:00Z"), moved.finish)
        assertEquals(LocalTime.of(15, 0), moved.finish?.atZone(plan.zone)?.toLocalTime())
    }
}
