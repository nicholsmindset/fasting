package com.fastingshift.app.ui.revival

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.openAppNotificationSettings
import com.fastingshift.app.ui.components.startReminderNotificationsEnabled
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SwitchGroupRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RevivalCard(vm: RevivalViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    RevivalCardContent(state, vm::join, vm::dismiss)
}

@Composable
internal fun RevivalCardContent(state: RevivalState, join: (String) -> Unit, dismiss: (String) -> Unit) {
    val event = state.event ?: return
    var opened by rememberSaveable(event.id) { mutableStateOf(false) }
    val date = Instant.ofEpochMilli(event.ends).atZone(ZoneId.of(event.zone)).toLocalDate().minusDays(1)
    val deadline = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0]).format(date)
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.revival_title), style = MaterialTheme.typography.titleLarge)
            if (event.restored != null) {
                Text(stringResource(R.string.revival_award, event.restoredDays ?: 0))
                TextButton(onClick = { opened = true }) { Text(stringResource(R.string.revival_view)) }
            } else if (event.joined != null) {
                Text(stringResource(R.string.revival_progress, state.marks, event.threshold, deadline))
                LinearProgressIndicator(progress = { state.marks.toFloat() / event.threshold }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { dismiss(event.id) }) { Text(stringResource(R.string.revival_skip)) }
            } else {
                Text(if (state.best > 0) stringResource(R.string.revival_invite, state.best, event.threshold, deadline)
                    else stringResource(R.string.revival_fresh, event.threshold, deadline))
                PrimaryButton(stringResource(R.string.revival_join), { join(event.id) })
                TextButton(onClick = { dismiss(event.id) }) { Text(stringResource(R.string.revival_skip)) }
            }
        }
    }
    if (opened) AlertDialog(onDismissRequest = { opened = false },
        title = { Text(stringResource(R.string.revival_finish_title)) },
        text = { Text(stringResource(R.string.revival_finish, event.restoredDays ?: 0, event.threshold)) },
        confirmButton = { TextButton(onClick = { opened = false }) { Text(stringResource(R.string.revival_done)) } })
}

@Composable
internal fun RevivalHistory(vm: RevivalViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.joinedCount == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.award?.restoredDays?.let { Text(stringResource(R.string.revival_history_award, it)) }
        state.currentRevived?.let { Text(stringResource(R.string.revival_history_current, it)) }
        Text(stringResource(R.string.revival_local_counts, state.completedCount, state.joinedCount),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun RevivalAlertSetting() {
    val context = LocalContext.current
    val available = startReminderNotificationsEnabled()
    var enabled by androidx.compose.runtime.remember(context) { mutableStateOf(RevivalAlerts.enabled(context)) }
    InsetGroup {
        SwitchGroupRow(stringResource(R.string.revival_alert_setting), enabled, { value ->
            RevivalAlerts.setEnabled(context, value)
            enabled = value
        }, subtitle = stringResource(R.string.revival_alert_details))
        if (enabled && !available) TextButton(onClick = { openAppNotificationSettings(context) }) {
            Text(stringResource(R.string.revival_open_notifications))
        }
    }
}
