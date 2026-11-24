package com.fastingshift.app.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.ProgressRing
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.onboarding.SelectGroupRow
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.timer.TimerDisplayMode
import com.fastingshift.domain.timer.TimerState
import com.fastingshift.domain.timer.toClockString
import java.text.NumberFormat
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
    onSetMode: (TimerDisplayMode) -> Unit,
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
    val haptics = rememberHaptics()
    var choosingView by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { choosingView = true }) { Text(stringResource(R.string.timer_choose_view)) }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(timer.progress, 0f..1f)
                    role = Role.Button
                    onClick(label = switchLabel) { onSetMode(mode.next()); true }
                }
                .clickable(onClickLabel = switchLabel, role = Role.Button) { haptics.tick(); onSetMode(mode.next()) },
        ) {
            if (maxWidth >= 315.dp && LocalDensity.current.fontScale <= 1.4f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimerValue(text, Modifier.weight(1f))
                    GoalDial(timer.progress)
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimerValue(text, Modifier.fillMaxWidth())
                    LinearProgressIndicator(progress = { timer.progress }, modifier = Modifier.fillMaxWidth().height(5.dp))
                }
            }
        }
    }
    if (choosingView) {
        GroupedSheet(stringResource(R.string.timer_choose_view), onDismiss = { choosingView = false }) {
            Column(Modifier.selectableGroup()) {
                TimerDisplayMode.entries.forEach { choice ->
                    val title = when (choice) {
                        TimerDisplayMode.ELAPSED -> R.string.timer_view_elapsed
                        TimerDisplayMode.REMAINING -> R.string.timer_view_remaining
                        TimerDisplayMode.BREAK_AT -> R.string.timer_view_end
                    }
                    SelectGroupRow(stringResource(title), selected = mode == choice, onClick = {
                        onSetMode(choice)
                        choosingView = false
                    })
                }
            }
        }
    }
}

@Composable
private fun TimerValue(text: RingText, modifier: Modifier) {
    val sizes = remember { TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 64.sp, stepSize = 2.sp) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text.big, modifier = Modifier.fillMaxWidth(), maxLines = 1, softWrap = false, autoSize = sizes,
            style = MaterialTheme.typography.displayLarge.merge(TabularNumbers).copy(
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp,
            ),
        )
        text.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun GoalDial(progress: Float) {
    val format = remember(Locale.getDefault()) { NumberFormat.getPercentInstance() }
    val percentage = (progress * 100).toInt()
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        ProgressRing(progress, Modifier.fillMaxSize(), stroke = 5.dp)
        Text(format.format(percentage / 100.0), style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.primary)
    }
}
