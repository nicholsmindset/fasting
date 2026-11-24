package com.fastingshift.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.latestKg
import com.fastingshift.app.ui.components.startOfTodayFlow
import com.fastingshift.app.ui.components.waterLabel
import com.fastingshift.app.ui.components.weightText
import com.fastingshift.app.ui.components.weightUnitLabel
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.LargeTitle
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SegmentedControl
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.timer.Today
import com.fastingshift.app.ui.timer.electrolyteLabel
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.hydration.WaterGoal
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HealthRetryStatus { IDLE, RUNNING, FAILED }

data class LogState(
    val hideWeight: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weights: List<WeightLog> = emptyList(),
    val waterTodayMl: Double = 0.0,
    val electrolytesToday: Map<Electrolyte, Double> = emptyMap(),
    val waterEntries: List<WaterLog> = emptyList(),
    val electrolyteEntries: List<ElectrolyteLog> = emptyList(),
    /** The user's goal, or the wellness default from their latest weight. */
    val waterGoalMl: Int = WaterGoal.FALLBACK_ML,
    /** Weight entries of the last 90 days, oldest first, for the trend chart. */
    val trend: List<WeightLog> = emptyList(),
    val now: Instant = Instant.EPOCH,
    val pendingHealthWrites: Int = 0,
    val healthRetryStatus: HealthRetryStatus = HealthRetryStatus.IDLE,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val logs: LogRepository,
    private val settings: SettingsRepository,
    private val health: HealthConnectManager,
    private val clock: Clock,
) : ViewModel() {
    private val retryStatus = MutableStateFlow(HealthRetryStatus.IDLE)
    private val syncState = combine(health.pendingWriteCount, retryStatus) { count, status -> count to status }

    val state: StateFlow<LogState> = startOfTodayFlow(clock).flatMapLatest { from ->
        combine(settings.settings, logs.observeWeights(), logs.observeWaterSince(from), logs.observeElectrolytesSince(from), syncState) { s, w, water, el, (count, status) ->
            val now = clock.instant()
            LogState(
                hideWeight = s.hideWeight,
                weightUnit = s.weightUnit,
                weights = w.take(10),
                waterTodayMl = water.sumOf(WaterGoal::toMl),
                electrolytesToday = el.groupBy { it.electrolyte }.mapValues { (_, l) -> l.sumOf { it.milligrams } },
                waterEntries = water.sortedByDescending { it.at },
                electrolyteEntries = el.sortedByDescending { it.at },
                waterGoalMl = Today.waterGoalMl(s.waterGoalMl, w.latestKg()),
                trend = weightTrendPoints(w, now),
                now = now,
                pendingHealthWrites = if (s.healthConnectSync) count else 0,
                healthRetryStatus = status,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    fun addWeight(value: Double, unit: WeightUnit) = viewModelScope.launch {
        settings.setWeightUnit(unit)
        val log = WeightLog(at = clock.instant(), value = value, unit = unit)
        val id = logs.addWeight(log)
        if (settings.current().healthConnectSync) health.writeWeight(log.copy(id = id))
    }

    fun addWater(ml: Double) = viewModelScope.launch {
        val log = WaterLog(at = clock.instant(), value = ml, unit = VolumeUnit.ML)
        val id = logs.addWater(log)
        if (settings.current().healthConnectSync) health.writeWater(log.copy(id = id))
    }

    fun addElectrolyte(e: Electrolyte, mg: Double) = viewModelScope.launch {
        logs.addElectrolyte(ElectrolyteLog(at = clock.instant(), electrolyte = e, milligrams = mg))
    }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { settings.setWeightUnit(unit) }
    fun deleteWeight(id: Long) = viewModelScope.launch {
        val entry = logs.getWeight(id)
        logs.deleteWeight(id)
        if (entry != null) health.forgetWeight(entry)
    }

    fun deleteWater(id: Long) = viewModelScope.launch {
        val entry = logs.getWater(id)
        logs.deleteWater(id)
        if (entry != null) health.forgetWater(entry)
    }

    fun retryHealthSync() = viewModelScope.launch {
        if (retryStatus.value == HealthRetryStatus.RUNNING) return@launch
        retryStatus.value = HealthRetryStatus.RUNNING
        try {
            health.retryPendingWrites()
        } finally {
            retryStatus.value = if (health.pendingWriteCount.value == 0) HealthRetryStatus.IDLE else HealthRetryStatus.FAILED
        }
    }

    fun deleteElectrolyte(id: Long) = viewModelScope.launch { logs.deleteElectrolyte(id) }
}

/** Everything the Log screen can ask for. */
class LogActions(
    val addWater: (Double) -> Unit = {},
    val addElectrolyte: (Electrolyte, Double) -> Unit = { _, _ -> },
    val addWeight: (Double, WeightUnit) -> Unit = { _, _ -> },
    val setWeightUnit: (WeightUnit) -> Unit = {},
    val deleteWater: (Long) -> Unit = {},
    val deleteElectrolyte: (Long) -> Unit = {},
    val deleteWeight: (Long) -> Unit = {},
    val retryHealthSync: () -> Unit = {},
)

@Composable
fun LogRoute(vm: LogViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    LogScreen(
        st,
        LogActions(
            addWater = { vm.addWater(it) }, addElectrolyte = { e, mg -> vm.addElectrolyte(e, mg) }, addWeight = { v, u -> vm.addWeight(v, u) },
            setWeightUnit = { vm.setWeightUnit(it) }, deleteWater = { vm.deleteWater(it) }, deleteElectrolyte = { vm.deleteElectrolyte(it) },
            deleteWeight = { vm.deleteWeight(it) }, retryHealthSync = { vm.retryHealthSync() },
        ),
        metrics = { MetricsRoute() },
    )
}

@Composable
fun LogScreen(st: LogState, actions: LogActions, metrics: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        LargeTitle(stringResource(R.string.log_title))
        if (st.pendingHealthWrites > 0) {
            AppCard(Modifier.testTag("health_sync_retry")) {
                Text(pluralStringResource(R.plurals.health_sync_pending, st.pendingHealthWrites, st.pendingHealthWrites))
                if (st.healthRetryStatus == HealthRetryStatus.FAILED) {
                    Text(stringResource(R.string.health_sync_check_access), style = MaterialTheme.typography.bodySmall)
                }
                if (st.healthRetryStatus == HealthRetryStatus.RUNNING) {
                    Text(stringResource(R.string.health_sync_retrying))
                } else {
                    SecondaryButton(stringResource(R.string.health_sync_retry), onClick = actions.retryHealthSync)
                }
            }
        }
        WaterSection(st, actions)
        ElectrolyteGroup(st, actions)
        // Glucose & ketones (MetricsSection.kt), separate state so this screen stays small.
        metrics()
        // Hide weight mode removes weight from every screen (SR-6), the trend chart included.
        if (!st.hideWeight) {
            WeightGroup(st, actions)
        }
    }
}

@Composable
private fun WaterSection(st: LogState, actions: LogActions) {
    val haptics = rememberHaptics()
    val progress = WaterGoal.progress(st.waterTodayMl, st.waterGoalMl)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().testTag("water_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.log_water), style = MaterialTheme.typography.titleMedium)
            val summary = stringResource(R.string.daily_water_a11y, st.waterTodayMl.toInt(), st.waterGoalMl)
            Column(Modifier.clearAndSetSemantics { contentDescription = summary }) {
                Text(
                    stringResource(R.string.unit_ml_value, st.waterTodayMl.toInt()),
                    style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.log_water_goal, waterLabel(st.waterGoalMl.toDouble())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (progress >= 1f) {
                Text(stringResource(R.string.log_water_goal_met), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(250, 500).forEach { ml ->
                    SecondaryButton(
                        stringResource(R.string.log_add_water, ml),
                        onClick = { haptics.tick(); actions.addWater(ml.toDouble()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (st.waterEntries.isNotEmpty()) {
            InsetGroup(header = stringResource(R.string.log_today_entries)) {
                st.waterEntries.forEachIndexed { i, w ->
                    if (i > 0) GroupDivider()
                    EntryRow(w.at.formatTime(), stringResource(R.string.unit_ml_value, WaterGoal.toMl(w).toInt())) { actions.deleteWater(w.id) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, value: String, onDelete: () -> Unit) {
    val delete = stringResource(R.string.delete)
    GroupRow(title = title, value = value) {
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "$delete $title", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ElectrolyteGroup(st: LogState, actions: LogActions) {
    var which by rememberSaveable { mutableStateOf(Electrolyte.SODIUM) }
    var amount by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InsetGroup(header = stringResource(R.string.log_electrolytes)) {
            Column(Modifier.selectableGroup()) {
                Electrolyte.entries.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        title = stringResource(electrolyteLabel(e)),
                        value = stringResource(R.string.unit_mg_value, (st.electrolytesToday[e] ?: 0.0).toInt()),
                        modifier = Modifier.selectable(selected = which == e, role = Role.RadioButton, onClick = { which = e }),
                        leading = { RadioButton(selected = which == e, onClick = null) },
                    )
                }
            }
            GroupDivider()
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.unit_mg)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                val milligrams = amount.toDoubleOrNull()?.takeIf { it > 0 }
                SecondaryButton(
                    stringResource(R.string.log_add),
                    onClick = { milligrams?.let { actions.addElectrolyte(which, it); amount = "" } },
                    enabled = milligrams != null,
                    modifier = Modifier.weight(0.6f).padding(top = 6.dp),
                )
            }
        }
        if (st.electrolyteEntries.isNotEmpty()) {
            InsetGroup {
                st.electrolyteEntries.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    EntryRow("${e.at.formatTime()} · ${stringResource(electrolyteLabel(e.electrolyte))}", stringResource(R.string.unit_mg_value, e.milligrams.toInt())) {
                        actions.deleteElectrolyte(e.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightGroup(st: LogState, actions: LogActions) {
    var value by rememberSaveable { mutableStateOf("") }
    val unit = st.weightUnit
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.fastingshift.app.ui.design.GroupHeader(stringResource(R.string.log_weight))
        AppCard {
            // The chosen unit is remembered for next time.
            SegmentedControl(WeightUnit.entries.map { weightUnitLabel(it) }, WeightUnit.entries.indexOf(unit), { actions.setWeightUnit(WeightUnit.entries[it]) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = { value = it.take(6) }, singleLine = true, label = { Text(weightUnitLabel(unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                val weight = value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..900.0 }
                SecondaryButton(
                    stringResource(R.string.log_add),
                    enabled = weight != null,
                    onClick = { weight?.let { actions.addWeight(it, unit); value = "" } },
                    modifier = Modifier.weight(0.6f).padding(top = 6.dp),
                )
            }
        }
        WeightTrendCard(st.trend, unit, st.now)
        if (st.weights.isNotEmpty()) {
            InsetGroup {
                st.weights.take(5).forEachIndexed { i, w ->
                    if (i > 0) GroupDivider()
                    EntryRow(w.at.formatDate(), weightText(convertWeight(w.value, w.unit, unit), unit)) { actions.deleteWeight(w.id) }
                }
            }
        }
    }
}
