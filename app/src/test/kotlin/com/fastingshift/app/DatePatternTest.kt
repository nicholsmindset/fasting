package com.fastingshift.app

import com.fastingshift.app.ui.timer.fullDateWithoutYearPattern
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** The Timer header date never shows a year, or a stray year marker, across app and device locales. */
class DatePatternTest {
    private val day = LocalDate.of(2026, 11, 22)
    private fun format(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return DateTimeFormatter.ofPattern(fullDateWithoutYearPattern(locale), locale).format(day)
    }

    @Test fun noYearInAppAndDeviceDateFormats() {
        for (tag in listOf("en-US", "en-GB", "en-SG", "ms-MY", "in-ID", "id-ID", "zh-CN", "zh-SG", "ko-KR", "ja-JP")) {
            val text = format(tag)
            assertFalse("2026" in text, "$tag: $text")
            assertFalse(text.startsWith("年") || "年11" in text || text.startsWith("년"), "$tag: $text")
            assertTrue("22" in text, "$tag: $text")
        }
        assertEquals("Sunday, November 22", format("en-US"))
        assertEquals("11月22日星期日", format("zh-CN"))
    }
}
