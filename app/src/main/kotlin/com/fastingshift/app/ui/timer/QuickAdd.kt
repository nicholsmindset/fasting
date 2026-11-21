package com.fastingshift.app.ui.timer

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.weightUnitLabel
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** PRD F6 quick-add on the Timer: water in one tap, weight and electrolytes in small dialogs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddRow(
    showWeight: Boolean,
    weightUnit: WeightUnit,
    onWater: () -> Unit,
    onWeight: (Double, WeightUnit) -> Unit,
    onElectrolyte: (Electrolyte, Double) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = onWater, label = { Text(stringResource(R.string.quick_water)) })
            // Hide weight mode removes weight from every screen (SR-6).
            if (showWeight) AssistChip(onClick = { dialog = "weight" }, label = { Text(stringResource(R.string.quick_weight)) })
            AssistChip(onClick = { dialog = "electrolytes" }, label = { Text(stringResource(R.string.quick_electrolytes)) })
        }
    }
    when (dialog) {
        "weight" -> WeightDialog(weightUnit, onDismiss = { dialog = null }) { v, u -> onWeight(v, u); dialog = null }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = { value = it.take(6) }, singleLine = true, label = { Text(weightUnitLabel(unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                WeightUnit.entries.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(weightUnitLabel(u)) }) }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Electrolyte.entries.forEach { e -> FilterChip(selected = which == e, onClick = { which = e }, label = { Text(stringResource(electrolyteLabel(e))) }) }
                }
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
fun StartEarlierDialog(initial: Instant?, onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = initial?.atZone(zone)
    var yesterday by rememberSaveable { mutableStateOf(start != null && start.toLocalDate() != today) }
    var minutes by rememberSaveable {
        mutableStateOf(start?.toLocalTime()?.let { it.hour * 60 + it.minute } ?: LocalTime.now(zone).minusHours(1).let { it.hour * 60 + it.minute })
    }
    val time = LocalTime.of(minutes / 60, minutes % 60)
    val picked = (if (yesterday) today.minusDays(1) else today).atTime(time).atZone(zone).toInstant()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_start_earlier_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text(stringResource(R.string.today)) })
                    FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text(stringResource(R.string.yesterday)) })
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> minutes = h * 60 + m }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(time))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
