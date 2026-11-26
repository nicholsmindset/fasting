package com.fastingshift.app.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

internal sealed interface WeeklySteps {
    data object PermissionNeeded : WeeklySteps
    data object Unavailable : WeeklySteps
    data object ReadFailed : WeeklySteps
    data object NoData : WeeklySteps
    data class Total(val count: Long) : WeeklySteps
}

/** The provider's aggregate deduplicates sources. A missing metric differs from a measured zero. */
internal suspend fun readWeeklySteps(
    weekStart: LocalDate,
    now: Instant,
    zone: ZoneId,
    hasPermission: suspend () -> Boolean,
    aggregate: suspend (Instant, Instant) -> Long?,
): WeeklySteps {
    return try {
        if (!hasPermission()) return WeeklySteps.PermissionNeeded
        val from = weekStart.atStartOfDay(zone).toInstant()
        val until = minOf(now, weekStart.plusWeeks(1).atStartOfDay(zone).toInstant())
        if (until <= from) return WeeklySteps.NoData
        aggregate(from, until)?.let { WeeklySteps.Total(it) } ?: WeeklySteps.NoData
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        WeeklySteps.PermissionNeeded
    } catch (_: Exception) {
        WeeklySteps.ReadFailed
    }
}
