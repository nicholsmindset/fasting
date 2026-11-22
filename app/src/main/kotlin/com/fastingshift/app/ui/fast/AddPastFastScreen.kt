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
import com.fastingshift.domain.service.EditResult
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPastFastState(
    val protocol: Protocol,
    val start: Instant,
    val end: Instant,
    val error: Boolean = false,
)

/** Records a fast the user forgot to time (PRD F2). The draft survives process death. */
@HiltViewModel
class AddPastFastViewModel @Inject constructor(
    private val service: FastService,
    private val handle: SavedStateHandle,
    clock: Clock,
) : ViewModel() {
    private val _state: MutableStateFlow<AddPastFastState>
    val state: StateFlow<AddPastFastState> get() = _state

    init {
        val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        val protocol = handle.get<String>(KEY_PROTOCOL)?.let(Presets::byId) ?: Presets.W16_8
        val end = handle.get<Long>(KEY_END)?.let(Instant::ofEpochMilli) ?: now.minus(Duration.ofHours(2))
        val start = handle.get<Long>(KEY_START)?.let(Instant::ofEpochMilli) ?: end.minus(Duration.ofHours(protocol.fastHours.toLong()))
        _state = MutableStateFlow(AddPastFastState(protocol, start, end))
    }

    private fun persist(s: AddPastFastState) {
        handle[KEY_PROTOCOL] = s.protocol.id
        handle[KEY_START] = s.start.toEpochMilli()
        handle[KEY_END] = s.end.toEpochMilli()
    }

    private fun change(f: (AddPastFastState) -> AddPastFastState) = _state.update { f(it).copy(error = false).also(::persist) }

    /** Choosing a protocol keeps the start and sets the end to its usual length. */
    fun select(p: Protocol) = change { it.copy(protocol = p, end = it.start.plus(Duration.ofHours(p.fastHours.toLong()))) }
    fun setStart(i: Instant) = change { it.copy(start = i) }
    fun setEnd(i: Instant) = change { it.copy(end = i) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            when (service.addPastFast(s.protocol, s.start, s.end)) {
                is EditResult.Updated -> onDone()
                else -> _state.update { it.copy(error = true) }
            }
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
        PrimaryButton(stringResource(R.string.save), onClick = { vm.save(onDone) }, haptic = true)
    }
}
