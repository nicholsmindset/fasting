package com.fastingshift.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.WeekFormat
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.startReminderNotificationsEnabled
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.schedule.DayOverride
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SchedulesRoute(onBack: () -> Unit, initialDate: LocalDate? = null, vm: SchedulesViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failure = stringResource(R.string.week_save_failed)
    LaunchedEffect(vm) {
        vm.failed.collect { error ->
            if (error) {
                snackbar.showSnackbar(failure)
                vm.failed.value = false
            }
        }
    }
    val format = rememberWeekFormat()
    val notifications = startReminderNotificationsEnabled()

    var editProtocol by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf(0L) }
    var removeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateText by rememberSaveable(initialDate) { mutableStateOf(initialDate?.toString()) }
    val closeEditor = { editProtocol = null }
    val editing = editProtocol
    val state = loaded
    BackHandler(enabled = editing != null) { if (!busy) closeEditor() }

    SecondaryScaffold(title = stringResource(if (editing == null) R.string.week_title else R.string.week_edit_routine),
        onBack = { if (!busy) { if (editing != null) closeEditor() else onBack() } }, snackbarHostState = snackbar) {
        when {
            state == null -> LoadingBox()
            editing != null -> {
                val original = state.routines.firstOrNull { it.id == editId }
                if (editId != 0L && original == null || !state.allows(editing)) {
                    LaunchedEffect(editId, editing) { closeEditor() }
                } else {
                    RoutineEditor(state, original, editing, format, notifications, busy,
                        onSave = { vm.save(it, closeEditor) }, onDelete = { vm.delete(editId, closeEditor) })
                }
            }
            else -> {
                fun edit(id: Long, protocol: String) { editId = id; editProtocol = protocol }
                val daily = state.dailyPlan
                if (daily != null && state.routines.none { it.protocolId == daily.id }) {
                    PrimaryButton(stringResource(R.string.week_create, daily.name), onClick = { edit(0, daily.id) })
                }
                if (state.routines.isNotEmpty()) {
                    InsetGroup(header = stringResource(R.string.week_routines)) {
                        state.routines.forEachIndexed { index, routine ->
                            if (index > 0) GroupDivider()
                            GroupRow(Presets.byId(routine.protocolId)?.name ?: routine.protocolId,
                                subtitle = if (routine.enabled) format.days(routine.days) else stringResource(R.string.week_paused),
                                value = format.time.format(routine.startTime), showChevron = state.allows(routine.protocolId),
                                onClick = if (state.allows(routine.protocolId)) ({ edit(routine.id, routine.protocolId) }) else null)
                            if (!state.allows(routine.protocolId)) {
                                GroupRow(stringResource(R.string.week_delete), onClick = { removeId = routine.id })
                            }
                        }
                    }
                }
                if (state.maxDuration == null) {
                    Text(stringResource(R.string.schedules_locked))
                } else {
                    WeekPreview(state, format, notifications, onDate = { dateText = it.toString() })
                    val anotherDaily = daily?.takeIf { state.routines.any { routine -> routine.protocolId == it.id } }
                    val presets = listOf(
                        Presets.FIVE_TWO to R.string.schedules_add_52,
                        Presets.ADF to R.string.schedules_add_adf,
                    ).filter { (preset, _) -> state.allows(preset.id) }
                    if (anotherDaily != null || presets.isNotEmpty()) {
                        InsetGroup(header = stringResource(R.string.schedules_new)) {
                            if (anotherDaily != null) {
                                GroupRow(stringResource(R.string.week_create, anotherDaily.name), showChevron = true,
                                    onClick = { edit(0, anotherDaily.id) })
                            }
                            presets.forEachIndexed { index, (preset, label) ->
                                if (index > 0 || anotherDaily != null) GroupDivider()
                                GroupRow(stringResource(label), showChevron = true, onClick = { edit(0, preset.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    val idToRemove = removeId
    if (idToRemove != null) {
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text(stringResource(R.string.week_delete)) },
            confirmButton = { TextButton(enabled = !busy, onClick = { vm.delete(idToRemove) { removeId = null } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    val day = dateText?.let(LocalDate::parse)
    if (day != null && state != null) {
        val original = state.starts(day, original = true)
        if (day < state.today || original.isEmpty()) {
            LaunchedEffect(day, state.today) { dateText = null }
        } else {
            DateEditor(day, state.settings.planHistory.dayOverrides[day]?.startTime
                ?: original.first().second.atZone(state.zone).toLocalTime(), state.today, state.now.atZone(state.zone).toLocalTime(),
                state.settings.planHistory.dayOverrides.containsKey(day), format, busy,
                onChange = { vm.changeDate(day, it) { dateText = null } }, onClose = { dateText = null })
        }
    }
}

@Composable
internal fun WeekPreview(state: PlannerState, format: WeekFormat, notifications: Boolean, onDate: ((LocalDate) -> Unit)? = null) {
    val next = state.nextStartAlert()?.atZone(state.zone)
    val summary = when {
        !state.settings.reminders.fastStartEnabled || !notifications -> stringResource(R.string.week_alert_off)
        state.active -> stringResource(R.string.week_active)
        next != null -> stringResource(R.string.week_next_reminder, format.date.format(next), format.time.format(next))
        else -> stringResource(R.string.week_no_reminder)
    }
    InsetGroup(header = stringResource(R.string.week_preview), footer = summary) {
        for (offset in 0L..6L) {
            val day = state.today.plusDays(offset)
            val starts = state.starts(day)
            val original = state.starts(day, original = true)
            val override = state.settings.planHistory.dayOverrides[day]
            val editable = onDate != null && original.isNotEmpty()
            val value = when {
                starts.isNotEmpty() -> starts.joinToString(" · ") { format.time.format(it.second.atZone(state.zone)) }
                original.isNotEmpty() && override != null -> stringResource(R.string.week_skipped)
                else -> stringResource(R.string.week_rest)
            }
            if (offset > 0) GroupDivider()
            GroupRow(format.date.format(day), modifier = Modifier.testTag("week-day-$day"),
                subtitle = if (starts.isNotEmpty() && override?.startTime != null) stringResource(R.string.week_changed) else null,
                value = value, showChevron = editable, onClick = if (editable) ({ onDate?.invoke(day); Unit }) else null)
        }
    }
}

@Composable
private fun DateEditor(day: LocalDate, time: LocalTime, today: LocalDate, now: LocalTime, changed: Boolean,
    format: WeekFormat, busy: Boolean, onChange: (DayOverride?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var invalid by rememberSaveable(day) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text(format.date.format(day)) },
        text = {
            Column {
                Text(stringResource(R.string.week_date_hint))
                TextButton(enabled = !busy, onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        val selected = LocalTime.of(hour, minute)
                        invalid = day == today && !selected.isAfter(now)
                        if (!invalid) onChange(DayOverride(selected))
                    }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                }) { Text(stringResource(R.string.week_change_time)) }
                TextButton(enabled = !busy, onClick = { onChange(DayOverride()) }) { Text(stringResource(R.string.week_skip_day)) }
                if (changed) TextButton(enabled = !busy, onClick = { onChange(null) }) { Text(stringResource(R.string.week_restore_day)) }
                if (invalid) Text(stringResource(R.string.week_future_time))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.cancel)) } })
}
