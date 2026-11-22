package com.fastingshift.app.ui.plus

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.PlanOffer
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.weightText
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.billing.Feature
import com.fastingshift.domain.billing.PlanType
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.insights.WeeklyConsistency
import com.fastingshift.domain.insights.WeightVsFasting
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.program.ProgramDay
import com.fastingshift.domain.program.ProgramProgress
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.service.FastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------- Paywall ----------

@HiltViewModel
class PaywallViewModel @Inject constructor(val billing: BillingManager) : ViewModel()

/**
 * Shown only when the user asks for Shift+ (paywall rule 2). Clear prices, the trial terms,
 * and how to cancel, on the same screen.
 */
@Composable
fun PaywallRoute(onClose: () -> Unit, vm: PaywallViewModel = hiltViewModel()) {
    val activity = LocalActivity.current ?: return
    val scope = rememberCoroutineScope()
    var offers by remember { mutableStateOf<List<PlanOffer>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ent by vm.billing.entitlement.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { offers = vm.billing.offers() }

    var chosen by remember { mutableStateOf<PlanType?>(null) }
    SecondaryScaffold(title = stringResource(R.string.paywall_title), onBack = onClose) {
        InsetGroup(footer = stringResource(R.string.paywall_free_forever)) {
            listOf(R.string.paywall_point_program, R.string.paywall_point_insights).forEachIndexed { i, point ->
                if (i > 0) GroupDivider()
                GroupRow(
                    title = stringResource(point),
                    leading = { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        val list = offers
        when {
            ent.isShiftPlus -> AppCard { Text(stringResource(R.string.paywall_already), color = MaterialTheme.colorScheme.primary) }
            !vm.billing.isConfigured -> AppCard { Text(stringResource(R.string.paywall_unavailable)) }
            list == null -> LoadingBox()
            list.isEmpty() -> AppCard { Text(stringResource(R.string.paywall_unavailable)) }
            else -> {
                // Annual is highlighted and preselected; every plan shows its full price.
                val selected = chosen ?: list.firstOrNull { it.plan == PlanType.ANNUAL }?.plan ?: list.first().plan
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { offer -> PlanCard(offer, offer.plan == selected) { chosen = offer.plan } }
                }
                val offer = list.first { it.plan == selected }
                PrimaryButton(
                    stringResource(if (offer.hasTrial) R.string.paywall_start_trial else R.string.paywall_continue),
                    onClick = { scope.launch { error = vm.billing.purchase(activity, offer); if (error == null && vm.billing.entitlement.value.isShiftPlus) onClose() } },
                    haptic = true,
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Paywall rule 1: leaving is as easy as buying.
        SecondaryButton(stringResource(R.string.onb_offer_free), onClick = onClose)
        Text(stringResource(R.string.paywall_terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, onSelect: () -> Unit) {
    val label = stringResource(
        when (offer.plan) {
            PlanType.ANNUAL -> R.string.plan_annual
            PlanType.MONTHLY -> R.string.plan_monthly
            PlanType.LIFETIME -> R.string.plan_lifetime
            PlanType.FREE -> R.string.plan_free
        },
    )
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
            .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (offer.plan == PlanType.ANNUAL) {
                        Text(
                            stringResource(R.string.paywall_best_value),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                if (offer.hasTrial) Text(stringResource(R.string.paywall_trial_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(offer.price, style = MaterialTheme.typography.titleMedium)
            RadioButton(selected = selected, onClick = null)
        }
    }
}

// ---------- Guided program ----------

@HiltViewModel
class ProgramViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val settings: SettingsRepository,
    val billing: BillingManager,
    private val clock: Clock,
) : ViewModel() {
    private val program = GuidedPrograms.fastingShift21
    val state = combine(programs.observeCompleted(program.id), settings.settings, billing.entitlement) { done, s, ent ->
        ProgramState(
            available = Feature.GUIDED_PROGRAM.isAvailable(ent),
            started = s.activeProgramId == program.id,
            done = done,
            today = ProgramProgress.currentDay(program, done, ZoneId.systemDefault(), LocalDate.now()),
            finished = ProgramProgress.isFinished(program, done),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val days = program.days
    fun start() = viewModelScope.launch { settings.setActiveProgram(program.id) }
    fun stop() = viewModelScope.launch { settings.setActiveProgram(null) }
    fun complete(day: Int) = viewModelScope.launch { programs.completeDay(program.id, day, clock.instant()) }
}

data class ProgramState(
    val available: Boolean = false,
    val started: Boolean = false,
    val done: Map<Int, java.time.Instant> = emptyMap(),
    val today: ProgramDay? = null,
    val finished: Boolean = false,
)

@Composable
fun ProgramRoute(onBack: () -> Unit, onUpgrade: () -> Unit, vm: ProgramViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    SecondaryScaffold(title = GuidedPrograms.fastingShift21.name, onBack = onBack) {
        val st = loaded
        if (st == null) {
            LoadingBox()
        } else if (!st.available) {
            AppCard { Text(stringResource(R.string.program_locked), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.plan_see_plus), onClick = onUpgrade)
        } else if (!st.started) {
            AppCard { Text(stringResource(R.string.program_intro), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.program_start), onClick = vm::start, haptic = true)
        } else {
            // Progress through the 21 days as a calm bar, not a score.
            AppCard {
                Text(pluralStringResource(R.plurals.program_progress, vm.days.size, st.done.size, vm.days.size), style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { st.done.size / vm.days.size.toFloat().coerceAtLeast(1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
            when {
                st.finished -> AppCard { Text(stringResource(R.string.program_finished), style = MaterialTheme.typography.bodyLarge) }
                st.today == null -> AppCard { Text(stringResource(R.string.program_come_back), style = MaterialTheme.typography.bodyLarge) }
                else -> st.today?.let { day ->
                    AppCard {
                        Text(stringResource(R.string.program_day, day.day, day.title), style = MaterialTheme.typography.titleLarge)
                        Text(day.lesson, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            pluralStringResource(R.plurals.program_target, day.targetHours, day.targetHours),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        day.chapter?.let { Text(stringResource(R.string.program_chapter, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    PrimaryButton(stringResource(R.string.program_complete), onClick = { vm.complete(day.day) }, haptic = true)
                }
            }
            PlainButton(stringResource(R.string.program_stop), onClick = vm::stop, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ---------- Insights ----------

data class InsightsState(
    val available: Boolean = false,
    val consistency: List<WeeklyConsistency> = emptyList(),
    val byWeekday: Map<DayOfWeek, Duration> = emptyMap(),
    val weight: List<WeightVsFasting> = emptyList(),
    val hideWeight: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    fasts: FastRepository,
    logs: LogRepository,
    schedules: ScheduleRepository,
    settings: SettingsRepository,
    billing: BillingManager,
    clock: Clock,
) : ViewModel() {
    val state = combine(fasts.observeAll(), logs.observeWeights(), schedules.observeAll(), settings.settings, billing.entitlement) { f, w, sch, s, ent ->
        val zone = ZoneId.systemDefault()
        InsightsState(
            available = Feature.INSIGHTS.isAvailable(ent),
            consistency = Insights.weeklyConsistency(f, zone, LocalDate.now(zone), 8) { ScheduleCalculator.isPlannedDay(sch, it) },
            byWeekday = Insights.averageByWeekday(f, zone),
            weight = Insights.weightVsFasting(f, w, zone, clock.instant()).takeLast(8),
            hideWeight = s.hideWeight,
            weightUnit = s.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsRoute(onBack: () -> Unit, onUpgrade: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    SecondaryScaffold(title = stringResource(R.string.insights_title), onBack = onBack) {
        val st = loaded
        if (st == null) {
            LoadingBox()
        } else if (!st.available) {
            AppCard { Text(stringResource(R.string.insights_locked), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.plan_see_plus), onClick = onUpgrade)
        } else {
            AppCard {
                Text(stringResource(R.string.insights_consistency), style = MaterialTheme.typography.titleMedium)
                Bars(st.consistency.map { it.ratio })
                Text(stringResource(R.string.insights_consistency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InsetGroup(header = stringResource(R.string.insights_weekday)) {
                DayOfWeek.entries.forEachIndexed { i, d ->
                    if (i > 0) GroupDivider()
                    val avg = st.byWeekday[d]
                    GroupRow(
                        title = d.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        value = avg?.hoursMinutes() ?: "—",
                    )
                }
            }
            if (!st.hideWeight) {
                val f = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                InsetGroup(header = stringResource(R.string.insights_weight), footer = stringResource(R.string.insights_weight_note)) {
                    st.weight.forEachIndexed { i, w ->
                        if (i > 0) GroupDivider()
                        GroupRow(
                            title = w.weekStart.format(f),
                            subtitle = com.fastingshift.app.ui.components.hoursLabel(w.fastingHours.toLong()),
                            value = w.averageWeightKg?.let { kg -> weightText(convertWeight(kg, WeightUnit.KG, st.weightUnit), st.weightUnit) } ?: "—",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bars(values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barW = slot * 0.5f
        val radius = CornerRadius(barW / 2, barW / 2)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
            val h = (size.height * v.coerceIn(0f, 1f)).let { if (it > 0f) it.coerceAtLeast(barW) else 0f }
            if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), radius)
        }
    }
}
