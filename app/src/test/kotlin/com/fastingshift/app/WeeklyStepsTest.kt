package com.fastingshift.app

import com.fastingshift.app.health.WeeklySteps
import com.fastingshift.app.health.readWeeklySteps
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeeklyStepsTest {
    private val zone = ZoneId.of("America/New_York")
    private val week = LocalDate.of(2026, 3, 2)
    private val now = Instant.parse("2026-03-06T19:15:00Z")

    @Test fun readUsesTheRecapWeekAndOnlyElapsedTime() = runTest {
        val value = readWeeklySteps(week, now, zone, { true }) { from, until ->
            assertEquals(Instant.parse("2026-03-02T05:00:00Z"), from)
            assertEquals(now, until)
            5012L
        }
        assertEquals(WeeklySteps.Total(5012), value)
        readWeeklySteps(week, now.plus(Duration.ofDays(10)), zone, { true }) { from, until ->
            // A civil week containing a spring clock change is 167 hours.
            assertEquals(167L, Duration.between(from, until).toHours())
            37L
        }
    }

    @Test fun aFutureWeekAndAnEmptyIntervalNeverQueryTheProvider() = runTest {
        val midnight = week.atStartOfDay(zone).toInstant()
        for (instant in listOf(midnight, midnight.minusSeconds(1))) {
            assertEquals(WeeklySteps.NoData, readWeeklySteps(week, instant, zone, { true }) { _, _ ->
                error("An empty interval must skip the provider")
            })
        }
    }

    @Test fun missingDataIsDifferentFromMeasuredZero() = runTest {
        assertEquals(WeeklySteps.NoData, readWeeklySteps(week, now, zone, { true }) { _, _ -> null })
        assertEquals(WeeklySteps.Total(0), readWeeklySteps(week, now, zone, { true }) { _, _ -> 0L })
    }

    @Test fun deniedPermissionAndProviderFailureKeepDistinctStates() = runTest {
        assertEquals(WeeklySteps.PermissionNeeded, readWeeklySteps(week, now, zone, { false }) { _, _ ->
            error("Denied reads must not request step data")
        })
        assertEquals(WeeklySteps.PermissionNeeded, readWeeklySteps(week, now, zone, { true }) { _, _ ->
            throw SecurityException("Revoked between check and read")
        })
        assertEquals(WeeklySteps.ReadFailed, readWeeklySteps(week, now, zone, { true }) { _, _ -> throw IOException() })
        assertFailsWith<CancellationException> {
            readWeeklySteps(week, now, zone, { true }) { _, _ -> throw CancellationException() }
        }
    }
}
