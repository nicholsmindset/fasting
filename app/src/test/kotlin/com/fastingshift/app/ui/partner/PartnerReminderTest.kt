package com.fastingshift.app.ui.partner

import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PartnerReminderTest {
    @Test fun pastTimeMovesToTomorrow() {
        val now = ZonedDateTime.parse("2026-11-26T19:00:00+08:00[Asia/Singapore]")
        assertEquals(now.toLocalDate().plusDays(1).atTime(18, 0).atZone(now.zone),
            nextPartnerReminder(now, LocalTime.of(18, 0)))
    }

    @Test fun skippedClockTimeMovesPastDstGap() {
        val now = ZonedDateTime.parse("2026-03-08T00:20:00-05:00[America/New_York]")
        assertEquals(LocalTime.of(3, 30), nextPartnerReminder(now, LocalTime.of(2, 30)).toLocalTime())
    }
}
