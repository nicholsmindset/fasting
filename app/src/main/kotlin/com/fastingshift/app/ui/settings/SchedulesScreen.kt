package com.fastingshift.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SchedulesViewModel @Inject constructor(private val repo: ScheduleRepository) : ViewModel() {
    val schedules = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(protocolId: String, days: Set<DayOfWeek>) = viewModelScope.launch {
        repo.save(Schedule(protocolId = protocolId, days = days, startTime = LocalTime.of(20, 0)))
    }
    fun save(s: Schedule) = viewModelScope.launch { repo.save(s) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

/** Weekly schedules for 5:2, ADF or any fasting days (PRD F2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulesRoute(onBack: () -> Unit, vm: SchedulesViewModel = hiltViewModel()) {
    val list by vm.schedules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.schedules_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.schedules_body))
        list.forEach { s ->
            SectionCard(Presets.byId(s.protocolId)?.name ?: s.protocolId) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d in s.days,
                            onClick = { vm.save(s.copy(days = if (d in s.days) s.days - d else s.days + d)) },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> vm.save(s.copy(startTime = LocalTime.of(h, m))) },
                            s.startTime.hour, s.startTime.minute, DateFormat.is24HourFormat(context)).show()
                    }) { Text(stringResource(R.string.schedules_start_at, s.startTime.toString())) }
                    Switch(checked = s.enabled, onCheckedChange = { vm.save(s.copy(enabled = it)) })
                }
                TextButton(onClick = { vm.delete(s.id) }) { Text(stringResource(R.string.delete)) }
            }
        }
        Button(onClick = { vm.add(Presets.FIVE_TWO.id, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)) }) { Text(stringResource(R.string.schedules_add_52)) }
        Button(onClick = { vm.add(Presets.ADF.id, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) }) { Text(stringResource(R.string.schedules_add_adf)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
