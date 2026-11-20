package com.fastingshift.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import com.fastingshift.app.ui.components.formatDate
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogState(
    val hideWeight: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weights: List<WeightLog> = emptyList(),
    val waterTodayMl: Double = 0.0,
    val electrolytesToday: Map<Electrolyte, Double> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val logs: LogRepository,
    private val settings: SettingsRepository,
    private val health: HealthConnectManager,
    private val clock: Clock,
) : ViewModel() {
    /** Today's totals start at local midnight when the screen opens. */
    private val startOfToday = flowOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())

    val state: StateFlow<LogState> = startOfToday.flatMapLatest { from ->
        combine(settings.settings, logs.observeWeights(), logs.observeWaterSince(from), logs.observeElectrolytesSince(from)) { s, w, water, el ->
            LogState(
                hideWeight = s.hideWeight,
                weights = w.take(10),
                waterTodayMl = water.sumOf { if (it.unit == VolumeUnit.ML) it.value else it.value * 29.5735 },
                electrolytesToday = el.groupBy { it.electrolyte }.mapValues { (_, l) -> l.sumOf { it.milligrams } },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    fun addWeight(value: Double, unit: WeightUnit) = viewModelScope.launch {
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

    fun deleteWeight(id: Long) = viewModelScope.launch { logs.deleteWeight(id) }
}

@Composable
fun LogRoute(vm: LogViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = stringResource(R.string.log_water)) {
            Text(stringResource(R.string.log_water_today, st.waterTodayMl.toInt()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(250, 500).forEach { ml -> OutlinedButton(onClick = { vm.addWater(ml.toDouble()) }) { Text("+$ml ml") } }
            }
        }
        ElectrolyteCard(st, vm)
        // Hide weight mode removes weight from every screen (SR-6).
        if (!st.hideWeight) WeightCard(st, vm)
    }
}

@Composable
private fun ElectrolyteCard(st: LogState, vm: LogViewModel) {
    var which by remember { mutableStateOf(Electrolyte.SODIUM) }
    var amount by remember { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.log_electrolytes)) {
        Electrolyte.entries.forEach { e ->
            Text(stringResource(R.string.log_electrolyte_today, stringResource(electrolyteLabel(e)), (st.electrolytesToday[e] ?: 0.0).toInt()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Electrolyte.entries.forEach { e -> FilterChip(selected = which == e, onClick = { which = e }, label = { Text(stringResource(electrolyteLabel(e))) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text("mg") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            Button(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { vm.addElectrolyte(which, it); amount = "" } }) {
                Text(stringResource(R.string.log_add))
            }
        }
    }
}

@Composable
private fun WeightCard(st: LogState, vm: LogViewModel) {
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(WeightUnit.KG) }
    SectionCard(title = stringResource(R.string.log_weight)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true, label = { Text(unit.name.lowercase()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            FilterChip(selected = unit == WeightUnit.KG, onClick = { unit = WeightUnit.KG }, label = { Text("kg") })
            FilterChip(selected = unit == WeightUnit.LB, onClick = { unit = WeightUnit.LB }, label = { Text("lb") })
        }
        Button(onClick = {
            value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..900.0 }?.let { vm.addWeight(it, unit); value = "" }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_add)) }
        st.weights.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${w.at.formatDate()}: ${"%.1f".format(w.value)} ${w.unit.name.lowercase()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.deleteWeight(w.id) }) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

private fun electrolyteLabel(e: Electrolyte) = when (e) {
    Electrolyte.SODIUM -> R.string.el_sodium
    Electrolyte.POTASSIUM -> R.string.el_potassium
    Electrolyte.MAGNESIUM -> R.string.el_magnesium
}
