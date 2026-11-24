package com.fastingshift.app.ui.timer

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.fastingshift.app.ui.design.GroupHeader
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SegmentedControl
import com.fastingshift.app.ui.design.rememberHaptics
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
import com.fastingshift.app.ui.theme.pickerDialogTheme
import com.fastingshift.app.ui.components.rememberWeekFormat
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
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        GroupHeader(stringResource(R.string.quick_add_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(stringResource(R.string.quick_water), WaterBlue, Modifier.weight(1f), onClick = { haptics.tick(); onWater() }) { WaterDrop(it) }
            // Hide weight mode removes weight from every screen (SR-6).
            if (showWeight) {
                QuickTile(stringResource(R.string.quick_weight), MaterialTheme.colorScheme.primary, Modifier.weight(1f), onClick = { dialog = "weight" }) {
                    Text(weightUnitLabel(weightUnit), style = MaterialTheme.typography.titleSmall, color = it)
                }
            }
            QuickTile(stringResource(R.string.quick_electrolytes), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f), onClick = { dialog = "electrolytes" }) {
                Text("Na", style = MaterialTheme.typography.titleSmall, color = it)
            }
        }
    }
    when (dialog) {
        "weight" -> WeightDialog(weightUnit, onDismiss = { dialog = null }) { v, u -> onWeight(v, u); dialog = null }
        "electrolytes" -> ElectrolyteDialog(onDismiss = { dialog = null }) { e, mg -> onElectrolyte(e, mg); dialog = null }
    }
}

/** A rounded white tile: a tinted badge with a glyph, and a label. The whole tile is one button. */
@Composable
private fun QuickTile(label: String, tint: Color, modifier: Modifier, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 88.dp).semantics { role = Role.Button },
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { glyph(tint) }
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** A small water drop, drawn so it needs no icon font. */
@Composable
fun WaterDrop(color: Color, modifier: Modifier = Modifier.size(width = 14.dp, height = 18.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2, 0f)
            cubicTo(w * 0.9f, h * 0.38f, w, h * 0.55f, w, h * 0.66f)
            cubicTo(w, h * 0.86f, w * 0.78f, h, w / 2, h)
            cubicTo(w * 0.22f, h, 0f, h * 0.86f, 0f, h * 0.66f)
            cubicTo(0f, h * 0.55f, w * 0.1f, h * 0.38f, w / 2, 0f)
            close()
        }
        drawPath(path, color)
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
