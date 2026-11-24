package com.fastingshift.app.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.util.Locale

/** Dates follow the app language. The clock follows the user's explicit 12/24-hour setting. */
internal class WeekFormat(val locale: Locale, use24h: Boolean) {
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEE MMM d"), locale)
    val time: DateTimeFormatter = run {
        val skeleton = if (use24h) "Hm" else "hm"
        val localized = DateFormat.getBestDateTimePattern(locale, skeleton)
        // Some runtimes return the skeleton unchanged. "hm" as a pattern drops separators and the day period.
        val pattern = if (localized != skeleton) localized else if (use24h) "HH:mm" else "h:mm a"
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    val dateTime: DateTimeFormatter = DateTimeFormatterBuilder().append(date).appendLiteral(' ').append(time).toFormatter(locale)
    fun days(days: Set<DayOfWeek>) = days.sorted().joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

@Composable
internal fun rememberWeekFormat(): WeekFormat {
    val locale = LocalConfiguration.current.locales[0]
    val use24h = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, use24h) { WeekFormat(locale, use24h) }
}
