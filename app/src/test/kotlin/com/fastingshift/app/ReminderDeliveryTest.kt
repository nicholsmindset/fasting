package com.fastingshift.app

import com.fastingshift.app.alarm.eligibleToStart
import com.fastingshift.app.alarm.matchesBroadcast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReminderDeliveryTest {
    private val at = Instant.parse("2026-09-01T16:00:01.123456Z")
    private val answers = ScreeningAnswers(32, false, false, false, emptySet(), 170.0, 70.0)

    @Test fun eligibilityChangesWithAcknowledgementOnSameGate() {
        val soft = ScreeningRecord(at, answers, GateResult.SOFT, clinicianAcknowledged = false)
        assertFalse(eligibleToStart(null))
        assertFalse(eligibleToStart(soft))
        assertTrue(eligibleToStart(soft.copy(clinicianAcknowledged = true)))
        assertFalse(eligibleToStart(soft.copy(gateResult = GateResult.BLOCK, clinicianAcknowledged = true)))
    }

    @Test fun matchingRequiresSameCurrentPlanEventAtAndroidPrecision() {
        val planned = Reminder(ReminderKind.SYMPTOM_CHECK_IN, at, fastId = 42, index = 3)
        val fired = planned.copy(triggerAt = Instant.ofEpochMilli(at.toEpochMilli()))
        assertTrue(matchesBroadcast(planned, fired))
        assertFalse(matchesBroadcast(planned.copy(kind = ReminderKind.COACHING), fired))
        assertFalse(matchesBroadcast(planned.copy(index = 4), fired))
        assertFalse(matchesBroadcast(planned.copy(fastId = 43), fired))
        assertFalse(matchesBroadcast(planned.copy(triggerAt = at.plusMillis(1)), fired))
    }
}
