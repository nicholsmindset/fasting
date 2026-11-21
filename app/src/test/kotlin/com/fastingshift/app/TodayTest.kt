package com.fastingshift.app

import com.fastingshift.app.ui.timer.EatingWindow
import com.fastingshift.app.ui.timer.NextFast
import com.fastingshift.app.ui.timer.Today
import com.fastingshift.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import org.junit.Test

/** The Today card's choices: which water goal applies, and when the next fast is. */
class TodayTest {
    private val zone = ZoneId.of("Europe/London")
    // Saturday 21 Nov 2026, 10:00.
    private val now = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, zone).toInstant()

    @Test fun anExplicitGoalWinsOverTheWeightDefault() {
        assertEquals(3_100, Today.waterGoalMl(3_100, 80.0))
    }

    @Test fun withoutAGoalTheDefaultComesFromTheLatestWeight() {
        assertEquals(2_800, Today.waterGoalMl(null, 80.0))
        assertEquals(2_000, Today.waterGoalMl(null, null))
    }

    @Test fun fastingNowBeatsEverythingElse() {
        val window = EatingWindow(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(7)))
        assertEquals(NextFast.FastingNow, Today.nextFast(true, window, emptyList(), zone, now))
    }

    @Test fun anOpenEatingWindowEndsWhenTheNextFastStarts() {
        val end = now.plus(Duration.ofHours(5))
        assertEquals(NextFast.At(end), Today.nextFast(false, EatingWindow(now.minus(Duration.ofHours(3)), end), emptyList(), zone, now))
    }

    @Test fun otherwiseTheNextScheduledStart() {
        val schedule = Schedule(protocolId = "duration_24", days = setOf(DayOfWeek.MONDAY), startTime = LocalTime.of(20, 0))
        val monday = ZonedDateTime.of(2026, 11, 23, 20, 0, 0, 0, zone).toInstant()
        assertEquals(NextFast.At(monday), Today.nextFast(false, null, listOf(schedule), zone, now))
    }

    @Test fun nothingPlannedWithoutWindowOrSchedule() {
        assertEquals(NextFast.None, Today.nextFast(false, null, emptyList(), zone, now))
    }
}
