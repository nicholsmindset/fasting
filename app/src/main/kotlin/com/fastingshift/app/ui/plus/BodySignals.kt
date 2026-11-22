package com.fastingshift.app.ui.plus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.log.formatMetric
import com.fastingshift.app.ui.log.unitLabel
import com.fastingshift.data.repo.MetricRepository
import com.fastingshift.domain.insights.BodySignals
import com.fastingshift.domain.insights.BodySignalsReport
import com.fastingshift.domain.insights.MetricReadings
import com.fastingshift.domain.insights.RestingHeartRateSample
import com.fastingshift.domain.insights.SleepSession
import com.fastingshift.domain.insights.WeeklyRestingHeartRate
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import com.fastingshift.domain.service.FastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Health Connect gave us for body signals (nothing is stored by the app). */
private data class HealthSignals(
    val available: Boolean = false,
    val granted: Boolean = false,
    val sleep: List<SleepSession> = emptyList(),
    val restingHeartRate: List<RestingHeartRateSample> = emptyList(),
)

data class BodySignalsState(
    /** Health Connect is installed and usable on this device. */
    val healthAvailable: Boolean = false,
    /** Sleep and resting heart rate may be read. */
    val granted: Boolean = false,
    val report: BodySignalsReport = BodySignalsReport(),
    val glucoseUnit: MetricUnit = MetricUnit.MMOL_L,
)

@HiltViewModel
class BodySignalsViewModel @Inject constructor(
    fasts: FastRepository,
    metrics: MetricRepository,
    val health: HealthConnectManager,
    private val clock: Clock,
) : ViewModel() {
    private val signals = MutableStateFlow(HealthSignals())

    init {
        refresh()
    }

    val state: StateFlow<BodySignalsState> = combine(fasts.observeAll(), metrics.observeAll(), signals) { f, m, hc ->
        BodySignalsState(
            healthAvailable = hc.available,
            granted = hc.granted,
            report = BodySignals.report(f, hc.sleep, hc.restingHeartRate, m, ZoneId.systemDefault(), clock.instant()),
            glucoseUnit = MetricReadings.defaultGlucoseUnit(m.firstOrNull { it.type == MetricType.GLUCOSE }, Locale.getDefault().country),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodySignalsState())

    /** Re-reads Health Connect, e.g. after the user answers the permission request. */
    fun refresh() = viewModelScope.launch {
        val available = health.isAvailable()
        val granted = available && health.hasPermissions(health.bodySignalPermissions)
        if (!granted) {
            signals.value = HealthSignals(available = available)
            return@launch
        }
        val since = clock.instant().minus(LOOKBACK)
        signals.value = HealthSignals(true, true, health.readSleep(since), health.readRestingHeartRate(since))
    }

    private companion object {
        val LOOKBACK: Duration = Duration.ofDays(120)
    }
}

/** "Body signals" in Shift+ insights. Shown only inside the unlocked insights screen. */
@Composable
fun BodySignalsRoute(vm: BodySignalsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(vm.health.permissionContract()) { vm.refresh() }
    BodySignalsSection(st, onGrant = { launcher.launch(vm.health.bodySignalPermissions) })
}

@Composable
fun BodySignalsSection(st: BodySignalsState, onGrant: () -> Unit) {
    Column(Modifier.testTag("body_signals"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InsetGroup(header = stringResource(R.string.body_title), footer = stringResource(R.string.body_correlation_note)) {
            if (st.granted) {
                SleepRow(st.report)
                GroupDivider()
                HeartRateRow(st.report)
                GroupDivider()
            }
            GlucoseRow(st.report, st.glucoseUnit)
        }
        st.report.restingHeartRate?.let { HeartRateTrend(it.weekly.takeLast(TREND_WEEKS)) }
        if (!st.granted) {
            AppCard(Modifier.testTag("body_signals_empty")) {
                Text(stringResource(R.string.body_connect_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.body_connect_body), style = MaterialTheme.typography.bodyMedium)
                if (st.healthAvailable) {
                    SecondaryButton(stringResource(R.string.body_connect_button), onClick = onGrant)
                } else {
                    Text(stringResource(R.string.body_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SleepRow(report: BodySignalsReport) {
    val c = report.sleep
    GroupRow(
        title = stringResource(R.string.body_sleep),
        subtitle = if (c == null) stringResource(R.string.body_sleep_need, BodySignals.MIN_NIGHTS) else stringResource(R.string.body_sleep_counts, c.fastingNights, c.otherNights),
        value = c?.let { stringResource(R.string.body_vs, it.afterFastingDay.hoursMinutes(), it.otherwise.hoursMinutes()) } ?: "—",
    )
}

@Composable
private fun HeartRateRow(report: BodySignalsReport) {
    val c = report.restingHeartRate
    GroupRow(
        title = stringResource(R.string.body_heart_rate),
        subtitle = if (c == null) stringResource(R.string.body_heart_rate_need, BodySignals.MIN_WEEKS) else stringResource(R.string.body_heart_rate_counts, c.fastingWeeks, c.otherWeeks),
        value = c?.let { stringResource(R.string.body_vs, bpm(it.fastingAverageBpm), bpm(it.otherAverageBpm)) } ?: "—",
    )
}

@Composable
private fun GlucoseRow(report: BodySignalsReport, unit: MetricUnit) {
    val c = report.glucose
    GroupRow(
        title = stringResource(R.string.body_glucose),
        subtitle = if (c == null) stringResource(R.string.body_glucose_need, BodySignals.MIN_GLUCOSE_READINGS) else stringResource(R.string.body_glucose_counts, c.duringFastReadings, c.outsideReadings),
        value = c?.let {
            val during = formatMetric(MetricReadings.glucoseIn(it.duringFastMmolPerL, unit), unit)
            val outside = formatMetric(MetricReadings.glucoseIn(it.outsideMmolPerL, unit), unit)
            stringResource(R.string.metrics_value_unit, stringResource(R.string.body_vs, during, outside), unitLabel(unit))
        } ?: "—",
    )
}

@Composable
private fun bpm(value: Double): String = stringResource(R.string.body_bpm_value, String.format(Locale.getDefault(), "%.0f", value))

/** Weekly resting heart rate; fasting weeks in the accent colour. */
@Composable
private fun HeartRateTrend(weeks: List<WeeklyRestingHeartRate>) {
    if (weeks.isEmpty()) return
    val fastingColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.outlineVariant
    val a11y = stringResource(R.string.body_trend_a11y, weeks.size)
    AppCard {
        Text(stringResource(R.string.body_trend_title), style = MaterialTheme.typography.titleSmall)
        val min = weeks.minOf { it.averageBpm } - 5
        val max = weeks.maxOf { it.averageBpm } + 5
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 4.dp).clearAndSetSemantics { contentDescription = a11y }) {
            val slot = size.width / weeks.size
            val barW = slot * 0.5f
            weeks.forEachIndexed { i, w ->
                val h = (size.height * ((w.averageBpm - min) / (max - min)).toFloat()).coerceAtLeast(barW)
                drawRoundRect(
                    if (w.fastingWeek) fastingColor else otherColor,
                    Offset(i * slot + (slot - barW) / 2, size.height - h),
                    Size(barW, h),
                    CornerRadius(barW / 2, barW / 2),
                )
            }
        }
        Text(stringResource(R.string.body_trend_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val TREND_WEEKS = 12
