package com.fastingshift.app.alarm

import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.ScheduleCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun eligibleSchedules(schedules: List<Schedule>, maxDuration: Duration?): List<Schedule> {
    if (maxDuration == null) return emptyList()
    return schedules.filter { schedule ->
        val protocol = Presets.byId(schedule.protocolId)
        protocol != null && Duration.ofHours(protocol.fastHours.toLong()) <= maxDuration
    }
}

/** Shared by the Android alarm planner and its in-app preview while the timer is idle. */
internal fun idleReminders(
    now: Instant,
    zone: ZoneId,
    appSettings: AppSettings,
    schedules: List<Schedule>,
    lastFast: Fast?,
    firstEvening: Pair<Long, LocalDate>? = null,
): List<Reminder> {
    val settings = appSettings.reminders
    val history = appSettings.planHistory
    val next = ScheduleCalculator.nextStart(schedules, zone, now, history.breakWeeks, history.dayOverrides, firstEvening)
    val recurring = schedules.any { it.enabled && it.days.isNotEmpty() && it.id != firstEvening?.first }

    // Without a weekly routine, a completed eating window still suggests the next start.
    // Calculate the original end even when it has passed: today's plan may have moved later.
    val ended = lastFast?.endedAt
    val window = if (lastFast != null && ended != null) {
        val hours = Presets.byId(lastFast.protocolId)?.eatHours ?: appSettings.eatingWindowHours
        ReminderPlanner.planNextStart(lastFast, hours, settings.copy(fastStartEnabled = true), ended)
    } else null
    val windowEnd = window?.let {
        val day = it.triggerAt.atZone(zone).toLocalDate()
        val starts = ScheduleCalculator.startsOn(schedules, day, zone, history.breakWeeks, history.dayOverrides, firstEvening)
        when {
            history.isBreak(day) -> null
            recurring || starts.isNotEmpty() -> starts.firstOrNull { occurrence -> occurrence.second.isAfter(ended) }?.second
            day in history.dayOverrides -> null
            else -> it.triggerAt
        }
    }
    val scheduled = next?.let { Reminder(ReminderKind.FAST_START, it.second, fastId = 0) }
    val fallback = if (!recurring && window != null && windowEnd == window.triggerAt && window.triggerAt.isAfter(now)) window else null
    val start = if (settings.fastStartEnabled) listOfNotNull(scheduled, fallback).minByOrNull { it.triggerAt } else null
    val closingAt = windowEnd?.minus(ReminderPlanner.WINDOW_CLOSING_BEFORE)
    val closing = if (settings.windowClosingEnabled && closingAt != null && closingAt.isAfter(now) && window != null) {
        val date = closingAt.atZone(zone).toLocalDate()
        if (history.isBreak(date) || history.dayOverrides[date] == DayOverride()) null
        else Reminder(ReminderKind.EATING_WINDOW_CLOSING, closingAt, window.fastId)
    } else null
    return listOfNotNull(start, closing).sortedBy { it.triggerAt }
}
