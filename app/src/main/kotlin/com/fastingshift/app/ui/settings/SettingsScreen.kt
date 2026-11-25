package com.fastingshift.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ads.AdPrivacyChoices
import com.fastingshift.app.ads.SettingsBanner
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.RestoreOutcome
import com.fastingshift.app.export.ExportManager
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.appNotificationsEnabled
import com.fastingshift.app.ui.components.openAppNotificationSettings
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
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
import com.fastingshift.domain.ramadan.FastingTimeSource
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

    var restoringPurchases by mutableStateOf(false)
        private set

    fun restorePurchases() {
        if (restoringPurchases) return
        restoringPurchases = true
        viewModelScope.launch {
            try {
                message.value = when (billing.restore()) {
                    RestoreOutcome.ACTIVE -> R.string.settings_purchases_restored
                    RestoreOutcome.EMPTY -> R.string.shift_restore_empty
                    RestoreOutcome.FAILED -> R.string.shift_restore_failed
                    RestoreOutcome.BUSY -> R.string.shift_transaction_busy
                }
            } finally {
                restoringPurchases = false
            }
        }
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
private enum class SettingsSheet { APPEARANCE, MILESTONES, FORGOT, WATER, CITY, METHOD, BOOK_CODE }

@Composable
internal fun SettingsRoute(
    section: SettingsSection?,
    onOpenSection: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onRerunScreening: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenRefeeding: () -> Unit,
    onOpenTrust: () -> Unit = {},
    onOpenMuslimHub: () -> Unit = {},
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

    if (section == null) {
        Box(Modifier.fillMaxSize()) {
            val planner: SchedulesViewModel = hiltViewModel()
            val week by planner.state.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    SettingsOverview(
                        planner = week,
                        appearance = s.appearance,
                        entitlement = ent,
                        onOpenWeek = onOpenSchedules,
                        onAppearance = { sheet = SettingsSheet.APPEARANCE },
                        onSection = onOpenSection,
                    )
                }
                if (sheet == null) SettingsBanner(vm.billing)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    } else {
        SecondaryScaffold(stringResource(section.title), onBack, snackbar) {
            when (section) {
                SettingsSection.REMINDERS -> ReminderGroups(
                    settings = s,
                    vm = vm,
                    onReliability = onOpenReliability,
                    onMilestones = { sheet = SettingsSheet.MILESTONES },
                    onForgot = { sheet = SettingsSheet.FORGOT },
                )
                SettingsSection.FASTING -> {
                    InsetGroup {
                        EatingWindowControl(
                            s.eatingWindowHours,
                            { vm.set { setEatingWindowHours(s.eatingWindowHours - 1) } },
                            { vm.set { setEatingWindowHours(s.eatingWindowHours + 1) } },
                        )
                    }
                    InsetGroup(header = stringResource(R.string.settings_water), footer = stringResource(R.string.settings_water_footer)) {
                        GroupRow(
                            stringResource(R.string.settings_water_goal),
                            value = waterGoalValue(water),
                            showChevron = true, opensPicker = true,
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

                    SunnahSettingsGroup(s, vm, onOpenMuslimHub)
                }
                SettingsSection.PRIVACY -> {
                    InsetGroup {
                        GroupRow(stringResource(R.string.trust_row), showChevron = true, onClick = onOpenTrust)
                        GroupDivider()
                        SwitchGroupRow(stringResource(R.string.wrapped_title), s.wrappedEnabled, { v -> vm.set { setWrapped(v) } },
                            subtitle = stringResource(R.string.wrapped_setting_note))
                        GroupDivider()
                        GroupRow(stringResource(R.string.recap_local_stats), subtitle = stringResource(R.string.recap_local_stats_detail,
                            s.annualRecapsShared, s.annualRecapsViewed, s.weeklyRecapsShared, s.weeklyRecapsViewed))
                        GroupDivider()
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
                        AdPrivacyChoices { vm.message.value = R.string.ad_privacy_unavailable }
                        GroupRow(stringResource(R.string.settings_privacy_policy), showChevron = true, onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(context.getString(R.string.privacy_policy_url)))) }
                        })
                    }
                    InsetGroup(header = stringResource(R.string.settings_files), footer = stringResource(R.string.settings_data_footer)) {
                        HistoryTransferRows()
                        GroupDivider()
                        ActionRow(stringResource(R.string.settings_export)) { exportLauncher.launch("fasting-shift-export.zip") }
                        GroupDivider()
                        ActionRow(stringResource(R.string.settings_backup)) { backupLauncher.launch("fasting-shift-backup.json") }
                    }
                    InsetGroup {
                        GroupRow(
                            stringResource(R.string.settings_restore),
                            subtitle = stringResource(R.string.settings_restore_summary),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        )
                    }
                }
                SettingsSection.SAFETY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsetGroup(header = stringResource(R.string.settings_safety)) {
                            screeningStatus?.let { st ->
                                GroupRow(stringResource(R.string.settings_screening_status), subtitle = stringResource(st.label()))
                                GroupDivider()
                            }
                            GroupRow(stringResource(R.string.settings_rerun_screening), showChevron = true, onClick = onRerunScreening)
                            GroupDivider()
                            GroupRow(stringResource(R.string.settings_refeeding), showChevron = true, onClick = onOpenRefeeding)
                        }
                        HelplineList()
                        Text(
                            stringResource(R.string.disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    DoctorReportGroup()
                }
                SettingsSection.PLAN -> PlanGroup(ent, vm, onOpenPaywall, onBookCode = { sheet = SettingsSheet.BOOK_CODE })
            }
        }
    }

    when (sheet) {
        SettingsSheet.APPEARANCE -> AppearanceSheet(
            selected = s.appearance,
            onPick = { mode -> vm.set { setAppearance(mode) }; sheet = null },
            onDismiss = { sheet = null },
        )
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
private fun ReminderGroups(
    settings: AppSettings,
    vm: SettingsViewModel,
    onReliability: () -> Unit,
    onMilestones: () -> Unit,
    onForgot: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsEnabled by remember(context) { mutableStateOf(appNotificationsEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsEnabled = appNotificationsEnabled(context) }
    if (!notificationsEnabled) {
        InsetGroup {
            GroupRow(
                title = stringResource(R.string.rel_notifications),
                value = stringResource(R.string.off),
                subtitle = stringResource(R.string.rel_notifications_fix),
                showChevron = true,
                onClick = { openAppNotificationSettings(context) },
            )
        }
    }
    val reminders = settings.reminders
    InsetGroup(header = stringResource(R.string.settings_fast_timing)) {
        SwitchGroupRow(stringResource(R.string.settings_start_alert), reminders.fastStartEnabled, { v -> vm.set { setFastStartReminder(v) } })
        GroupDivider()
        SwitchGroupRow(stringResource(R.string.settings_end_alert), reminders.fastEndEnabled, { v -> vm.set { setFastEndReminder(v) } })
        GroupDivider()
        GroupRow(
            stringResource(R.string.settings_milestones),
            value = milestonesValue(reminders.milestones.map { it.toHours().toInt() }),
            showChevron = true, opensPicker = true, onClick = onMilestones,
        )
    }
    ReminderDisclosure(R.string.settings_adjust_timing,
        listOf(reminders.nearGoalEnabled, reminders.forgotToEndAfter != null, reminders.windowClosingEnabled).count { it }) {
        SwitchGroupRow(stringResource(R.string.settings_near_goal), reminders.nearGoalEnabled, { v -> vm.set { setNearGoalReminder(v) } })
        GroupDivider()
        GroupRow(
            stringResource(R.string.settings_forgot_title),
            value = forgotValue(reminders.forgotToEndAfter?.toHours()?.toInt() ?: 0),
            showChevron = true, opensPicker = true, onClick = onForgot,
        )
        GroupDivider()
        SwitchGroupRow(stringResource(R.string.settings_window_closing), reminders.windowClosingEnabled, { v -> vm.set { setWindowClosingReminder(v) } })
    }
    ReminderDisclosure(R.string.settings_routine_reminders,
        listOf(reminders.hydrationEnabled, settings.coachingEnabled, settings.weeklyRecapEnabled).count { it }) {
        SwitchGroupRow(stringResource(R.string.settings_hydration), reminders.hydrationEnabled, { v -> vm.set { setHydrationNudges(v) } })
        GroupDivider()
        SwitchGroupRow(stringResource(R.string.settings_coaching), settings.coachingEnabled, { v -> vm.set { setCoaching(v) } })
        GroupDivider()
        SwitchGroupRow(stringResource(R.string.settings_weekly_recap), settings.weeklyRecapEnabled, { v -> vm.set { setWeeklyRecap(v) } })
    }
    InsetGroup(header = stringResource(R.string.settings_delivery)) {
        GroupRow(
            stringResource(R.string.settings_reliability),
            subtitle = stringResource(R.string.settings_reminders_footer),
            showChevron = true, onClick = onReliability,
        )
        GroupDivider()
        SwitchGroupRow(stringResource(R.string.settings_ongoing), settings.ongoingNotification, { v -> vm.set { setOngoingNotification(v) } })
    }
}

@Composable
private fun ReminderDisclosure(title: Int, enabledCount: Int, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val status = stringResource(if (expanded) R.string.settings_expanded else R.string.settings_collapsed)
    InsetGroup {
        GroupRow(stringResource(title), modifier = Modifier.semantics { stateDescription = status },
            value = stringResource(R.string.settings_alerts_enabled, enabledCount), onClick = { expanded = !expanded },
            trailing = {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f))
            })
        if (expanded) {
            GroupDivider()
            content()
        }
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
        footer = when {
            !s.ramadanEnabled -> stringResource(R.string.ramadan_footer_off)
            preview != null && (preview.today.fajr == null || preview.today.maghrib == null) ->
                stringResource(R.string.ramadan_local_timetable)
            preview != null && preview.today.fastingTimeSource == FastingTimeSource.MUIS_TIMETABLE ->
                stringResource(R.string.ramadan_footer_muis_timetable, preview.today.date.year)
            else -> stringResource(R.string.ramadan_footer)
        },
    ) {
        SwitchGroupRow(stringResource(R.string.ramadan_mode_title), s.ramadanEnabled, onToggle)
        if (s.ramadanEnabled) {
            GroupDivider()
            GroupRow(
                stringResource(R.string.ramadan_city),
                value = preview?.city?.name ?: stringResource(R.string.ramadan_city_none),
                showChevron = true,
                opensPicker = true, onClick = onCity,
            )
            if (preview != null) {
                GroupDivider()
                GroupRow(stringResource(R.string.ramadan_method), value = RamadanText.methodName(preview.method), showChevron = true, opensPicker = true, onClick = onMethod)
                GroupDivider()
                GroupRow(
                    stringResource(R.string.ramadan_today_suhoor),
                    value = preview.today.fajr?.let { clockTime(it, preview.city.zone) } ?: stringResource(R.string.ramadan_time_unavailable),
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.ramadan_today_iftar),
                    value = preview.today.maghrib?.let { clockTime(it, preview.city.zone) } ?: stringResource(R.string.ramadan_time_unavailable),
                )
            }
        }
    }
}

/** Paywall rule 4: plan, renewal date and one-tap cancel are always visible. */
@Composable
private fun PlanGroup(ent: Entitlement, vm: SettingsViewModel, onOpenPaywall: () -> Unit, onBookCode: () -> Unit) {
    InsetGroup {
        val planName = stringResource(planLabel(ent.plan))
        GroupRow(
            stringResource(R.string.settings_plan_row),
            value = planName + if (ent.inTrial) " · " + stringResource(R.string.plan_trial) else "",
            subtitle = ent.expiresAt?.let { stringResource(if (ent.willRenew) R.string.plan_renews else R.string.plan_ends, it.formatDate()) },
        )
        GroupDivider()
        GroupRow(stringResource(R.string.plan_see_plus), showChevron = true, onClick = onOpenPaywall)
        if (ent.plan == PlanType.MONTHLY || ent.plan == PlanType.ANNUAL) {
            GroupDivider()
            ManagePlanRow()
        }
        if (vm.billing.isConfigured) {
            GroupDivider()
            if (vm.restoringPurchases) GroupRow(stringResource(R.string.shift_restoring))
            else ActionRow(stringResource(R.string.plan_restore), vm::restorePurchases)
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
