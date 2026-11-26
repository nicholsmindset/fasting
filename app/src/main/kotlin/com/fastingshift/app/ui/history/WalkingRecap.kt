package com.fastingshift.app.ui.history

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fastingshift.app.R
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.health.WeeklySteps
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Counts live only while the current week's private recap is on screen. */
@Composable
internal fun WalkingRecap(health: HealthConnectManager, weekStart: LocalDate, now: Instant, zone: ZoneId) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val contract = remember(health) { health.permissionContract() }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(contract) { granted ->
        denied = !granted.containsAll(health.stepPermissions)
        refresh++
    }
    if (lifecycle != Lifecycle.State.RESUMED) return

    var state by remember(weekStart, zone) { mutableStateOf<WeeklySteps?>(null) }
    LaunchedEffect(weekStart, now, zone, refresh) {
        state = health.weeklySteps(weekStart, now, zone)
    }
    val result = state ?: return
    Column {
        Text(stringResource(R.string.recap_steps_heading), style = MaterialTheme.typography.titleSmall)
        when (result) {
            WeeklySteps.PermissionNeeded -> {
                val reason = if (denied) R.string.recap_steps_denied else R.string.recap_steps_privacy
                Text(stringResource(reason), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    try {
                        launcher.launch(health.stepPermissions)
                    } catch (_: ActivityNotFoundException) {
                        state = WeeklySteps.Unavailable
                    } catch (_: SecurityException) {
                        state = WeeklySteps.ReadFailed
                    }
                }) { Text(stringResource(R.string.recap_steps_connect)) }
            }
            WeeklySteps.Unavailable -> Text(stringResource(R.string.recap_steps_unavailable))
            WeeklySteps.ReadFailed -> {
                Text(stringResource(R.string.recap_steps_failed))
                TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.recap_steps_retry)) }
            }
            WeeklySteps.NoData -> Text(stringResource(R.string.recap_steps_empty))
            is WeeklySteps.Total -> {
                val locale = LocalConfiguration.current.locales[0]
                val formatted = remember(result.count, locale) { NumberFormat.getIntegerInstance(locale).format(result.count) }
                Text(stringResource(if (result.count == 1L) R.string.recap_steps_one else R.string.recap_steps_total, formatted))
            }
        }
    }
}
