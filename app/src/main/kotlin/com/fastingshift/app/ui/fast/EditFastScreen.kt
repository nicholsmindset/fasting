package com.fastingshift.app.ui.fast

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.service.EditResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditFastState(
    val original: Fast? = null,
    val start: Instant? = null,
    val targetEnd: Instant? = null,
    val end: Instant? = null,
    val note: String = "",
    val error: Int? = null,
)

@HiltViewModel
class EditFastViewModel @Inject constructor(
    private val fasts: FastRepository,
    private val service: FastService,
) : ViewModel() {
    private val _state = MutableStateFlow(EditFastState())
    val state: StateFlow<EditFastState> = _state

    fun load(id: Long) {
        if (_state.value.original?.id == id) return
        viewModelScope.launch {
            val f = fasts.getById(id) ?: return@launch
            _state.value = EditFastState(f, f.startAt, f.targetEndAt, f.endedAt, f.note.orEmpty())
        }
    }

    /** Moving the start keeps the planned length, matching how people think about edits. */
    fun setStart(i: Instant) = _state.update {
        val planned = Duration.between(it.start, it.targetEnd)
        it.copy(start = i, targetEnd = i.plus(planned), error = null)
    }
    fun setTargetEnd(i: Instant) = _state.update { it.copy(targetEnd = i, error = null) }
    fun setEnd(i: Instant) = _state.update { it.copy(end = i, error = null) }
    fun setNote(n: String) = _state.update { it.copy(note = n.take(500)) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val f = s.original ?: return
        viewModelScope.launch {
            when (service.editTimes(f.id, s.start!!, s.targetEnd!!, s.end)) {
                is EditResult.Updated -> {
                    fasts.getById(f.id)?.let { fasts.update(it.copy(note = s.note.ifBlank { null })) }
                    onDone()
                }
                is EditResult.Blocked, is EditResult.NeedsConfirmation -> _state.update { it.copy(error = R.string.edit_error_blocked) }
                EditResult.Invalid -> _state.update { it.copy(error = R.string.edit_error_invalid) }
                EditResult.NotFound -> onDone()
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val f = _state.value.original ?: return
        viewModelScope.launch {
            fasts.delete(f.id)
            onDone()
        }
    }
}

@Composable
fun EditFastRoute(fastId: Long, onDone: () -> Unit, vm: EditFastViewModel = hiltViewModel()) {
    vm.load(fastId)
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val f = st.original ?: return
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.headlineSmall)
            st.start?.let { s ->
                Text(stringResource(R.string.edit_start))
                OutlinedButton(onClick = { pickDateTime(context, s, vm::setStart) }, modifier = Modifier.fillMaxWidth()) { Text(s.formatDateTime()) }
            }
            st.targetEnd?.let { t ->
                Text(stringResource(R.string.edit_goal, Duration.between(st.start, t).hoursMinutes()))
                OutlinedButton(onClick = { pickDateTime(context, t, vm::setTargetEnd) }, modifier = Modifier.fillMaxWidth()) { Text(t.formatDateTime()) }
            }
            st.end?.let { e ->
                Text(stringResource(R.string.edit_end))
                OutlinedButton(onClick = { pickDateTime(context, e, vm::setEnd) }, modifier = Modifier.fillMaxWidth()) { Text(e.formatDateTime()) }
            }
            OutlinedTextField(value = st.note, onValueChange = vm::setNote, label = { Text(stringResource(R.string.edit_note)) }, modifier = Modifier.fillMaxWidth())
            st.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            if (!f.isActive) {
                TextButton(onClick = { vm.delete(onDone) }) { Text(stringResource(R.string.edit_delete)) }
            }
        }
    }
}

private fun pickDateTime(context: Context, initial: Instant, onPicked: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val z = initial.atZone(zone)
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            onPicked(ZonedDateTime.of(y, m + 1, d, h, min, 0, 0, zone).toInstant())
        }, z.hour, z.minute, DateFormat.is24HourFormat(context)).show()
    }, z.year, z.monthValue - 1, z.dayOfMonth).show()
}
