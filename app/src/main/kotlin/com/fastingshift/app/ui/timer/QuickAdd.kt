package com.fastingshift.app.ui.timer

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.components.weightUnitLabel
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SegmentedControl
import com.fastingshift.app.ui.theme.pickerDialogTheme
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** These secondary actions open editors. Water is logged directly from its daily total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddRow(
    showWeight: Boolean,
    weightUnit: WeightUnit,
    onWeight: (Double, WeightUnit) -> Unit,
    onElectrolyte: (Electrolyte, Double) -> Unit,
) {
    var dialog by rememberSaveable(showWeight) { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showWeight) {
            PlainButton(stringResource(R.string.quick_weight), onClick = { dialog = "weight" })
        }
        PlainButton(stringResource(R.string.quick_electrolytes), onClick = { dialog = "electrolytes" })
    }
    when (dialog) {
        "weight" -> if (showWeight) {
            WeightDialog(weightUnit, onDismiss = { dialog = null }) { v, u -> onWeight(v, u); dialog = null }
        }
        "electrolytes" -> ElectrolyteDialog(onDismiss = { dialog = null }) { e, mg -> onElectrolyte(e, mg); dialog = null }
    }
}

@Composable
private fun WeightDialog(initialUnit: WeightUnit, onDismiss: () -> Unit, onSave: (Double, WeightUnit) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(initialUnit) }
    val parsed = value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..900.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_weight)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = { value = it.take(6) }, singleLine = true, label = { Text(weightUnitLabel(unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                SegmentedControl(WeightUnit.entries.map { weightUnitLabel(it) }, WeightUnit.entries.indexOf(unit), { unit = WeightUnit.entries[it] })
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let { onSave(it, unit) } }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElectrolyteDialog(onDismiss: () -> Unit, onSave: (Electrolyte, Double) -> Unit) {
    var which by rememberSaveable { mutableStateOf(Electrolyte.SODIUM) }
    var amount by rememberSaveable { mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()?.takeIf { it > 0 && it <= 20_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_electrolytes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedControl(Electrolyte.entries.map { stringResource(electrolyteLabel(it)) }, Electrolyte.entries.indexOf(which), { which = Electrolyte.entries[it] })
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(5) }, singleLine = true,
                    label = { Text(stringResource(R.string.quick_amount_mg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let { onSave(which, it) } }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun electrolyteLabel(e: Electrolyte) = when (e) {
    Electrolyte.SODIUM -> R.string.el_sodium
    Electrolyte.POTASSIUM -> R.string.el_potassium
    Electrolyte.MAGNESIUM -> R.string.el_magnesium
}

/** "Started earlier?": pick today or yesterday, then a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartEarlierDialog(initial: Instant?, now: Instant, onDismiss: () -> Unit, onPick: (Instant) -> Unit,
    zone: ZoneId = ZoneId.systemDefault()) {
    val context = LocalContext.current
    val pickerTheme = pickerDialogTheme()
    val format = rememberWeekFormat()
    val today = now.atZone(zone).toLocalDate()
    val seedMillis = rememberSaveable(initial) { (initial ?: now.minusSeconds(3600)).toEpochMilli() }
    val seed = Instant.ofEpochMilli(seedMillis).atZone(zone)
    var day by rememberSaveable { mutableStateOf(seed.toLocalDate().toEpochDay()) }
    var minutes by rememberSaveable { mutableStateOf(seed.hour * 60 + seed.minute) }
    val time = LocalTime.of(minutes / 60, minutes % 60)
    val local = LocalDate.ofEpochDay(day).atTime(time)
    val picked = java.time.ZonedDateTime.ofLocal(local, zone, seed.offset)
    val valid = picked.toLocalDateTime() == local && !picked.toInstant().isAfter(now)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_start_earlier_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedControl(
                    listOf(stringResource(R.string.today), stringResource(R.string.yesterday)),
                    if (day == today.toEpochDay()) 0 else 1,
                    { day = today.minusDays(it.toLong()).toEpochDay() },
                )
                SecondaryButton(
                    format.time.format(time),
                    onClick = {
                        TimePickerDialog(context, pickerTheme, { _, h, m -> minutes = h * 60 + m }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                    },
                )
                if (!valid) Text(stringResource(R.string.plan_past_time), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onPick(picked.toInstant()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
