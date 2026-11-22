package com.fastingshift.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.LargeTitle
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.ramadan.CityPickerSheet
import com.fastingshift.app.ui.ramadan.MethodPickerSheet
import com.fastingshift.app.ui.ramadan.RamadanText
import com.fastingshift.app.ui.ramadan.clockTime
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.PrayerDay
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Today's Ramadan times for the chosen city, for the preview rows. */
data class RamadanPreview(val city: City, val method: PrayerMethod, val today: PrayerDay)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val billing: BillingManager,
    val health: HealthConnectManager,
    private val export: ExportManager,
    screening: ScreeningRepository,
    logs: LogRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** The latest screening result for the Safety section; null inside means "not screened". */
    val screeningStatus: StateFlow<ScreeningStatus?> = screening.observeLatest()
        .map { r -> ScreeningStatus(r?.gateResult, r?.clinicianAcknowledged ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Automatic water goal from the latest logged weight, else the weight from the safety answers. */
    val waterGoal: StateFlow<WaterGoalDisplay> = combine(settings.settings, logs.observeWeights(), screening.observeLatest()) { s, weights, r ->
        val latest = weights.maxByOrNull { it.at }
        val kg = latest?.let { convertWeight(it.value, it.unit, WeightUnit.KG) } ?: r?.answers?.weightKg
        WaterGoalDisplay.of(s.waterGoalMl, kg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterGoalDisplay.of(null, null))

    val ramadan: StateFlow<RamadanPreview?> = settings.settings.map { s ->
        Cities.byId(s.ramadanCityId)?.let { city ->
            val method = s.ramadanMethod ?: Cities.defaultMethod(city)
            RamadanPreview(city, method, RamadanPlanner.day(city, method, clock.instant().atZone(city.zone).toLocalDate()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<Int?>(null)

    fun set(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch { settings.block() }

    fun toggleMilestone(h: Int) = set {
        val current = current().reminders.milestones.map { it.toHours().toInt() }.toSet()
        setMilestoneHours(if (h in current) current - h else current + h)
    }

    fun chooseCity(city: City) = set { setRamadanCity(city.id, Cities.defaultMethod(city)) }
    fun chooseMethod(city: City, method: PrayerMethod) = set { setRamadanCity(city.id, method) }

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

/** Which bottom sheet or dialog is open. */
private enum class SettingsSheet { MILESTONES, FORGOT, WATER, CITY, METHOD, BOOK_CODE }

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
    val water by vm.waterGoal.collectAsStateWithLifecycle()
    val ramadan by vm.ramadan.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ent by vm.billing.entitlement.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportZip) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    var restoreFile by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreFile = it }
    restoreFile?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreFile = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    restoreFile = null
                    vm.restore(file)
                }) { Text(stringResource(R.string.restore_confirm_action)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restoreFile = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    val hcLauncher = rememberLauncherForActivityResult(vm.health.permissionContract()) { vm.onHealthPermissions(it) }

    msg?.let { m ->
        LaunchedEffect(m) {
            snackbar.showSnackbar(context.getString(m))
            vm.message.value = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            LargeTitle(stringResource(R.string.settings_title), Modifier.padding(horizontal = 4.dp))

            PlanGroup(ent, vm, onOpenPaywall, onBookCode = { sheet = SettingsSheet.BOOK_CODE })

            InsetGroup(header = stringResource(R.string.settings_reminders), footer = stringResource(R.string.settings_reminders_footer)) {
                SwitchGroupRow(stringResource(R.string.settings_end_alert), s.reminders.fastEndEnabled, { v -> vm.set { setFastEndReminder(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_near_goal), s.reminders.nearGoalEnabled, { v -> vm.set { setNearGoalReminder(v) } })
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_milestones),
                    value = milestonesValue(s.reminders.milestones.map { it.toHours().toInt() }),
                    showChevron = true,
                    onClick = { sheet = SettingsSheet.MILESTONES },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_forgot_title),
                    value = forgotValue(s.reminders.forgotToEndAfter?.toHours()?.toInt() ?: 0),
                    showChevron = true,
                    onClick = { sheet = SettingsSheet.FORGOT },
                )
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_start_alert), s.reminders.fastStartEnabled, { v -> vm.set { setFastStartReminder(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_window_closing), s.reminders.windowClosingEnabled, { v -> vm.set { setWindowClosingReminder(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_hydration), s.reminders.hydrationEnabled, { v -> vm.set { setHydrationNudges(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_ongoing), s.ongoingNotification, { v -> vm.set { setOngoingNotification(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_coaching), s.coachingEnabled, { v -> vm.set { setCoaching(v) } })
                GroupDivider()
                SwitchGroupRow(stringResource(R.string.settings_weekly_recap), s.weeklyRecapEnabled, { v -> vm.set { setWeeklyRecap(v) } })
            }

            InsetGroup(header = stringResource(R.string.settings_water), footer = stringResource(R.string.settings_water_footer)) {
                GroupRow(
                    stringResource(R.string.settings_water_goal),
                    value = waterGoalValue(water),
                    showChevron = true,
                    onClick = { sheet = SettingsSheet.WATER },
                )
            }

            RamadanGroup(
                s = s,
                preview = ramadan,
                onToggle = { on ->
                    vm.set { setRamadan(on) }
                    if (on && ramadan == null) sheet = SettingsSheet.CITY
                },
                onCity = { sheet = SettingsSheet.CITY },
                onMethod = { sheet = SettingsSheet.METHOD },
            )

            InsetGroup(header = stringResource(R.string.settings_fasting)) {
                GroupRow(stringResource(R.string.settings_eating_window_title), subtitle = stringResource(R.string.settings_eating_window_subtitle), value = stringResource(R.string.settings_hours_value, s.eatingWindowHours)) {
                    StepperControl(
                        onDecrease = { vm.set { setEatingWindowHours(s.eatingWindowHours - 1) } },
                        onIncrease = { vm.set { setEatingWindowHours(s.eatingWindowHours + 1) } },
                        canDecrease = s.eatingWindowHours > 1,
                        canIncrease = s.eatingWindowHours < 23,
                    )
                }
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_schedules_title),
                    subtitle = stringResource(R.string.settings_schedules_subtitle),
                    showChevron = true,
                    onClick = onOpenSchedules,
                )
                GroupDivider()
                GroupRow(stringResource(R.string.settings_reliability), showChevron = true, onClick = onOpenReliability)
            }

            InsetGroup(header = stringResource(R.string.settings_privacy), footer = stringResource(R.string.settings_data_footer)) {
                SwitchGroupRow(stringResource(R.string.settings_hide_weight), s.hideWeight, { v -> vm.set { setHideWeight(v) } })
                if (vm.health.isAvailable()) {
                    GroupDivider()
                    SwitchGroupRow(
                        stringResource(R.string.settings_health_connect),
                        s.healthConnectSync,
                        { on -> if (on) hcLauncher.launch(vm.health.permissions) else vm.set { setHealthConnectSync(false) } },
                        subtitle = stringResource(R.string.settings_hc_note),
                    )
                } else if (vm.health.needsProviderUpdate()) {
                    // Health Connect is installed but too old to use: offer the Play Store update.
                    GroupDivider()
                    GroupRow(
                        stringResource(R.string.settings_hc_update),
                        showChevron = true,
                        onClick = { runCatching { context.startActivity(vm.health.providerUpdateIntent()) } },
                    )
                }
                GroupDivider()
                ActionRow(stringResource(R.string.settings_export)) { exportLauncher.launch("fasting-shift-export.zip") }
                GroupDivider()
                ActionRow(stringResource(R.string.settings_backup)) { backupLauncher.launch("fasting-shift-backup.json") }
                GroupDivider()
                ActionRow(stringResource(R.string.settings_restore)) {
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }

            LanguageGroup()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsetGroup(header = stringResource(R.string.settings_safety)) {
                    screeningStatus?.let { st ->
                        GroupRow(stringResource(R.string.settings_screening_status), subtitle = stringResource(st.label()))
                        GroupDivider()
                    }
                    GroupRow(stringResource(R.string.settings_rerun_screening), showChevron = true, onClick = onRerunScreening)
                    GroupDivider()
                    GroupRow(stringResource(R.string.settings_refeeding), showChevron = true, onClick = onOpenRefeeding)
                    GroupDivider()
                    GroupRow(stringResource(R.string.settings_privacy_policy), showChevron = true, onClick = {
                        // No browser installed must not crash Settings.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(context.getString(R.string.privacy_policy_url)))) }
                    })
                }
                HelplineList()
                Text(
                    stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (sheet) {
        SettingsSheet.MILESTONES -> MilestonesSheet(
            chosen = s.reminders.milestones.map { it.toHours().toInt() }.toSet(),
            onToggle = vm::toggleMilestone,
            onDismiss = { sheet = null },
        )
        SettingsSheet.FORGOT -> ForgotSheet(
            selected = s.reminders.forgotToEndAfter?.toHours()?.toInt() ?: 0,
            onPick = { h -> vm.set { setForgotToEndHours(h) }; sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.WATER -> WaterGoalSheet(water, onSet = { ml -> vm.set { setWaterGoalMl(ml) } }, onDismiss = { sheet = null })
        SettingsSheet.CITY -> CityPickerSheet(
            selectedId = s.ramadanCityId,
            onPick = { vm.chooseCity(it); sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.METHOD -> ramadan?.let { r ->
            MethodPickerSheet(r.method, r.city, onPick = { vm.chooseMethod(r.city, it); sheet = null }, onDismiss = { sheet = null })
        }
        SettingsSheet.BOOK_CODE -> BookCodeDialog(
            onRedeem = { code ->
                sheet = null
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BillingManager.redeemUri(code))) }
            },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun waterGoalValue(w: WaterGoalDisplay): String {
    val liters = stringResource(R.string.settings_liters, formatLiters(w.ml))
    return if (w.automatic) stringResource(R.string.settings_water_auto, liters) else liters
}

/** A row that performs an action, in the accent colour (iOS action row). */
@Composable
private fun ActionRow(title: String, onClick: () -> Unit) =
    GroupRow(title, titleColor = MaterialTheme.colorScheme.primary, onClick = onClick)

@Composable
private fun RamadanGroup(s: AppSettings, preview: RamadanPreview?, onToggle: (Boolean) -> Unit, onCity: () -> Unit, onMethod: () -> Unit) {
    InsetGroup(
        header = stringResource(R.string.ramadan_group),
        footer = stringResource(if (s.ramadanEnabled) R.string.ramadan_footer else R.string.ramadan_footer_off),
    ) {
        SwitchGroupRow(stringResource(R.string.ramadan_mode_title), s.ramadanEnabled, onToggle)
        if (s.ramadanEnabled) {
            GroupDivider()
            GroupRow(
                stringResource(R.string.ramadan_city),
                value = preview?.city?.name ?: stringResource(R.string.ramadan_city_none),
                showChevron = true,
                onClick = onCity,
            )
            if (preview != null) {
                GroupDivider()
                GroupRow(stringResource(R.string.ramadan_method), value = RamadanText.methodName(preview.method), showChevron = true, onClick = onMethod)
                GroupDivider()
                GroupRow(stringResource(R.string.ramadan_today_suhoor), value = clockTime(preview.today.fajr, preview.city.zone))
                GroupDivider()
                GroupRow(stringResource(R.string.ramadan_today_iftar), value = clockTime(preview.today.maghrib, preview.city.zone))
            }
        }
    }
}

/** Paywall rule 4: plan, renewal date and one-tap cancel are always visible. */
@Composable
private fun PlanGroup(ent: Entitlement, vm: SettingsViewModel, onOpenPaywall: () -> Unit, onBookCode: () -> Unit) {
    val context = LocalContext.current
    InsetGroup(header = stringResource(R.string.settings_plan)) {
        val planName = stringResource(
            when (ent.plan) {
                PlanType.FREE -> R.string.plan_free
                PlanType.MONTHLY -> R.string.plan_monthly
                PlanType.ANNUAL -> R.string.plan_annual
                PlanType.LIFETIME -> R.string.plan_lifetime
            },
        )
        GroupRow(
            stringResource(R.string.settings_plan_row),
            value = planName + if (ent.inTrial) " · " + stringResource(R.string.plan_trial) else "",
            subtitle = ent.expiresAt?.let { stringResource(if (ent.willRenew) R.string.plan_renews else R.string.plan_ends, it.formatDate()) },
        )
        if (ent.plan == PlanType.FREE) {
            GroupDivider()
            GroupRow(stringResource(R.string.plan_see_plus), showChevron = true, onClick = onOpenPaywall)
        }
        if (ent.plan == PlanType.MONTHLY || ent.plan == PlanType.ANNUAL) {
            GroupDivider()
            GroupRow(stringResource(R.string.plan_cancel), showChevron = true, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BillingManager.playSubscriptionsUri(context.packageName))) }
            })
        }
        if (vm.billing.isConfigured) {
            GroupDivider()
            ActionRow(stringResource(R.string.plan_restore), vm::restorePurchases)
            GroupDivider()
            GroupRow(stringResource(R.string.plan_book_code), showChevron = true, onClick = onBookCode)
        }
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
