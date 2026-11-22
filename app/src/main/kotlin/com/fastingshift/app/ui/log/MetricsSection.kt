package com.fastingshift.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupHeader
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SegmentedControl
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.domain.insights.MetricReadings
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MetricsState(
    /** Most recent readings first. */
    val readings: List<MetricLog> = emptyList(),
    val glucoseUnit: MetricUnit = MetricUnit.MMOL_L,
    /** The latest glucose reading is low and recent: suggest breaking the fast. */
    val lowGlucose: Boolean = false,
    /** The latest ketone reading is high and recent: suggest checking in with a clinician. */
    val highKetone: Boolean = false,
    /** Just deleted, can be put back. */
    val lastDeleted: MetricLog? = null,
)

/** Everything the glucose & ketones section can ask for. */
class MetricActions(
    val add: (MetricType, Double, MetricUnit) -> Unit = { _, _, _ -> },
    val setGlucoseUnit: (MetricUnit) -> Unit = {},
    val delete: (MetricLog) -> Unit = {},
    val undoDelete: () -> Unit = {},
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val metrics: MetricRepository,
    private val clock: Clock,
) : ViewModel() {
    private val chosenUnit = MutableStateFlow<MetricUnit?>(null)
    private val lastDeleted = MutableStateFlow<MetricLog?>(null)
    private var undoTimeout: Job? = null

    val state: StateFlow<MetricsState> = combine(metrics.observeAll(), chosenUnit, lastDeleted) { all, chosen, deleted ->
        val now = clock.instant()
        val latestGlucose = all.firstOrNull { it.type == MetricType.GLUCOSE }
        val latestKetone = all.firstOrNull { it.type == MetricType.KETONE }
        fun recent(log: MetricLog?) = log != null && Duration.between(log.at, now) <= ALERT_WINDOW
        MetricsState(
            readings = all.take(RECENT),
            // Remembered without settings: the unit of the latest glucose reading, else the locale.
            glucoseUnit = chosen ?: MetricReadings.defaultGlucoseUnit(latestGlucose, Locale.getDefault().country),
            lowGlucose = recent(latestGlucose) && MetricReadings.isLowGlucose(latestGlucose!!),
            highKetone = recent(latestKetone) && MetricReadings.isHighKetone(latestKetone!!),
            lastDeleted = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricsState(glucoseUnit = MetricReadings.defaultGlucoseUnit(null, Locale.getDefault().country)))

    fun add(type: MetricType, value: Double, unit: MetricUnit) = viewModelScope.launch {
        val safeUnit = if (type == MetricType.KETONE) MetricUnit.MMOL_L else unit
        if (!MetricReadings.isValid(type, safeUnit, value)) return@launch
        clearUndo()
        metrics.add(MetricLog(at = clock.instant(), type = type, value = value, unit = safeUnit))
    }

    fun setGlucoseUnit(unit: MetricUnit) = chosenUnit.update { unit }

    fun delete(log: MetricLog) = viewModelScope.launch {
        metrics.delete(log.id)
        lastDeleted.value = log
        undoTimeout?.cancel()
        undoTimeout = viewModelScope.launch { delay(UNDO_MS); lastDeleted.value = null }
    }

    fun undoDelete() = viewModelScope.launch {
        val log = lastDeleted.value ?: return@launch
        clearUndo()
        metrics.add(log)
    }

    private fun clearUndo() {
        undoTimeout?.cancel()
        lastDeleted.value = null
    }

    private companion object {
        const val RECENT = 10
        const val UNDO_MS = 8_000L
        val ALERT_WINDOW: Duration = Duration.ofHours(6)
    }
}

@Composable
fun MetricsRoute(vm: MetricsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    MetricsSection(
        st,
        MetricActions(
            add = { t, v, u -> vm.add(t, v, u) },
            setGlucoseUnit = vm::setGlucoseUnit,
            delete = { vm.delete(it) },
            undoDelete = { vm.undoDelete() },
        ),
    )
}

/** Glucose & ketones on the Log screen: quick add, safety cards and recent readings. */
@Composable
fun MetricsSection(st: MetricsState, actions: MetricActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupHeader(stringResource(R.string.metrics_title))
        MetricEntryCard(st, actions)
        if (st.lowGlucose) LowGlucoseCard()
        if (st.highKetone) HighKetoneCard()
        st.lastDeleted?.let { deleted ->
            AppCard(Modifier.testTag("metric_undo")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.metrics_deleted, metricValueText(deleted, st.glucoseUnit)), modifier = Modifier.weight(1f))
                    PlainButton(stringResource(R.string.metrics_undo), onClick = actions.undoDelete)
                }
            }
        }
        InsetGroup(footer = stringResource(R.string.metrics_ranges_note)) {
            if (st.readings.isEmpty()) {
                GroupRow(title = stringResource(R.string.metrics_empty))
            }
            st.readings.forEachIndexed { i, log ->
                if (i > 0) GroupDivider()
                MetricRow(log, st.glucoseUnit, onDelete = { actions.delete(log) })
            }
        }
    }
}

@Composable
private fun MetricEntryCard(st: MetricsState, actions: MetricActions) {
    val haptics = rememberHaptics()
    var type by rememberSaveable { mutableStateOf(MetricType.GLUCOSE) }
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val unit = if (type == MetricType.KETONE) MetricUnit.MMOL_L else st.glucoseUnit
    AppCard {
        SegmentedControl(
            listOf(stringResource(R.string.metrics_glucose), stringResource(R.string.metrics_ketones)),
            MetricType.entries.indexOf(type),
            { type = MetricType.entries[it]; invalid = false },
        )
        if (type == MetricType.GLUCOSE) {
            SegmentedControl(
                MetricUnit.entries.map { unitLabel(it) },
                MetricUnit.entries.indexOf(st.glucoseUnit),
                { actions.setGlucoseUnit(MetricUnit.entries[it]); invalid = false },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5); invalid = false },
                label = { Text(unitLabel(unit)) },
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("metric_input"),
            )
            SecondaryButton(
                stringResource(R.string.log_add),
                onClick = {
                    val value = MetricReadings.parse(text, type, unit)
                    if (value == null) {
                        invalid = true
                    } else {
                        haptics.tick()
                        actions.add(type, value, unit)
                        text = ""
                    }
                },
                modifier = Modifier.weight(0.6f).padding(top = 6.dp).testTag("metric_add"),
            )
        }
        if (invalid) {
            val range = MetricReadings.range(type, unit)!!
            Text(
                stringResource(R.string.metrics_invalid, formatMetric(range.start, unit), formatMetric(range.endInclusive, unit), unitLabel(unit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(if (type == MetricType.KETONE) R.string.metrics_ketones_local else R.string.metrics_glucose_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LowGlucoseCard() {
    AppCard(Modifier.testTag("metric_low_glucose")) {
        Text(stringResource(R.string.metrics_low_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.metrics_low_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HighKetoneCard() {
    AppCard(Modifier.testTag("metric_high_ketone")) {
        Text(stringResource(R.string.metrics_high_ketone_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.metrics_high_ketone_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricRow(log: MetricLog, glucoseUnit: MetricUnit, onDelete: () -> Unit) {
    val name = stringResource(if (log.type == MetricType.GLUCOSE) R.string.metrics_glucose else R.string.metrics_ketones)
    val title = "${log.at.formatDateTime()} · $name"
    val delete = stringResource(R.string.delete)
    GroupRow(title = title, value = metricValueText(log, glucoseUnit), modifier = Modifier.testTag("metric_row")) {
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "$delete $title", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

/** A reading in the user's glucose unit (ketones are always mmol/L), e.g. "5.4 mmol/L". */
@Composable
fun metricValueText(log: MetricLog, glucoseUnit: MetricUnit): String {
    val unit = if (log.type == MetricType.KETONE) MetricUnit.MMOL_L else glucoseUnit
    return stringResource(R.string.metrics_value_unit, formatMetric(MetricReadings.valueIn(log, unit), unit), unitLabel(unit))
}

/** mg/dL as a whole number, mmol/L to one decimal, in the user's locale. */
fun formatMetric(value: Double, unit: MetricUnit): String =
    if (unit == MetricUnit.MG_DL) String.format(Locale.getDefault(), "%.0f", value) else String.format(Locale.getDefault(), "%.1f", value)

@Composable
fun unitLabel(unit: MetricUnit): String = stringResource(if (unit == MetricUnit.MG_DL) R.string.unit_mg_dl else R.string.unit_mmol_l)
