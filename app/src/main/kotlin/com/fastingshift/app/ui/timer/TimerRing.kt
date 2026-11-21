package com.fastingshift.app.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.timer.TimerDisplayMode
import com.fastingshift.domain.timer.TimerState
import com.fastingshift.domain.timer.toClockString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the ring shows in each [TimerDisplayMode]: a small label, the big value and a sub-line. */
private data class RingText(val label: String, val big: String, val sub: String?)

@Composable
private fun ringText(mode: TimerDisplayMode, fast: Fast, timer: TimerState, now: Instant): RingText = when (mode) {
    TimerDisplayMode.ELAPSED -> RingText(
        label = stringResource(R.string.timer_mode_elapsed),
        big = timer.elapsed.toClockString(),
        sub = if (timer.isTargetReached) stringResource(R.string.timer_goal_reached, timer.pastTarget.toClockString())
        else stringResource(R.string.timer_remaining, timer.remaining.toClockString()),
    )
    TimerDisplayMode.REMAINING -> if (timer.isTargetReached) {
        RingText(stringResource(R.string.timer_mode_past), "+" + timer.pastTarget.toClockString(), stringResource(R.string.timer_goal_of, fast.plannedDuration.hoursMinutes()))
    } else {
        RingText(stringResource(R.string.timer_mode_remaining), timer.remaining.toClockString(), stringResource(R.string.timer_goal_of, fast.plannedDuration.hoursMinutes()))
    }
    TimerDisplayMode.BREAK_AT -> {
        val zone = ZoneId.systemDefault()
        val target = fast.targetEndAt.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val day = if (target.toLocalDate() != today) dayFormat().format(target) else null
        val sub = when {
            timer.isTargetReached -> stringResource(R.string.timer_goal_reached, timer.pastTarget.hoursMinutes())
            day != null -> stringResource(R.string.timer_break_day_in, day, timer.remaining.hoursMinutes())
            else -> stringResource(R.string.timer_break_in, timer.remaining.hoursMinutes())
        }
        RingText(stringResource(R.string.timer_mode_break_at), DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(target), sub)
    }
}

/** "Sat, Nov 22" in the user's locale. */
private fun dayFormat(): DateTimeFormatter {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale)
}

/**
 * The big timer. Tapping it cycles what it shows (time fasted, time left, when to break the
 * fast), like a clock face you can flip. TalkBack reads it as one node with the current view.
 */
@Composable
fun TimerRing(
    fast: Fast,
    timer: TimerState,
    mode: TimerDisplayMode,
    now: Instant,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = ringText(mode, fast, timer, now)
    val base = stringResource(R.string.a11y_timer, timer.elapsed.hoursMinutes(), fast.plannedDuration.hoursMinutes(), (timer.progress * 100).toInt())
    val modeDescription = when (mode) {
        TimerDisplayMode.ELAPSED -> stringResource(R.string.a11y_mode_elapsed)
        TimerDisplayMode.REMAINING -> if (timer.isTargetReached) stringResource(R.string.a11y_mode_past, timer.pastTarget.hoursMinutes())
        else stringResource(R.string.a11y_mode_remaining, timer.remaining.hoursMinutes())
        TimerDisplayMode.BREAK_AT -> stringResource(R.string.a11y_mode_break_at, text.big)
    }
    val description = "$base. $modeDescription"
    val switchLabel = stringResource(R.string.a11y_timer_switch)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CircleShape)
                .clearAndSetSemantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(timer.progress, 0f..1f)
                    role = Role.Button
                    onClick(label = switchLabel) { onToggle(); true }
                }
                .clickable(onClickLabel = switchLabel, role = Role.Button, onClick = onToggle),
        ) {
            CircularProgressIndicator(
                progress = { timer.progress },
                modifier = Modifier.size(260.dp),
                strokeWidth = 14.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 36.dp)) {
                Text(text.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(text.big, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                text.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ModeDots(mode)
        Text(stringResource(R.string.timer_tap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModeDots(mode: TimerDisplayMode) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        TimerDisplayMode.entries.forEach { m ->
            Box(
                Modifier.size(if (m == mode) 8.dp else 6.dp).clip(CircleShape).background(
                    if (m == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}
