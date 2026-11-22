package com.fastingshift.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.design.rememberHaptics
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
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
import com.fastingshift.app.ui.components.SecondaryScaffold
import androidx.compose.runtime.remember
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repo: ScheduleRepository,
    screening: com.fastingshift.domain.service.ScreeningRepository,
) : ViewModel() {
    val schedules = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Schedules only make sense for people who may start a fast (SR-1/SR-2). */
    val canSchedule = screening.observeLatest()
        .map { r -> r != null && com.fastingshift.domain.safety.FastPolicy.maxAllowed(r.gateResult, r.clinicianAcknowledged) != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun add(protocolId: String, days: Set<DayOfWeek>) = viewModelScope.launch {
        repo.save(Schedule(protocolId = protocolId, days = days, startTime = LocalTime.of(20, 0)))
    }
    fun save(s: Schedule) = viewModelScope.launch { repo.save(s) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

/** Weekly schedules for 5:2, ADF or any fasting days (PRD F2). */
@Composable
fun SchedulesRoute(onBack: () -> Unit, vm: SchedulesViewModel = hiltViewModel()) {
    val list by vm.schedules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    SecondaryScaffold(title = stringResource(R.string.schedules_title), onBack = onBack) {
        Text(
            stringResource(R.string.schedules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        list.forEach { s ->
            InsetGroup(header = Presets.byId(s.protocolId)?.name ?: s.protocolId) {
                SwitchGroupRow(stringResource(R.string.schedules_enabled), s.enabled, { vm.save(s.copy(enabled = it)) })
                GroupDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.schedules_days), style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DayOfWeek.entries.forEach { d ->
                            DayToggle(d, selected = d in s.days) { vm.save(s.copy(days = if (d in s.days) s.days - d else s.days + d)) }
                        }
                    }
                }
                GroupDivider()
                GroupRow(
                    stringResource(R.string.schedules_start_time),
                    value = timeFormat.format(s.startTime),
                    showChevron = true,
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> vm.save(s.copy(startTime = LocalTime.of(h, m))) },
                            s.startTime.hour, s.startTime.minute, DateFormat.is24HourFormat(context)).show()
                    },
                )
                GroupDivider()
                GroupRow(stringResource(R.string.delete), titleColor = MaterialTheme.colorScheme.error, onClick = { vm.delete(s.id) })
            }
        }
        val canSchedule by vm.canSchedule.collectAsStateWithLifecycle()
        if (canSchedule) {
            InsetGroup(header = stringResource(R.string.schedules_new)) {
                GroupRow(stringResource(R.string.schedules_add_52), titleColor = MaterialTheme.colorScheme.primary, onClick = {
                    vm.add(Presets.FIVE_TWO.id, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
                })
                GroupDivider()
                GroupRow(stringResource(R.string.schedules_add_adf), titleColor = MaterialTheme.colorScheme.primary, onClick = {
                    vm.add(Presets.ADF.id, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                })
            }
        } else {
            Text(
                stringResource(R.string.schedules_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** A round day toggle (filled when it's a fasting day), like iOS's repeat-day picker. */
@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onToggle: () -> Unit) {
    val haptics = rememberHaptics()
    val full = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { haptics.tick(); onToggle() })
            .semantics { contentDescription = full },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
