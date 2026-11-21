package com.fastingshift.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.weightText
import com.fastingshift.app.ui.components.weightUnitLabel
import com.fastingshift.app.ui.timer.electrolyteLabel
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogState(
    val hideWeight: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weights: List<WeightLog> = emptyList(),
    val waterTodayMl: Double = 0.0,
    val electrolytesToday: Map<Electrolyte, Double> = emptyMap(),
    val waterEntries: List<WaterLog> = emptyList(),
    val electrolyteEntries: List<ElectrolyteLog> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val logs: LogRepository,
    private val settings: SettingsRepository,
    private val health: HealthConnectManager,
    private val clock: Clock,
) : ViewModel() {
    /** Local midnight, re-checked every minute so today's totals reset at midnight (and on zone changes). */
    private val startOfToday: Flow<Instant> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val now = clock.instant()
            val today = now.atZone(zone).toLocalDate()
            emit(today.atStartOfDay(zone).toInstant())
            val untilMidnight = Duration.between(now, today.plusDays(1).atStartOfDay(zone).toInstant()).toMillis()
            delay(untilMidnight.coerceIn(1_000, 60_000))
        }
    }.distinctUntilChanged()

    val state: StateFlow<LogState> = startOfToday.flatMapLatest { from ->
        combine(settings.settings, logs.observeWeights(), logs.observeWaterSince(from), logs.observeElectrolytesSince(from)) { s, w, water, el ->
            LogState(
                hideWeight = s.hideWeight,
                weightUnit = s.weightUnit,
                weights = w.take(10),
                waterTodayMl = water.sumOf { if (it.unit == VolumeUnit.ML) it.value else it.value * 29.5735 },
                electrolytesToday = el.groupBy { it.electrolyte }.mapValues { (_, l) -> l.sumOf { it.milligrams } },
                waterEntries = water.sortedByDescending { it.at },
                electrolyteEntries = el.sortedByDescending { it.at },
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
    fun deleteWeight(id: Long) = viewModelScope.launch { logs.deleteWeight(id) }
    fun deleteWater(id: Long) = viewModelScope.launch { logs.deleteWater(id) }
    fun deleteElectrolyte(id: Long) = viewModelScope.launch { logs.deleteElectrolyte(id) }
}

@Composable
fun LogRoute(vm: LogViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = stringResource(R.string.log_water)) {
            Text(stringResource(R.string.log_water_today, st.waterTodayMl.toInt()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(250, 500).forEach { ml -> OutlinedButton(onClick = { vm.addWater(ml.toDouble()) }) { Text(stringResource(R.string.log_add_water, ml)) } }
            }
            st.waterEntries.forEach { w ->
                val ml = if (w.unit == VolumeUnit.ML) w.value else w.value * 29.5735
                EntryRow("${w.at.formatTime()} · ${stringResource(R.string.unit_ml_value, ml.toInt())}") { vm.deleteWater(w.id) }
            }
        }
        ElectrolyteCard(st, vm)
        // Hide weight mode removes weight from every screen (SR-6).
        if (!st.hideWeight) WeightCard(st, vm)
    }
}

@Composable
private fun EntryRow(text: String, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElectrolyteCard(st: LogState, vm: LogViewModel) {
    var which by rememberSaveable { mutableStateOf(Electrolyte.SODIUM) }
    var amount by rememberSaveable { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.log_electrolytes)) {
        Electrolyte.entries.forEach { e ->
            Text(stringResource(R.string.log_electrolyte_today, stringResource(electrolyteLabel(e)), (st.electrolytesToday[e] ?: 0.0).toInt()))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Electrolyte.entries.forEach { e -> FilterChip(selected = which == e, onClick = { which = e }, label = { Text(stringResource(electrolyteLabel(e))) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.unit_mg)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            Button(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.addElectrolyte(which, it); amount = "" } }) {
                Text(stringResource(R.string.log_add))
            }
        }
        st.electrolyteEntries.forEach { e ->
            EntryRow("${e.at.formatTime()} · ${stringResource(electrolyteLabel(e.electrolyte))} ${stringResource(R.string.unit_mg_value, e.milligrams.toInt())}") {
                vm.deleteElectrolyte(e.id)
            }
        }
    }
}

@Composable
private fun WeightCard(st: LogState, vm: LogViewModel) {
    var value by rememberSaveable { mutableStateOf("") }
    val unit = st.weightUnit
    SectionCard(title = stringResource(R.string.log_weight)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value, onValueChange = { value = it.take(6) }, singleLine = true, label = { Text(weightUnitLabel(unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            // The chosen unit is remembered for next time.
            WeightUnit.entries.forEach { u -> FilterChip(selected = unit == u, onClick = { vm.setWeightUnit(u) }, label = { Text(weightUnitLabel(u)) }) }
        }
        Button(onClick = {
            value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..900.0 }?.let { vm.addWeight(it, unit); value = "" }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_add)) }
        st.weights.forEach { w ->
            EntryRow("${w.at.formatDate()}: ${weightText(convertWeight(w.value, w.unit, unit), unit)}") { vm.deleteWeight(w.id) }
        }
    }
}
