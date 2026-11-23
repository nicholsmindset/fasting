package com.fastingshift.app.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.WeekFormat
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.onboarding.CheckGroupRow
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

private enum class Repeat(val label: Int) {
    WEEKDAYS(R.string.week_weekdays),
    EVERY_DAY(R.string.week_every_day),
    CHOOSE(R.string.week_choose_days),
}

@Composable
internal fun RoutineEditor(state: PlannerState, original: Schedule?, protocol: String, format: WeekFormat,
    notifications: Boolean, busy: Boolean, onSave: (Schedule) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val initial = original ?: Schedule(
        protocolId = protocol, days = when (protocol) {
            Presets.FIVE_TWO.id -> setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
            Presets.ADF.id -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            else -> weekdays
        }, startTime = LocalTime.of(20, 0),
    )
    val key = "${initial.id}:$protocol"
    var repeat by rememberSaveable(key) { mutableStateOf(when (initial.days) {
        weekdays -> Repeat.WEEKDAYS
        DayOfWeek.entries.toSet() -> Repeat.EVERY_DAY
        else -> Repeat.CHOOSE
    }) }
    var customDays by rememberSaveable(key) { mutableStateOf(initial.days.sumOf { 1 shl it.ordinal }) }
    var minutes by rememberSaveable(key) { mutableStateOf(initial.startTime.toSecondOfDay() / 60) }
    var enabled by rememberSaveable(key) { mutableStateOf(initial.enabled) }
    var repeatMenu by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val days = when (repeat) {
        Repeat.WEEKDAYS -> weekdays
        Repeat.EVERY_DAY -> DayOfWeek.entries.toSet()
        Repeat.CHOOSE -> DayOfWeek.entries.filterTo(mutableSetOf()) { customDays and (1 shl it.ordinal) != 0 }
    }
    val time = LocalTime.of(minutes / 60, minutes % 60)
    val draft = initial.copy(days = days, startTime = time, enabled = enabled)
    val preview = state.copy(schedules = state.schedules.filter { it.id != initial.id } + draft)
    InsetGroup(header = Presets.byId(protocol)?.name ?: protocol) {
        Box {
            GroupRow(stringResource(R.string.week_repeat), value = stringResource(repeat.label), showChevron = true, onClick = { repeatMenu = true })
            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                Repeat.entries.forEach { item ->
                    DropdownMenuItem(text = { Text(stringResource(item.label)) }, onClick = {
                        if (repeat != Repeat.CHOOSE && item == Repeat.CHOOSE) customDays = days.sumOf { 1 shl it.ordinal }
                        repeat = item
                        repeatMenu = false
                    })
                }
            }
        }
        if (repeat == Repeat.CHOOSE) {
            DayOfWeek.entries.forEach { day ->
                GroupDivider()
                CheckGroupRow(day.getDisplayName(TextStyle.FULL, format.locale), day in days, { customDays = customDays xor (1 shl day.ordinal) })
            }
        }
        GroupDivider()
        GroupRow(stringResource(R.string.schedules_start_time), value = format.time.format(time), showChevron = true,
            onClick = {
                TimePickerDialog(context, { _, hour, minute -> minutes = hour * 60 + minute },
                    time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            })
        if (original != null) {
            GroupDivider()
            SwitchGroupRow(stringResource(R.string.schedules_enabled), enabled, { enabled = it })
        }
    }
    WeekPreview(preview, format, notifications)
    PrimaryButton(stringResource(R.string.week_save), onClick = { onSave(draft) }, enabled = !busy && days.isNotEmpty())
    if (original != null) {
        TextButton(enabled = !busy, onClick = { deleteDialog = true }) { Text(stringResource(R.string.week_delete)) }
    }
    if (deleteDialog) {
        AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text(stringResource(R.string.week_delete)) },
            confirmButton = { TextButton(enabled = !busy, onClick = onDelete) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
