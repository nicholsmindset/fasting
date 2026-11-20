package com.fastingshift.app

import com.fastingshift.app.alarm.AlarmGateway
import com.fastingshift.app.alarm.AlarmScheduler
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.reminders.ReminderSettings
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AlarmSchedulerTest {
    private class FakeGateway(var exact: Boolean) : AlarmGateway {
        val scheduled = mutableMapOf<Int, Pair<Reminder, Duration?>>()
        override fun canScheduleExact() = exact
        override fun setExact(reminder: Reminder) { scheduled[reminder.requestCode] = reminder to null }
        override fun setWindowed(reminder: Reminder, window: Duration) { scheduled[reminder.requestCode] = reminder to window }
        override fun cancel(requestCode: Int) { scheduled.remove(requestCode) }
    }

    private val t0 = Instant.parse("2026-11-20T12:00:00Z")
    private val fast = Fast(id = 1, startAt = t0, targetEndAt = t0.plus(Duration.ofHours(36)), protocolId = "duration_36")

    @Test fun exactWhenGranted() {
        val gw = FakeGateway(exact = true)
        val plan = ReminderPlanner.planForActiveFast(fast, ReminderSettings(), t0)
        assertTrue(AlarmScheduler(gw).apply(plan))
        assertEquals(plan.size, gw.scheduled.size)
        assertTrue(gw.scheduled.values.all { it.second == null })
    }

    @Test fun windowedFallbackWhenDenied() {
        val gw = FakeGateway(exact = false)
        assertFalse(AlarmScheduler(gw).apply(ReminderPlanner.planForActiveFast(fast, ReminderSettings(), t0)))
        assertTrue(gw.scheduled.values.all { it.second == Duration.ofMinutes(15) })
    }

    @Test fun rescheduleReplacesStaleAlarms() {
        val gw = FakeGateway(exact = true)
        val scheduler = AlarmScheduler(gw)
        scheduler.apply(ReminderPlanner.planForActiveFast(fast, ReminderSettings(), t0))
        // Fast ended: empty plan clears everything.
        scheduler.apply(emptyList())
        assertTrue(gw.scheduled.isEmpty())
    }

    @Test fun rescheduleAfterRebootKeepsOnlyFutureAlarms() {
        val gw = FakeGateway(exact = true)
        val later = t0.plus(Duration.ofHours(20))
        AlarmScheduler(gw).apply(ReminderPlanner.planForActiveFast(fast, ReminderSettings(), later))
        assertTrue(gw.scheduled.values.all { it.first.triggerAt.isAfter(later) })
    }
}
