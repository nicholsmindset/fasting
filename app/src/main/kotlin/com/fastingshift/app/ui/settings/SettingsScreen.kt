package com.fastingshift.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.ScreeningRepository
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.export.ExportManager
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val billing: BillingManager,
    val health: HealthConnectManager,
    private val export: ExportManager,
    screening: ScreeningRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** The latest screening result for the Safety section; null inside means "not screened". */
    val screeningStatus: StateFlow<ScreeningStatus?> = screening.observeLatest()
        .map { r -> ScreeningStatus(r?.gateResult, r?.clinicianAcknowledged ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<Int?>(null)

    fun set(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch { settings.block() }

    fun toggleMilestone(h: Int) = set {
        val current = current().reminders.milestones.map { it.toHours().toInt() }.toSet()
        setMilestoneHours(if (h in current) current - h else current + h)
    }

    fun onHealthPermissions(granted: Set<String>) = viewModelScope.launch {
        val ok = granted.containsAll(health.permissions)
        settings.setHealthConnectSync(ok)
        if (ok) health.importWeights(clock.instant().minus(Duration.ofDays(30)))
        message.value = if (ok) R.string.settings_hc_on else R.string.settings_hc_denied
    }

    fun exportZip(uri: android.net.Uri) = io(R.string.settings_exported) { export.writeExportZip(uri) }
    fun backup(uri: android.net.Uri) = io(R.string.settings_backed_up) { export.writeBackup(uri) }
    fun restore(uri: android.net.Uri) = io(R.string.settings_restored) { export.restore(uri) }

    fun restorePurchases() = viewModelScope.launch {
        message.value = if (billing.restore() == null) R.string.settings_purchases_restored else R.string.settings_billing_error
    }

    private fun io(success: Int, block: suspend () -> Unit) = viewModelScope.launch {
        message.value = try {
            block(); success
        } catch (e: BackupFormatException) {
            R.string.settings_restore_invalid
        } catch (e: Exception) {
            R.string.settings_file_error
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(
    onRerunScreening: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenRefeeding: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val screeningStatus by vm.screeningStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ent by vm.billing.entitlement.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportZip) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    val hcLauncher = rememberLauncherForActivityResult(vm.health.permissionContract()) { vm.onHealthPermissions(it) }

    msg?.let { m ->
        LaunchedEffect(m) {
            snackbar.showSnackbar(context.getString(m))
            vm.message.value = null
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SubscriptionCard(ent, vm, onOpenPaywall)

        SectionCard(title = stringResource(R.string.settings_reminders)) {
            Toggle(R.string.settings_end_alert, s.reminders.fastEndEnabled) { v -> vm.set { setFastEndReminder(v) } }
            Toggle(R.string.settings_start_alert, s.reminders.fastStartEnabled) { v -> vm.set { setFastStartReminder(v) } }
            Toggle(R.string.settings_ongoing, s.ongoingNotification) { v -> vm.set { setOngoingNotification(v) } }
            Text(stringResource(R.string.settings_milestones))
            val chosen = s.reminders.milestones.map { it.toHours().toInt() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 16, 18, 20, 24, 36, 48).forEach { h ->
                    FilterChip(selected = h in chosen, onClick = { vm.toggleMilestone(h) }, label = { Text("${h}h") })
                }
            }
            Text(stringResource(R.string.settings_forgot))
            val forgot = s.reminders.forgotToEndAfter?.toHours()?.toInt() ?: 0
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2, 4, 6, 8).forEach { h ->
                    FilterChip(selected = forgot == h, onClick = { vm.set { setForgotToEndHours(h) } },
                        label = { Text(if (h == 0) stringResource(R.string.off) else "${h}h") })
                }
            }
            Stepper(R.string.settings_eating_window, s.eatingWindowHours) { v -> vm.set { setEatingWindowHours(v) } }
            TextButton(onClick = onOpenSchedules) { Text(stringResource(R.string.settings_schedules)) }
            TextButton(onClick = onOpenReliability) { Text(stringResource(R.string.settings_reliability)) }
        }

        SectionCard(title = stringResource(R.string.settings_privacy)) {
            Toggle(R.string.settings_hide_weight, s.hideWeight) { v -> vm.set { setHideWeight(v) } }
            if (vm.health.isAvailable()) {
                Toggle(R.string.settings_health_connect, s.healthConnectSync) { on ->
                    if (on) hcLauncher.launch(vm.health.permissions) else vm.set { setHealthConnectSync(false) }
                }
                Text(stringResource(R.string.settings_hc_note), style = MaterialTheme.typography.bodySmall)
            } else if (vm.health.needsProviderUpdate()) {
                // Health Connect is installed but too old to use: offer the Play Store update.
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(vm.health.providerUpdateIntent()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_hc_update)) }
            }
            Text(stringResource(R.string.settings_local_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { exportLauncher.launch("fasting-shift-export.zip") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(onClick = { backupLauncher.launch("fasting-shift-backup.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_backup)) }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_restore))
            }
            Text(stringResource(R.string.settings_restore_note), style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = stringResource(R.string.settings_safety)) {
            screeningStatus?.let { st ->
                Text(stringResource(R.string.settings_screening_status), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(st.label()))
            }
            OutlinedButton(onClick = onRerunScreening, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_rerun_screening)) }
            TextButton(onClick = onOpenRefeeding) { Text(stringResource(R.string.settings_refeeding)) }
            HelplineList()
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(context.getString(R.string.privacy_policy_url))))
            }) { Text(stringResource(R.string.settings_privacy_policy)) }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

data class ScreeningStatus(val gate: GateResult?, val clinicianAcknowledged: Boolean) {
    fun label(): Int = when (gate) {
        GateResult.OK -> R.string.screening_ok
        GateResult.SOFT -> if (clinicianAcknowledged) R.string.screening_soft_ack else R.string.screening_soft
        GateResult.BLOCK -> R.string.screening_block
        null -> R.string.screening_none
    }
}

/** Paywall rule 4: plan, renewal date and one-tap cancel are always visible. */
@Composable
private fun SubscriptionCard(ent: Entitlement, vm: SettingsViewModel, onOpenPaywall: () -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.settings_plan)) {
        val planName = stringResource(
            when (ent.plan) {
                PlanType.FREE -> R.string.plan_free
                PlanType.MONTHLY -> R.string.plan_monthly
                PlanType.ANNUAL -> R.string.plan_annual
                PlanType.LIFETIME -> R.string.plan_lifetime
            },
        )
        Text(planName + if (ent.inTrial) " · " + stringResource(R.string.plan_trial) else "", style = MaterialTheme.typography.titleSmall)
        ent.expiresAt?.let {
            Text(stringResource(if (ent.willRenew) R.string.plan_renews else R.string.plan_ends, it.formatDate()))
        }
        if (ent.plan == PlanType.FREE) {
            TextButton(onClick = onOpenPaywall) { Text(stringResource(R.string.plan_see_plus)) }
        }
        if (ent.plan == PlanType.MONTHLY || ent.plan == PlanType.ANNUAL) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, BillingManager.playSubscriptionsUri(context.packageName)))
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_cancel)) }
        }
        if (vm.billing.isConfigured) {
            TextButton(onClick = vm::restorePurchases) { Text(stringResource(R.string.plan_restore)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = true, label = { Text(stringResource(R.string.plan_book_code)) }, modifier = Modifier.weight(1f))
                TextButton(enabled = code.isNotBlank(), onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, BillingManager.redeemUri(code)))
                }) { Text(stringResource(R.string.plan_redeem)) }
            }
        }
    }
}

@Composable
private fun Toggle(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: Int, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label, value), Modifier.weight(1f))
        val decrease = stringResource(R.string.a11y_decrease)
        val increase = stringResource(R.string.a11y_increase)
        OutlinedButton(onClick = { onChange(value - 1) }) { Text("−", Modifier.semantics { contentDescription = decrease }) }
        OutlinedButton(onClick = { onChange(value + 1) }) { Text("+", Modifier.semantics { contentDescription = increase }) }
    }
}
