package com.fastingshift.app

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.history.CalendarCard
import com.fastingshift.app.ui.history.HistoryState
import com.fastingshift.app.ui.history.WeekRow
import com.fastingshift.app.ui.ramadan.MuslimText
import com.fastingshift.app.ui.ramadan.clockTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class UiDateTimeLocaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun datesAndPrayerTimesFollowTheCurrentUiLanguage() {
        val malay = Locale.forLanguageTag("ms-MY")
        val english = Locale.forLanguageTag("en-SG")
        val active = mutableStateOf(malay)
        val day = LocalDate.of(2026, 8, 3)
        val zone = ZoneId.of("Asia/Singapore")
        val evening = day.atTime(18, 53).atZone(zone)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocale(active.value) }
            CompositionLocalProvider(LocalConfiguration provides config) {
                Text(MuslimText.gregorianFull(day))
                Text(MuslimText.gregorian(day))
                Text(clockTime(evening.toInstant(), zone))
            }
        }

        fun assertLocale(locale: Locale) {
            for (style in listOf(FormatStyle.FULL, FormatStyle.MEDIUM)) {
                val date = DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(day)
                compose.onNodeWithText(date).assertExists()
            }
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(evening)
            compose.onNodeWithText(time).assertExists()
        }
        assertLocale(malay)
        compose.runOnIdle { active.value = english }
        assertLocale(english)
    }

    @Test fun calendarMonthAndTalkBackDaySwitchLanguagesTogether() {
        val english = Locale.forLanguageTag("en-SG")
        val malay = Locale.forLanguageTag("ms-MY")
        val active = mutableStateOf(english)
        val day = LocalDate.of(2026, 8, 3)
        val month = YearMonth.from(day)
        val history = HistoryState(today = day, month = month, weeks = listOf(WeekRow(day, emptyList())))
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocale(active.value) }
            CompositionLocalProvider(LocalConfiguration provides config) {
                CalendarCard(history, onShift = {}, onEditFast = {})
            }
        }

        fun assertLocale(locale: Locale) {
            compose.onNodeWithText("${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}").assertExists()
            val label = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(day)
            compose.onNodeWithContentDescription(label).assertExists()
        }
        assertLocale(english)
        compose.runOnIdle { active.value = malay }
        assertLocale(malay)
    }
}
