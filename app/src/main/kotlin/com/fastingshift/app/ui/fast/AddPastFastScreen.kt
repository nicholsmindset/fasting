package com.fastingshift.app.ui.fast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.onboarding.protocolDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.service.EditResult
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPastFastState(
    val protocol: Protocol,
    val start: Instant,
    val end: Instant,
    val error: Boolean = false,
    val saving: Boolean = false,
)

/** The earliest whole picker minute that still fits the rolling history window. */
private fun oldestStartMinute(now: Instant): Instant {
    val cutoff = now.minus(FastService.MAX_PAST_AGE)
    val minute = cutoff.truncatedTo(ChronoUnit.MINUTES)
    return if (minute == cutoff) minute else minute.plus(1, ChronoUnit.MINUTES)
}

/** Anchor the suggested end on the selected local day, even when the fast started the night before. */
internal fun pastFastEndOn(day: LocalDate, zone: ZoneId, now: Instant): Instant? {
    val oldest = oldestStartMinute(now)
    if (day < oldest.atZone(zone).toLocalDate() || day > now.atZone(zone).toLocalDate()) return null
    val dayStart = day.atStartOfDay(zone).toInstant()
    // At exactly midnight a fast ending now belongs to yesterday.
    val firstEnd = maxOf(oldest.plus(FastPolicy.MIN_FAST), dayStart.plus(1, ChronoUnit.MINUTES))
    val lastEnd = minOf(now.truncatedTo(ChronoUnit.MINUTES), day.plusDays(1).atStartOfDay(zone).toInstant().minus(1, ChronoUnit.MINUTES))
    if (firstEnd > lastEnd) return null
    return day.atTime(LocalTime.NOON).atZone(zone).toInstant().coerceIn(firstEnd, lastEnd)
}

internal fun canAddPastFastEndingOn(day: LocalDate, zone: ZoneId, now: Instant): Boolean =
    pastFastEndOn(day, zone, now) != null

/** Records a fast the user forgot to time (PRD F2). The draft survives process death. */
@HiltViewModel
class AddPastFastViewModel @Inject constructor(
    private val service: FastService,
    private val handle: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {
    private val _state: MutableStateFlow<AddPastFastState>
    val state: StateFlow<AddPastFastState> get() = _state

    private val dateArgument = handle.get<String>("date")
    val unavailableDate: Boolean

    init {
        val now = clock.instant()
        val dateEnd = dateArgument?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { pastFastEndOn(it, ZoneId.systemDefault(), now) }
        }
        val restoredEnd = handle.get<Long>(KEY_END)?.let(Instant::ofEpochMilli)
        unavailableDate = dateArgument != null && dateEnd == null && restoredEnd == null
        val protocol = handle.get<String>(KEY_PROTOCOL)?.let(Presets::byId) ?: Presets.W16_8
        val end = restoredEnd ?: dateEnd ?: now.truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(2))
        val suggestedStart = end.minus(Duration.ofHours(protocol.fastHours.toLong()))
        val start = handle.get<Long>(KEY_START)?.let(Instant::ofEpochMilli) ?: maxOf(suggestedStart, oldestStartMinute(now))
        _state = MutableStateFlow(AddPastFastState(protocol, start, end))
        if (!unavailableDate) persist(_state.value)
    }

    private fun persist(s: AddPastFastState) {
        handle[KEY_PROTOCOL] = s.protocol.id
        handle[KEY_START] = s.start.toEpochMilli()
        handle[KEY_END] = s.end.toEpochMilli()
    }

    private fun change(f: (AddPastFastState) -> AddPastFastState) = _state.update {
        if (it.saving) it else f(it).copy(error = false).also(::persist)
    }

    /** A day-specific draft keeps the chosen end. The general history form keeps its start. */
    fun select(p: Protocol) = change {
        val length = Duration.ofHours(p.fastHours.toLong())
        if (dateArgument == null) it.copy(protocol = p, end = it.start.plus(length))
        else it.copy(protocol = p, start = maxOf(it.end.minus(length), oldestStartMinute(clock.instant())))
    }
    fun setStart(i: Instant) = change { it.copy(start = i) }
    fun setEnd(i: Instant) = change { it.copy(end = i) }

    fun save(onDone: () -> Unit) {
        if (unavailableDate) return
        val s = _state.value
        if (s.saving || !_state.compareAndSet(s, s.copy(saving = true, error = false))) return
        viewModelScope.launch {
            val saved = try {
                service.addPastFast(s.protocol, s.start, s.end) is EditResult.Updated
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            // Keep a successful draft locked while its destination closes.
            if (saved) onDone() else _state.update { it.copy(saving = false, error = true) }
        }
    }

    private companion object {
        const val KEY_PROTOCOL = "protocol"
        const val KEY_START = "start"
        const val KEY_END = "end"
    }
}

@Composable
fun AddPastFastRoute(onDone: () -> Unit, vm: AddPastFastViewModel = hiltViewModel()) {
    if (vm.unavailableDate) {
        LaunchedEffect(vm) { onDone() }
        return
    }
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SecondaryScaffold(title = stringResource(R.string.add_past_title), onBack = onDone) {
        var pickProtocol by remember { mutableStateOf(false) }
        Text(stringResource(R.string.add_past_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InsetGroup {
            Box {
                GroupRow(stringResource(R.string.add_past_protocol), value = st.protocol.name, showChevron = true, onClick = { pickProtocol = true })
                DropdownMenu(expanded = pickProtocol, onDismissRequest = { pickProtocol = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                    Presets.all.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(protocolDescription(p)) },
                            onClick = { vm.select(p); pickProtocol = false },
                            trailingIcon = if (p.id == st.protocol.id) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        val length = Duration.between(st.start, st.end)
        InsetGroup(header = stringResource(R.string.add_past_times)) {
            GroupRow(stringResource(R.string.edit_start), value = st.start.formatDateTime(), showChevron = true, onClick = { pickDateTime(context, st.start, vm::setStart) })
            GroupDivider()
            GroupRow(stringResource(R.string.edit_end), value = st.end.formatDateTime(), showChevron = true, onClick = { pickDateTime(context, st.end, vm::setEnd) })
            if (!length.isNegative) {
                GroupDivider()
                GroupRow(stringResource(R.string.add_past_length), value = length.hoursMinutes())
            }
        }
        AnimatedVisibility(st.error) {
            Text(stringResource(R.string.add_past_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(stringResource(R.string.save), onClick = { vm.save(onDone) }, enabled = !st.saving, haptic = true)
    }
}
