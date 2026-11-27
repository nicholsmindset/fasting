package com.fastingshift.app.ui.plus

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
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
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.Feature
import com.fastingshift.domain.billing.PlanType
import com.fastingshift.domain.insights.ScheduleReview
import com.fastingshift.domain.insights.ReviewWeek
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.insights.WeeklyConsistency
import com.fastingshift.domain.insights.WeightVsFasting
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.program.ProgramDay
import com.fastingshift.domain.program.ProgramProgress
import com.fastingshift.domain.service.FastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import com.fastingshift.app.ui.settings.ManagePlanRow
import com.fastingshift.app.ui.settings.SchedulesViewModel
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------- Paywall ----------

@Composable
fun PaywallRoute(
    onClose: () -> Unit,
    onOpenProgram: () -> Unit,
    onOpenReview: () -> Unit,
    vm: PaywallViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current
    val ent by vm.entitlement.collectAsStateWithLifecycle()
    var chosenId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val busy = vm.action != null
    SecondaryScaffold(title = stringResource(R.string.paywall_title), onBack = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.shift_review_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.shift_review_description), style = MaterialTheme.typography.bodyLarge)
        }

        if (ent.isShiftPlus) {
            PrimaryButton(stringResource(R.string.shift_open_review), onClick = onOpenReview)
            if (ent.plan != PlanType.LIFETIME && !ent.fromBookCode) ManagePlanRow()
        } else {
            SecondaryButton(stringResource(R.string.shift_example_review), onClick = onOpenReview)
        }
        val program = GuidedPrograms.fastingShift21
        when {
            program.isPublished && ent.isShiftPlus ->
                GroupRow(stringResource(R.string.program_full_title), showChevron = true, onClick = onOpenProgram)
            program.freePreview != null ->
                PlainButton(stringResource(R.string.program_read_day_one), onClick = onOpenProgram)
            else -> PlainButton(stringResource(R.string.program_read_sample), onClick = onOpenProgram)
        }

        if (!ent.isShiftPlus) {
            when (val store = vm.storefront) {
                Storefront.Disabled -> Text(stringResource(R.string.shift_billing_off), style = MaterialTheme.typography.bodyMedium)
                Storefront.Loading -> LoadingBox()
                Storefront.Empty, Storefront.Failed -> {
                    Text(stringResource(if (store == Storefront.Empty) R.string.shift_no_offers else R.string.shift_load_failed))
                    SecondaryButton(stringResource(R.string.shift_retry_plans), onClick = vm::reload, enabled = !busy)
                }
                is Storefront.Ready -> {
                    val selected = store.offers.find { it.id == chosenId }
                        ?: store.offers.find { it.plan == PlanType.ANNUAL } ?: store.offers.first()
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        store.offers.forEach { offer ->
                            PlanCard(offer, offer.id == selected.id, enabled = !busy) { chosenId = offer.id }
                        }
                    }
                    firstPaymentAfterTrial(selected)?.let { firstPrice ->
                        Text(stringResource(R.string.shift_first_payment, firstPrice), style = MaterialTheme.typography.bodyMedium)
                    }
                    PrimaryButton(
                        stringResource(if (vm.action == StoreAction.PURCHASE) R.string.shift_opening_play else R.string.shift_continue_play),
                        onClick = { activity?.let { vm.purchase(it, selected) } },
                        enabled = activity != null && !busy && !vm.pendingPayment,
                    )
                }
            }
        }
        vm.message?.takeIf { !ent.isShiftPlus || it == R.string.shift_purchase_active || it == R.string.settings_purchases_restored }?.let { status ->
            Text(stringResource(status), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (vm.configured && !ent.isShiftPlus) {
            SecondaryButton(
                stringResource(if (vm.action == StoreAction.RESTORE) R.string.shift_restoring else R.string.plan_restore),
                onClick = vm::restore, enabled = !busy,
            )
        }
        Text(stringResource(R.string.shift_free_features), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SecondaryButton(stringResource(if (ent.isShiftPlus) R.string.settings_done else R.string.onb_offer_free), onClick = onClose)
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val label = stringResource(
        when (offer.plan) {
            PlanType.MONTHLY -> R.string.plan_monthly
            PlanType.ANNUAL -> R.string.plan_annual
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
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                val terms = offerTerms(offer, LocalConfiguration.current.locales[0], LocalContext.current.resources)
                terms.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            RadioButton(selected = selected, enabled = enabled, onClick = null)
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
        val localClock = clock.withZone(ZoneId.systemDefault())
        ProgramState(
            available = Feature.GUIDED_PROGRAM.isAvailable(ent),
            started = s.activeProgramId == program.id,
            done = done,
            today = ProgramProgress.currentDay(program, done, localClock.zone, LocalDate.now(localClock)),
            finished = ProgramProgress.isFinished(program, done),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val days = program.days
    fun start() = viewModelScope.launch {
        if (program.isPublished && Feature.GUIDED_PROGRAM.isAvailable(billing.entitlement.value)) {
            settings.setActiveProgram(program.id)
        }
    }
    fun stop() = viewModelScope.launch { settings.setActiveProgram(null) }
    fun complete(day: Int) = viewModelScope.launch {
        val current = state.value ?: return@launch
        if (program.isPublished && Feature.GUIDED_PROGRAM.isAvailable(billing.entitlement.value) &&
            current.started && current.today?.day == day
        ) {
            programs.completeDay(program.id, day, clock.instant())
        }
    }
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
    val program = GuidedPrograms.fastingShift21
    if (!program.isPublished) {
        SecondaryScaffold(title = stringResource(R.string.program_preview_title), onBack = onBack) {
            val firstDay = program.freePreview
            if (firstDay != null) {
                ProgramReadingCard(firstDay)
            } else {
                FreeReflection()
            }
            Text(stringResource(R.string.program_in_preparation), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val loaded by vm.state.collectAsStateWithLifecycle()
    SecondaryScaffold(title = stringResource(R.string.program_full_title), onBack = onBack) {
        val st = loaded
        if (st == null) {
            LoadingBox()
        } else if (!st.available) {
            program.freePreview?.let { ProgramReadingCard(it) }
            AppCard { Text(stringResource(R.string.program_locked), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.plan_see_plus), onClick = onUpgrade)
        } else if (!st.started) {
            AppCard { Text(stringResource(R.string.program_released_intro), style = MaterialTheme.typography.bodyLarge) }
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
                    ProgramReadingCard(day)
                    PrimaryButton(stringResource(R.string.program_complete), onClick = { vm.complete(day.day) }, haptic = true)
                }
            }
            PlainButton(stringResource(R.string.program_stop), onClick = vm::stop, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun FreeReflection() {
    AppCard {
        Text(stringResource(R.string.program_excerpt), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.program_excerpt_credit), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard {
        Text(stringResource(R.string.program_sample_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.program_sample_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.program_sample_prompt), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProgramReadingCard(day: ProgramDay) {
    val reading = day.reading(LocalConfiguration.current.locales[0].language) ?: return
    AppCard {
        Text(stringResource(R.string.program_day, day.day, reading.title), style = MaterialTheme.typography.titleLarge)
        Text(reading.lesson, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.program_action_label), style = MaterialTheme.typography.titleSmall)
        Text(reading.action, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.program_reflection_label), style = MaterialTheme.typography.titleSmall)
        Text(reading.reflection, style = MaterialTheme.typography.bodyLarge)
        day.targetHours?.let { target ->
            Text(pluralStringResource(R.plurals.program_target, target, target), style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.program_chapter, reading.source), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Insights ----------

internal sealed interface InsightsState {
    data object Example : InsightsState

    data class Personal(
        val review: List<ReviewWeek>,
        val consistency: List<WeeklyConsistency>,
        val byWeekday: Map<DayOfWeek, Duration>,
        /** Null when weight display is disabled. */
        val weight: List<WeightVsFasting>?,
        val weightUnit: WeightUnit,
    ) : InsightsState
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun insightStates(
    entitlements: Flow<Entitlement>,
    settings: Flow<AppSettings>,
    fasts: () -> Flow<List<Fast>>,
    weights: () -> Flow<List<WeightLog>>,
    clock: Clock,
): Flow<InsightsState> {
    val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }
    return entitlements.map { Feature.INSIGHTS.isAvailable(it) }.distinctUntilChanged().flatMapLatest { paid ->
        if (!paid) flowOf(InsightsState.Example)
        else settings.flatMapLatest { preferences ->
            val weightLogs = if (preferences.hideWeight) flowOf(emptyList()) else weights()
            combine(fasts(), weightLogs, ticks) { records, weight, now ->
                val zone = ZoneId.systemDefault()
                val today = now.atZone(zone).toLocalDate()
                InsightsState.Personal(
                    review = ScheduleReview.finishedWeeks(records, preferences.planHistory, preferences.weekReflections, zone, today),
                    consistency = Insights.weeklyConsistency(records, zone, today, 8, preferences.planHistory::isPlanned),
                    byWeekday = Insights.averageByWeekday(records, zone),
                    weight = if (preferences.hideWeight) null else Insights.weightVsFasting(records, weight, zone, now).takeLast(8),
                    weightUnit = preferences.weightUnit,
                )
            }
        }
    }
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    fasts: FastRepository,
    logs: LogRepository,
    settings: SettingsRepository,
    billing: BillingManager,
    clock: Clock,
) : ViewModel() {
    internal val state = insightStates(billing.entitlement, settings.settings, fasts::observeAll, logs::observeWeights, clock)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), null)
}

@Composable
fun InsightsRoute(onBack: () -> Unit, onUpgrade: () -> Unit, onOpenWeek: (LocalDate?) -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val upcoming = if (loaded is InsightsState.Personal) {
        val planner by hiltViewModel<SchedulesViewModel>().state.collectAsStateWithLifecycle()
        remember(planner) {
            planner?.let { week ->
                (0L..6L).map { week.today.plusDays(it) }
                    .filter { day -> week.starts(day, original = true).isNotEmpty() }
                    .associateBy { it.dayOfWeek }
            }.orEmpty()
        }
    } else emptyMap()
    InsightsScreen(loaded, onBack, onUpgrade, onOpenWeek, upcoming) { BodySignalsRoute() }
}

@Composable
internal fun InsightsScreen(st: InsightsState?, onBack: () -> Unit, onUpgrade: () -> Unit, onOpenWeek: (LocalDate?) -> Unit,
    upcoming: Map<DayOfWeek, LocalDate> = emptyMap(),
    bodySignals: @Composable () -> Unit) {
    SecondaryScaffold(title = stringResource(R.string.insights_title), onBack = onBack) {
        when (st) {
            null -> LoadingBox()
            InsightsState.Example -> {
                ScheduleReviewSection(reviewExample, example = true, onOpenWeek = onOpenWeek)
                Text(stringResource(R.string.insights_locked), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.plan_see_plus), onClick = onUpgrade)
            }
            is InsightsState.Personal -> {
                ScheduleReviewSection(st.review, example = false, onOpenWeek = onOpenWeek, upcoming = upcoming)
                AppCard {
                    Text(stringResource(R.string.insights_consistency), style = MaterialTheme.typography.titleMedium)
                    Bars(st.consistency)
                    Text(stringResource(R.string.insights_consistency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val locale = LocalConfiguration.current.locales[0]
                InsetGroup(header = stringResource(R.string.insights_weekday)) {
                    DayOfWeek.entries.forEachIndexed { i, day ->
                        if (i > 0) GroupDivider()
                        GroupRow(title = day.getDisplayName(TextStyle.FULL, locale), value = st.byWeekday[day]?.hoursMinutes() ?: "—")
                    }
                }
                st.weight?.let { weight ->
                    val f = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
                    InsetGroup(header = stringResource(R.string.insights_weight), footer = stringResource(R.string.insights_weight_note)) {
                        weight.forEachIndexed { i, w ->
                            if (i > 0) GroupDivider()
                            GroupRow(
                                title = w.weekStart.format(f),
                                subtitle = com.fastingshift.app.ui.components.hoursLabel(w.fastingHours.toLong()),
                                value = w.averageWeightKg?.let { kg -> weightText(convertWeight(kg, WeightUnit.KG, st.weightUnit), st.weightUnit) } ?: "—",
                            )
                        }
                    }
                }
                bodySignals()
            }
        }
    }
}

@Composable
private fun Bars(weeks: List<WeeklyConsistency>) {
    val locale = LocalConfiguration.current.locales[0]
    val format = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
    val values = weeks.map { it.ratio }
    val description = weeks.map { week ->
        val result = if (week.plannedDays == 0) stringResource(R.string.history_no_planned_days)
            else pluralStringResource(R.plurals.review_days_done, week.plannedDays, week.completedDays, week.plannedDays)
        week.weekStart.format(format) + ", " + result
    }.joinToString(". ")
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barW = slot * 0.5f
        val radius = CornerRadius(barW / 2, barW / 2)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
            val h = size.height * v.coerceIn(0f, 1f)
            if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), radius)
        }
    }
    if (weeks.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(weeks.first().weekStart.format(format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(weeks.last().weekStart.format(format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
