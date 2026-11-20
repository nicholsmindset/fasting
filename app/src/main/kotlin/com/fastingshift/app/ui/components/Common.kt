package com.fastingshift.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "16h 20m" — for summaries; the live timer uses H:MM:SS. */
fun Duration.hoursMinutes(): String {
    val h = toHours()
    val m = toMinutes() % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Formats in the device's current zone, read at call time so zone changes show correctly. */
fun Instant.formatDateTime(): String = dateTime.format(atZone(ZoneId.systemDefault()))
fun Instant.formatDate(): String = date.format(atZone(ZoneId.systemDefault()))
fun Instant.formatTime(): String = time.format(atZone(ZoneId.systemDefault()))
