package com.fastingshift.app.ui.firstweek

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.theme.pickerDialogTheme
import com.fastingshift.domain.model.Protocol
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
internal fun FirstWindowTimes(start: ZonedDateTime, protocol: Protocol) {
    val format = rememberWeekFormat().dateTime
    Text(stringResource(R.string.first_plan_starts_at, format.format(start)),
        modifier = Modifier.testTag("first-plan-start"), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.first_plan_finishes_at, format.format(start.plusHours(protocol.fastHours.toLong()))),
        modifier = Modifier.testTag("first-plan-finish"), style = MaterialTheme.typography.bodyLarge)
}

/** Choosing a time explicitly anchors the next local occurrence. An open screen never reschedules it. */
@Composable
fun FirstPlanTimeFields(
    now: Instant,
    protocol: Protocol,
    start: ZonedDateTime?,
    onPick: (ZonedDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val latestNow by rememberUpdatedState(now)
    val latestPick by rememberUpdatedState(onPick)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (start != null) FirstWindowTimes(start, protocol)
        SecondaryButton(stringResource(if (start == null) R.string.first_plan_pick_clock else R.string.first_plan_change_clock),
            onClick = { picking = true }, modifier = Modifier.testTag("first-plan-clock-picker"))
    }
    if (picking) {
        val context = LocalContext.current
        val theme = pickerDialogTheme()
        DisposableEffect(context, theme, zone) {
            val initial = start?.toLocalTime() ?: latestNow.atZone(zone).toLocalTime()
            val dialog = TimePickerDialog(context, theme, { _, h, m ->
                latestPick(FirstWeekRules.nextStartAt(latestNow, LocalTime.of(h, m), zone))
            }, initial.hour, initial.minute, DateFormat.is24HourFormat(context))
            dialog.setOnDismissListener { picking = false }
            dialog.show()
            onDispose {
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
    }
}
