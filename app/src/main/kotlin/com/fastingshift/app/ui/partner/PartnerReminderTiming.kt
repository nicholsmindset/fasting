package com.fastingshift.app.ui.partner

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal data class PartnerQuietHours(val start: LocalTime, val end: LocalTime) {
    fun contains(time: LocalTime): Boolean =
        if (start < end) time >= start && time < end else time >= start || time < end
}

internal fun nextPartnerReminder(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val next = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (next.isAfter(now)) next else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

internal fun partnerReminderTarget(
    now: ZonedDateTime, time: LocalTime, firstDay: LocalDate, lastDay: LocalDate,
    plannedDays: Set<String>, marked: Set<String>, quiet: PartnerQuietHours?,
): ZonedDateTime? {
    if (marked.size >= 3 || quiet?.contains(time) == true) return null
    var day = maxOf(nextPartnerReminder(now, time).toLocalDate(), firstDay)
    while (day <= lastDay) {
        val candidate = day.atTime(time).atZone(now.zone)
        val key = day.toString()
        if ((plannedDays.isEmpty() || key in plannedDays) && key !in marked &&
            candidate.toLocalDate() == day && quiet?.contains(candidate.toLocalTime()) != true) return candidate
        day = day.plusDays(1)
    }
    return null
}

internal fun canDeliverPartnerReminder(due: ZonedDateTime, now: ZonedDateTime, quiet: PartnerQuietHours?): Boolean {
    return !now.isBefore(due) && !now.isAfter(due.plus(Duration.ofHours(1))) &&
        now.toLocalDate() == due.toLocalDate() && quiet?.contains(now.toLocalTime()) != true
}
