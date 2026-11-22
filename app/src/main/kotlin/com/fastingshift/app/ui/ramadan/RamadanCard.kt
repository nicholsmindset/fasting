package com.fastingshift.app.ui.ramadan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.FastingTimeSource
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.RamadanPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Timer's Ramadan card needs; [Hidden] when Ramadan mode is off. */
sealed interface RamadanCardState {
    data object Hidden : RamadanCardState
    data object NeedsCity : RamadanCardState
    data class Active(
        val city: City,
        val method: PrayerMethod,
        val status: RamadanStatus,
        val fajr: Instant,
        val maghrib: Instant,
        val fastingTimeSource: FastingTimeSource,
    ) : RamadanCardState
}

@HiltViewModel
class RamadanCardViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Ticks on each minute boundary so the countdown stays live without per-second work. */
    private val minutes = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(60_000 - now.toEpochMilli() % 60_000)
        }
    }

    val state: StateFlow<RamadanCardState> = combine(settings.settings, minutes) { s, now ->
        if (!s.ramadanEnabled) return@combine RamadanCardState.Hidden
        val city = Cities.byId(s.ramadanCityId) ?: return@combine RamadanCardState.NeedsCity
        val method = s.ramadanMethod ?: Cities.defaultMethod(city)
        val phase = RamadanPlanner.phase(city, method, now)
        val status = RamadanText.status(phase, now)
        // The bar runs Fajr to Maghrib of the day being fasted (or about to be).
        val (fajr, maghrib) = when (phase) {
            is com.fastingshift.domain.ramadan.RamadanPhase.Fasting -> phase.suhoorEnded to phase.iftar
            is com.fastingshift.domain.ramadan.RamadanPhase.Eating -> phase.suhoorEnds to phase.nextIftar
        }
        RamadanCardState.Active(city, method, status, fajr, maghrib, phase.fastingTimeSource)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RamadanCardState.Hidden)

    fun chooseCity(city: City) = viewModelScope.launch { settings.setRamadanCity(city.id, Cities.defaultMethod(city)) }
}

/**
 * Ramadan mode card for the Timer: "Iftar in 3h 12m" while fasting, "Suhoor ends 5:41 AM"
 * while eating. Shows nothing when Ramadan mode is off.
 */
@Composable
fun RamadanCard(vm: RamadanCardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    when (val s = state) {
        RamadanCardState.Hidden -> Unit
        RamadanCardState.NeedsCity -> AppCard {
            Header(stringResource(R.string.ramadan_mode_title))
            Text(stringResource(R.string.ramadan_choose_city_body), style = MaterialTheme.typography.bodyMedium)
            SecondaryButton(stringResource(R.string.ramadan_choose_city), onClick = { picking = true })
        }
        is RamadanCardState.Active -> ActiveCard(s)
    }
    if (state != RamadanCardState.Hidden) QadaLedgerCard()
    if (picking) {
        CityPickerSheet(selectedId = null, onPick = { vm.chooseCity(it); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
private fun ActiveCard(s: RamadanCardState.Active) {
    val zone = s.city.zone
    AppCard {
        Header(stringResource(R.string.ramadan_mode_city, s.city.name))
        when (val st = s.status) {
            is RamadanStatus.Fasting -> {
                Text(stringResource(R.string.ramadan_iftar_in, countdownText(st.remaining)), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.ramadan_iftar_at, clockTime(st.iftar, zone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val percent = (st.progress * 100).toInt()
                val description = stringResource(R.string.a11y_ramadan_progress, percent)
                LinearProgressIndicator(
                    progress = { st.progress },
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).semantics { contentDescription = description },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Caption(stringResource(R.string.ramadan_fajr_at, clockTime(s.fajr, zone)))
                    Caption(stringResource(R.string.ramadan_maghrib_at, clockTime(s.maghrib, zone)))
                }
            }
            is RamadanStatus.Eating -> {
                Text(stringResource(R.string.ramadan_suhoor_ends, clockTime(st.suhoorEnds, zone)), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.ramadan_in, countdownText(st.remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val source = when (s.fastingTimeSource) {
            FastingTimeSource.MUIS_TIMETABLE ->
                stringResource(R.string.ramadan_source_muis_timetable, s.fajr.atZone(zone).year)
            FastingTimeSource.CALCULATED ->
                stringResource(R.string.ramadan_source_calculated, RamadanText.methodName(s.method))
        }
        Caption(source)
    }
}

@Composable
private fun Header(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crescent(size = 18.dp)
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A simple crescent moon, drawn so it needs no extra icon library. Decorative. */
@Composable
fun Crescent(size: Dp, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(size + 10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r * 0.92f)) }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(r * 0.42f, -r * 0.28f), r * 0.78f)) }
            drawPath(Path.combine(PathOperation.Difference, moon, bite), color)
        }
    }
}

@Composable
fun countdownText(c: Countdown): String =
    if (c.hours == 0L) stringResource(R.string.ramadan_minutes, c.minutes.toInt()) else stringResource(R.string.ramadan_hours_minutes, c.hours.toInt(), c.minutes.toInt())

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Clock time in the chosen city's zone (prayer times belong to the place, not the phone). */
fun clockTime(at: Instant, zone: ZoneId): String = timeFormat.format(at.atZone(zone))
