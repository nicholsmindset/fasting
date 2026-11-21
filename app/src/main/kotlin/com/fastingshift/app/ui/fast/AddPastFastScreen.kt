package com.fastingshift.app.ui.fast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPastFastRoute(onDone: () -> Unit, vm: AddPastFastViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SecondaryScaffold(title = stringResource(R.string.add_past_title), onBack = onDone) {
        Text(stringResource(R.string.add_past_body))
        Text(stringResource(R.string.add_past_protocol), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.all.forEach { p -> FilterChip(selected = p.id == st.protocol.id, onClick = { vm.select(p) }, label = { Text(p.name) }) }
        }
        Text(stringResource(R.string.edit_start))
        OutlinedButton(onClick = { pickDateTime(context, st.start, vm::setStart) }, modifier = Modifier.fillMaxWidth()) { Text(st.start.formatDateTime()) }
        Text(stringResource(R.string.edit_end))
        OutlinedButton(onClick = { pickDateTime(context, st.end, vm::setEnd) }, modifier = Modifier.fillMaxWidth()) { Text(st.end.formatDateTime()) }
        val length = Duration.between(st.start, st.end)
        if (!length.isNegative) Text(stringResource(R.string.refeed_your_fast, length.hoursMinutes()), style = MaterialTheme.typography.bodyMedium)
        if (st.error) Text(stringResource(R.string.add_past_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
    }
}
