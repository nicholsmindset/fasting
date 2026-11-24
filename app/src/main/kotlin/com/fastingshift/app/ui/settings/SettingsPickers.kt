package com.fastingshift.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.fastingshift.app.ui.ramadan.RadioChoiceRow
import com.fastingshift.data.settings.AppAppearance

val MILESTONE_HOURS = listOf(12, 14, 16, 18, 20, 24, 36, 48)
val FORGOT_HOURS = listOf(0, 2, 4, 6, 8)

internal fun appearanceLabel(mode: AppAppearance): Int = when (mode) {
    AppAppearance.SYSTEM -> R.string.appearance_system
    AppAppearance.LIGHT -> R.string.appearance_light
    AppAppearance.DARK -> R.string.appearance_dark
}

@Composable
internal fun AppearanceSheet(selected: AppAppearance, onPick: (AppAppearance) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.appearance_title), onDismiss = onDismiss) {
        InsetGroup(modifier = Modifier.selectableGroup()) {
            AppAppearance.entries.forEachIndexed { index, mode ->
                if (index > 0) GroupDivider()
                RadioChoiceRow(stringResource(appearanceLabel(mode)), selected == mode, onClick = { onPick(mode) })
            }
        }
    }
}

@Composable
internal fun EatingWindowControl(hours: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val title = stringResource(R.string.settings_eating_window_title)
    val subtitle = stringResource(R.string.settings_eating_window_subtitle)
    val value = stringResource(R.string.settings_hours_value, hours)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        val stepper: @Composable () -> Unit = {
            ValueStepper(title, value, hours > 1, hours < 23, onDecrease, onIncrease)
        }
        if (maxWidth < 290.dp || LocalDensity.current.fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingLabel(title, subtitle)
                stepper()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingLabel(title, subtitle, Modifier.weight(1f))
                stepper()
            }
        }
    }
}

@Composable
private fun SettingLabel(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ValueStepper(title: String, value: String, canDecrease: Boolean, canIncrease: Boolean,
    onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        StepButton(false, title, value, canDecrease) { haptics.tick(); onDecrease() }
        Text(value, modifier = Modifier.widthIn(min = 38.dp).padding(horizontal = 2.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
        StepButton(true, title, value, canIncrease) { haptics.tick(); onIncrease() }
    }
}

@Composable
private fun StepButton(increase: Boolean, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    val action = stringResource(if (increase) R.string.a11y_increase else R.string.a11y_decrease)
    IconButton(enabled = enabled, onClick = onClick, modifier = Modifier.size(48.dp).semantics {
        contentDescription = "$action, $title"
        stateDescription = value
    }) {
        val tint = LocalContentColor.current
        Canvas(Modifier.size(16.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(tint, Offset(0f, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
            if (increase) drawLine(tint, Offset(center.x, 0f), Offset(center.x, size.height), stroke, StrokeCap.Round)
        }
    }
}

/** "12h, 16h" or "Off". */
@Composable
fun milestonesValue(hours: List<Int>): String {
    if (hours.isEmpty()) return stringResource(R.string.off)
    val format = stringResource(R.string.settings_hours_value)
    val locale = LocalConfiguration.current.locales[0]
    return hours.sorted().joinToString(", ") { String.format(locale, format, it) }
}

@Composable
fun forgotValue(hours: Int): String = if (hours == 0) stringResource(R.string.off) else stringResource(R.string.settings_after_hours, hours)

@Composable
fun MilestonesSheet(chosen: Set<Int>, onToggle: (Int) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_milestones), onDismiss = onDismiss) {
        InsetGroup(footer = stringResource(R.string.settings_milestones_footer)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 260.dp && LocalDensity.current.fontScale <= 1.6f) 2 else 1
                Column {
                    MILESTONE_HOURS.chunked(columns).forEachIndexed { index, hours ->
                        if (index > 0) GroupDivider()
                        Row(Modifier.fillMaxWidth()) {
                            hours.forEach { hour ->
                                val checked = hour in chosen
                                val haptics = rememberHaptics()
                                Row(Modifier.weight(1f).toggleable(value = checked, role = Role.Checkbox,
                                    onValueChange = { haptics.tick(); onToggle(hour) })
                                    .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Checkbox(checked, onCheckedChange = null)
                                    Text(stringResource(R.string.settings_hours_value, hour), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotSheet(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_forgot_title), onDismiss = onDismiss) {
        InsetGroup(modifier = Modifier.selectableGroup(), footer = stringResource(R.string.settings_forgot_footer)) {
            FORGOT_HOURS.forEachIndexed { i, h ->
                if (i > 0) GroupDivider()
                RadioChoiceRow(forgotValue(h), selected = h == selected, onClick = { onPick(h) })
            }
        }
    }
}

/** Automatic or custom daily water goal, with a 250 ml stepper. */
@Composable
fun WaterGoalSheet(display: WaterGoalDisplay, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_water_goal), onDismiss = onDismiss) {
        InsetGroup(footer = stringResource(R.string.settings_water_footer)) {
            val locale = LocalConfiguration.current.locales[0]
            val value = stringResource(R.string.settings_liters, formatLiters(display.ml, locale))
            Column(Modifier.selectableGroup()) {
                RadioChoiceRow(stringResource(R.string.settings_water_use_auto), selected = display.automatic, onClick = { onSet(null) },
                    subtitle = stringResource(R.string.settings_liters, formatLiters(display.automaticMl, locale)))
                GroupDivider()
                RadioChoiceRow(stringResource(R.string.settings_water_custom), selected = !display.automatic, onClick = {
                    if (display.automatic) onSet(display.ml)
                })
            }
            if (!display.automatic) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    ValueStepper(stringResource(R.string.settings_water_goal), value,
                        canDecrease = display.ml > WaterGoalDisplay.MIN_ML, canIncrease = display.ml < WaterGoalDisplay.MAX_ML,
                        onDecrease = { onSet(WaterGoalDisplay.stepped(display.ml, -1)) },
                        onIncrease = { onSet(WaterGoalDisplay.stepped(display.ml, 1)) })
                }
            }
        }
    }
}

@Composable
fun BookCodeDialog(onRedeem: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_book_code)) },
        text = {
            OutlinedTextField(
                value = code, onValueChange = { code = it }, singleLine = true,
                label = { Text(stringResource(R.string.plan_book_code)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) onRedeem(code.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = { onRedeem(code.trim()) }) { Text(stringResource(R.string.plan_redeem)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
