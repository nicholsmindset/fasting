package com.fastingshift.app.ui.plus

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.PlanOffer
import com.fastingshift.app.ui.components.SectionCard
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
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var offers by remember { mutableStateOf<List<PlanOffer>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ent by vm.billing.entitlement.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { offers = vm.billing.offers() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.headlineSmall)
        listOf(R.string.paywall_point_program, R.string.paywall_point_insights).forEach { Text("• " + stringResource(it)) }
        Text(stringResource(R.string.paywall_free_forever), style = MaterialTheme.typography.bodySmall)
        when {
            ent.isShiftPlus -> Text(stringResource(R.string.paywall_already), color = MaterialTheme.colorScheme.primary)
            !vm.billing.isConfigured -> Text(stringResource(R.string.paywall_unavailable))
            offers == null -> Text(stringResource(R.string.loading))
            offers!!.isEmpty() -> Text(stringResource(R.string.paywall_unavailable))
            else -> offers!!.forEach { offer ->
                val label = stringResource(
                    when (offer.plan) {
                        PlanType.ANNUAL -> R.string.plan_annual
                        PlanType.MONTHLY -> R.string.plan_monthly
                        PlanType.LIFETIME -> R.string.plan_lifetime
                        PlanType.FREE -> R.string.plan_free
                    },
                )
                Button(onClick = { scope.launch { error = vm.billing.purchase(activity, offer); if (error == null && vm.billing.entitlement.value.isShiftPlus) onClose() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("$label · ${offer.price}" + if (offer.hasTrial) " · " + stringResource(R.string.paywall_trial_note) else "")
                }
            }
        }
        Text(stringResource(R.string.paywall_terms), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Paywall rule 1: leaving is as easy as buying.
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_offer_free)) }
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramState())

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
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(GuidedPrograms.fastingShift21.name, style = MaterialTheme.typography.headlineSmall)
        if (!st.available) {
            Text(stringResource(R.string.program_locked))
            Button(onClick = onUpgrade) { Text(stringResource(R.string.plan_see_plus)) }
        } else if (!st.started) {
            Text(stringResource(R.string.program_intro))
            Button(onClick = vm::start) { Text(stringResource(R.string.program_start)) }
        } else {
            when {
                st.finished -> Text(stringResource(R.string.program_finished))
                st.today == null -> Text(stringResource(R.string.program_come_back))
                else -> st.today?.let { day ->
                    SectionCard(stringResource(R.string.program_day, day.day, day.title)) {
                        Text(day.lesson)
                        Text(stringResource(R.string.program_target, day.targetHours))
                        day.chapter?.let { Text(stringResource(R.string.program_chapter, it), style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = { vm.complete(day.day) }) { Text(stringResource(R.string.program_complete)) }
                    }
                }
            }
            Text(stringResource(R.string.program_progress, st.done.size, vm.days.size))
            TextButton(onClick = vm::stop) { Text(stringResource(R.string.program_stop)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

// ---------- Insights ----------

data class InsightsState(
    val available: Boolean = false,
    val consistency: List<WeeklyConsistency> = emptyList(),
    val byWeekday: Map<DayOfWeek, Duration> = emptyMap(),
    val weight: List<WeightVsFasting> = emptyList(),
    val hideWeight: Boolean = false,
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())
}

@Composable
fun InsightsRoute(onBack: () -> Unit, onUpgrade: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineSmall)
        if (!st.available) {
            Text(stringResource(R.string.insights_locked))
            Button(onClick = onUpgrade) { Text(stringResource(R.string.plan_see_plus)) }
        } else {
            SectionCard(stringResource(R.string.insights_consistency)) {
                Bars(st.consistency.map { it.ratio })
                Text(stringResource(R.string.insights_consistency_note), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.insights_weekday)) {
                DayOfWeek.entries.forEach { d ->
                    val avg = st.byWeekday[d]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.width(48.dp))
                        Text(avg?.let { "${it.toHours()}h ${it.toMinutes() % 60}m" } ?: "—")
                    }
                }
            }
            if (!st.hideWeight) {
                SectionCard(stringResource(R.string.insights_weight)) {
                    st.weight.forEach { w ->
                        Text(stringResource(R.string.insights_weight_row, w.weekStart.toString(), w.fastingHours.toInt(), w.averageWeightKg?.let { "%.1f kg".format(it) } ?: "—"))
                    }
                    Text(stringResource(R.string.insights_weight_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun Bars(values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barW = slot * 0.6f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2
            drawRect(track, Offset(x, 0f), Size(barW, size.height))
            val h = size.height * v.coerceIn(0f, 1f)
            drawRect(color, Offset(x, size.height - h), Size(barW, h))
        }
    }
}
