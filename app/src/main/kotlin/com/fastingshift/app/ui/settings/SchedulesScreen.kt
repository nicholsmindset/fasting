package com.fastingshift.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.fastingshift.app.ui.ramadan.GroupedSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.theme.pickerDialogTheme
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
import kotlinx.coroutines.launch

@Composable
fun SchedulesRoute(onBack: () -> Unit, initialDate: LocalDate? = null, vm: SchedulesViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.plan_date_saved)
    val undoLabel = stringResource(R.string.plan_date_undo)
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
            val override = state.settings.planHistory.dayOverrides[day]
            val usualTimes = original.map { it.second.atZone(state.zone).toLocalTime() }
            val currentTimes = state.starts(day).map { it.second.atZone(state.zone).toLocalTime() }
            DateEditor(day, usualTimes, currentTimes, override, state, notifications, format, busy,
                onChange = { replacement ->
                    vm.changeDate(day, override, replacement) {
                        dateText = null
                        snackbar.currentSnackbarData?.dismiss()
                        scope.launch {
                            if (snackbar.showSnackbar(saved, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                                vm.undoDate(day, replacement, override)
                            }
                        }
                    }
                }, onClose = { dateText = null })
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
private fun DateEditor(day: LocalDate, usualTimes: List<LocalTime>, currentTimes: List<LocalTime>, override: DayOverride?,
    state: PlannerState, notifications: Boolean, format: WeekFormat, busy: Boolean, onChange: (DayOverride?) -> Unit,
    onClose: () -> Unit) {
    val context = LocalContext.current
    val pickerTheme = pickerDialogTheme()
    // null means no pending edit. "usual" explicitly restores the routine, including after rotation.
    var draft by rememberSaveable(day, override?.toRecord()) { mutableStateOf<String?>(null) }
    var invalid by rememberSaveable(day) { mutableStateOf(false) }
    val proposed = draft?.let(DayOverride::fromRecord)
    val time = proposed?.startTime ?: override?.startTime ?: usualTimes.first()
    val preview = draft?.let { state.preview(day, proposed) }
    val expired = proposed?.startTime != null && preview?.start?.isAfter(state.now) != true
    GroupedSheet(format.date.format(day), onDismiss = { if (!busy) onClose() }, dismissLabel = android.R.string.cancel) {
        if (preview == null) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.plan_this_date), style = MaterialTheme.typography.labelLarge)
                val value = if (currentTimes.isEmpty()) stringResource(R.string.week_skipped)
                    else currentTimes.joinToString(" · ") { format.time.format(it) }
                Text(value, style = MaterialTheme.typography.headlineMedium)
                if (override != null) {
                    Text(stringResource(R.string.plan_usual_times, usualTimes.joinToString(" · ") { format.time.format(it) }),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.week_date_hint), style = MaterialTheme.typography.bodyMedium)
        } else {
            DatePreview(day, state, preview, notifications, format)
        }
        val pickTime = {
            TimePickerDialog(context, pickerTheme, { _, hour, minute ->
                val selected = LocalTime.of(hour, minute)
                invalid = !day.atTime(selected).atZone(state.zone).toInstant().isAfter(state.now)
                if (!invalid) {
                    val replacement = if (selected == usualTimes.first()) null else DayOverride(selected)
                    draft = if (replacement == override) null else replacement?.toRecord() ?: "usual"
                }
            }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            Unit
        }
        if (invalid || expired) Text(stringResource(R.string.week_future_time),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
        if (preview != null) {
            PrimaryButton(stringResource(R.string.plan_date_save), onClick = { onChange(proposed) },
                modifier = Modifier.testTag("plan-date-save"), enabled = !busy && !expired && !invalid)
            TextButton(enabled = !busy, onClick = pickTime) { Text(stringResource(R.string.week_change_time)) }
        } else {
            PrimaryButton(stringResource(R.string.week_change_time), enabled = !busy, onClick = pickTime)
            if (override?.isSkipped != true) {
                TextButton(enabled = !busy, onClick = { invalid = false; draft = DayOverride().toRecord() }) {
                    Text(stringResource(R.string.week_skip_day))
                }
            }
            if (override != null) {
                TextButton(enabled = !busy, onClick = { invalid = false; draft = "usual" }) {
                    Text(stringResource(R.string.week_restore_day))
                }
            }
        }
    }
}

@Composable
private fun DatePreview(day: LocalDate, state: PlannerState, preview: DateChangePreview, notifications: Boolean, format: WeekFormat) {
    Column(Modifier.testTag("plan-date-preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        preview.start?.let { PreviewTime(stringResource(R.string.plan_date_start), format.dateTime.format(it.atZone(state.zone))) }
        preview.finish?.let { PreviewTime(stringResource(R.string.plan_date_finish), format.dateTime.format(it.atZone(state.zone))) }
        if (preview.skipped) {
            Text(stringResource(R.string.week_skipped), style = MaterialTheme.typography.titleLarge)
        }
        val before = preview.previousAlert?.atZone(state.zone)?.let(format.dateTime::format)
        val after = preview.nextAlert?.atZone(state.zone)?.let(format.dateTime::format)
        val reminder = when {
            !state.settings.reminders.fastStartEnabled || !notifications -> stringResource(R.string.week_alert_off)
            state.active -> stringResource(R.string.week_active)
            after == null && before != null -> stringResource(R.string.plan_date_reminder_removed, before)
            after == null -> stringResource(R.string.week_no_reminder)
            before == null -> stringResource(R.string.plan_date_reminder_added, after)
            preview.previousAlert == preview.nextAlert -> stringResource(R.string.plan_date_reminder_same, after)
            else -> stringResource(R.string.plan_date_reminder_moved, before, after)
        }
        Text(reminder, style = MaterialTheme.typography.bodyMedium)
        val tomorrow = day.plusDays(1)
        val times = state.starts(tomorrow).joinToString(" · ") { format.time.format(it.second.atZone(state.zone)) }
        Text(if (times.isEmpty()) stringResource(R.string.plan_date_only)
            else stringResource(R.string.plan_date_next_unchanged, format.date.format(tomorrow), times),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewTime(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}
