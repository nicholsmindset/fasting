package com.fastingshift.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.components.WeekFormat
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], manifest = Config.NONE)
class WeekFormatTest {
    @Test fun eveningTimesIncludeMinutesAndRespectBothClockSettings() {
        val locale = Locale.forLanguageTag("en-SG")
        val twelveHour = WeekFormat(locale, use24h = false).time
        val twentyFourHour = WeekFormat(locale, use24h = true).time
        val evening = LocalTime.of(20, 0)
        val moved = LocalTime.of(21, 30)

        fun normalized(value: String) = value.uppercase(Locale.ROOT).replace(Regex("[\\p{Z}\\s]+"), " ")
        assertEquals("8:00 PM", normalized(twelveHour.format(evening)))
        assertEquals("9:30 PM", normalized(twelveHour.format(moved)))
        assertEquals("20:00", twentyFourHour.format(evening))
        assertEquals("21:30", twentyFourHour.format(moved))
    }
}
