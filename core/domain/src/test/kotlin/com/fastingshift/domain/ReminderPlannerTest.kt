package com.fastingshift.domain

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.reminders.ReminderSettings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlannerTest {
    private val start = Instant.parse("2026-11-20T12:00:00Z")
    private fun fast(h: Long) = Fast(id = 3, startAt = start, targetEndAt = start.plus(hours(h)), protocolId = "p")

    @Test fun `16h fast plans end, milestones and forgot nudge`() {
        val plan = ReminderPlanner.planForActiveFast(fast(16), ReminderSettings(milestones = listOf(hours(12), hours(20))), start)
        val nonCheckIns = plan.filter { it.kind != ReminderKind.SYMPTOM_CHECK_IN }
        assertEquals(listOf(ReminderKind.MILESTONE, ReminderKind.FAST_END, ReminderKind.FORGOT_TO_END), nonCheckIns.map { it.kind })
        assertEquals(start.plus(hours(16)), nonCheckIns[1].triggerAt)
        assertEquals(start.plus(hours(20)), nonCheckIns[2].triggerAt)
        // A 16h fast only gets safety check-ins if it is still running at 24h.
        val checkIns = plan.filter { it.kind == ReminderKind.SYMPTOM_CHECK_IN }
        assertTrue(checkIns.all { java.time.Duration.between(start, it.triggerAt) >= hours(24) })
    }

    @Test fun `extended fast always gets 8-hourly check-ins`() {
        val plan = ReminderPlanner.planForActiveFast(fast(36), ReminderSettings(fastEndEnabled = false, forgotToEndAfter = null), start)
        val checkIns = plan.filter { it.kind == ReminderKind.SYMPTOM_CHECK_IN }
        // Every 8h from the start, and the next few beyond the planned end in case it keeps going.
        assertEquals(listOf(8L, 16L, 24L, 32L, 40L, 48L, 56L), checkIns.map { java.time.Duration.between(start, it.triggerAt).toHours() })
        assertEquals(checkIns.size, checkIns.map { it.requestCode }.toSet().size)
    }

    @Test fun `check-ins roll on while an overrunning fast continues`() {
        // 16h fast still running at 60h: check-ins keep coming every 8h ahead of now.
        val now = start.plus(hours(60))
        val plan = ReminderPlanner.planForActiveFast(fast(16), ReminderSettings(), now)
        val next = plan.filter { it.kind == ReminderKind.SYMPTOM_CHECK_IN }.map { java.time.Duration.between(start, it.triggerAt).toHours() }
        assertEquals(listOf(64L, 72L, 80L), next)
    }

    @Test fun `only future reminders after reschedule`() {
        val plan = ReminderPlanner.planForActiveFast(fast(16), ReminderSettings(), start.plus(hours(17)))
        assertEquals(listOf(ReminderKind.FORGOT_TO_END), plan.filter { it.kind != ReminderKind.SYMPTOM_CHECK_IN }.map { it.kind })
        assertTrue(plan.all { it.triggerAt.isAfter(start.plus(hours(17))) })
    }

    @Test fun `ended fast plans nothing`() {
        assertEquals(emptyList(), ReminderPlanner.planForActiveFast(fast(16).end(start.plus(hours(2))), ReminderSettings(), start))
    }

    @Test fun `request codes are unique across kinds`() {
        val plan = ReminderPlanner.planForActiveFast(fast(72), ReminderSettings(milestones = (1L..70L).map(::hours)), start)
        assertEquals(plan.size, plan.map(Reminder::requestCode).toSet().size)
    }

    @Test fun `next start after eating window`() {
        val ended = fast(16).end(start.plus(hours(16)))
        val r = ReminderPlanner.planNextStart(ended, eatHours = 8, ReminderSettings(), start.plus(hours(17)))!!
        assertEquals(start.plus(hours(24)), r.triggerAt)
        assertNull(ReminderPlanner.planNextStart(ended, eatHours = null, ReminderSettings(), start))
    }
}
