package com.fastingshift.app

import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.app.alarm.idleReminders
import com.fastingshift.app.alarm.matchesBroadcast
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderSettings
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.PlanHistory
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WeekReminderPlanTest {
    private val thursday = LocalDate.of(2026, 11, 26)
    private val now = Instant.parse("2026-11-26T13:00:00Z")
    private val weekdays = Schedule(1, "window_16_8", DayOfWeek.entries.take(5).toSet(), LocalTime.of(20, 0))
    private val settings = AppSettings(reminders = ReminderSettings(windowClosingEnabled = true))
    private val start = Instant.parse("2026-11-25T20:00:00Z")
    private val previous = Fast(12, start, now.minusSeconds(3600), protocolId = "window_16_8").end(now.minusSeconds(3600))

    private fun plan(
        prefs: AppSettings = settings,
        at: Instant = now,
        schedules: List<Schedule> = listOf(weekdays),
        zone: ZoneId = ZoneOffset.UTC,
    ) = idleReminders(at, zone, prefs, schedules, previous)

    private fun override(time: LocalTime?) = settings.copy(
        planHistory = PlanHistory(dayOverrides = mapOf(thursday to DayOverride(time))),
    )

    @Test fun laterDinnerMovesBothNotificationsAndInvalidatesOldQueuedBroadcasts() {
        val later = override(LocalTime.of(22, 0))
        val original = plan()
        val moved = plan(later)
        assertEquals(listOf(Instant.parse("2026-11-26T21:30:00Z"), Instant.parse("2026-11-26T22:00:00Z")), moved.map { it.triggerAt })
        original.forEach { queued ->
            assertFalse(plan(later, queued.triggerAt.minusMillis(1)).any { matchesBroadcast(it, queued) })
        }
        val afterOriginalWindow = plan(later, Instant.parse("2026-11-26T20:10:00Z"))
        assertEquals(moved, afterOriginalWindow)
        assertEquals(original.map { it.requestCode }, moved.map { it.requestCode })
    }

    @Test fun skipSuppressesWindowNudgeAndPreservesFriday() {
        val reminders = plan(override(null))
        assertEquals(ReminderKind.FAST_START, reminders.single().kind)
        assertEquals(Instant.parse("2026-11-27T20:00:00Z"), reminders.single().triggerAt)
        assertEquals(Instant.parse("2026-11-30T20:00:00Z"),
            plan(at = Instant.parse("2026-11-27T21:00:00Z")).single().triggerAt)
    }

    @Test fun midnightClosingNudgeCannotFireOnASkippedDate() {
        val earlyStart = Instant.parse("2026-11-26T00:10:00Z")
        val last = Fast(13, earlyStart, Instant.parse("2026-11-26T16:10:00Z"), protocolId = "window_16_8").end(
            Instant.parse("2026-11-26T16:10:00Z"),
        )
        val next = idleReminders(last.endedAt!!, ZoneOffset.UTC, override(null),
            listOf(weekdays.copy(startTime = LocalTime.of(0, 10))), last)
        assertEquals(ReminderKind.FAST_START, next.single().kind)
        assertEquals(Instant.parse("2026-11-27T00:10:00Z"), next.single().triggerAt)
    }

    @Test fun disabledStartNeverArmsEvenWhenMovedAndClosingCanBeDisabledSeparately() {
        val later = override(LocalTime.of(22, 0))
        assertEquals(ReminderKind.EATING_WINDOW_CLOSING,
            plan(later.copy(reminders = ReminderSettings(windowClosingEnabled = true, fastStartEnabled = false))).single().kind)
        assertTrue(plan(later.copy(reminders = ReminderSettings(fastStartEnabled = false))).isEmpty())
    }

    @Test fun zoneChangeUsesLocalReminderTimeAndNoSchedulePreservesOriginalWindow() {
        val later = override(LocalTime.of(22, 0))
        assertEquals(Instant.parse("2026-11-26T14:00:00Z"),
            plan(later, zone = ZoneId.of("Asia/Singapore")).single { it.kind == ReminderKind.FAST_START }.triggerAt)
        val fallback = plan(schedules = emptyList()).single { it.kind == ReminderKind.FAST_START }
        assertEquals(previous.id, fallback.fastId)
        assertEquals(Instant.parse("2026-11-26T20:00:00Z"), fallback.triggerAt)
    }

    @Test fun schedulesWithUnknownOrRestrictedDurationsCannotGenerateStartReminders() {
        val saved = listOf(weekdays, weekdays.copy(protocolId = "duration_72"), weekdays.copy(protocolId = "unknown"))
        assertEquals(listOf(weekdays), eligibleSchedules(saved, Duration.ofHours(24)))
        assertTrue(eligibleSchedules(saved, null).isEmpty())
    }
}
