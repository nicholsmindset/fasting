package com.fastingshift.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.ramadan.CheckRow
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.fastingshift.app.ui.onboarding.SelectGroupRow
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
                SelectGroupRow(stringResource(appearanceLabel(mode)), selected == mode, onClick = { onPick(mode) })
            }
        }
    }
}

/** iOS-style − | + stepper. */
@Composable
fun StepperControl(onDecrease: () -> Unit, onIncrease: () -> Unit, canDecrease: Boolean = true, canIncrease: Boolean = true) {
    val haptics = rememberHaptics()
    val decrease = stringResource(R.string.a11y_decrease)
    val increase = stringResource(R.string.a11y_increase)
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf("−", decrease, canDecrease) { haptics.tick(); onDecrease() }
        VerticalDivider(Modifier.height(18.dp), color = MaterialTheme.colorScheme.outline)
        StepperHalf("+", increase, canIncrease) { haptics.tick(); onIncrease() }
    }
}

@Composable
private fun StepperHalf(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(46.dp).height(34.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

/** "12h, 16h" or "Off". */
@Composable
fun milestonesValue(hours: List<Int>): String {
    if (hours.isEmpty()) return stringResource(R.string.off)
    val format = stringResource(R.string.settings_hours_value)
    return hours.sorted().joinToString(", ") { String.format(java.util.Locale.getDefault(), format, it) }
}

@Composable
fun forgotValue(hours: Int): String = if (hours == 0) stringResource(R.string.off) else stringResource(R.string.settings_after_hours, hours)

@Composable
fun MilestonesSheet(chosen: Set<Int>, onToggle: (Int) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_milestones), onDismiss = onDismiss) {
        InsetGroup(footer = stringResource(R.string.settings_milestones_footer)) {
            MILESTONE_HOURS.forEachIndexed { i, h ->
                if (i > 0) GroupDivider()
                CheckRow(stringResource(R.string.settings_hours_value, h), selected = h in chosen, onClick = { onToggle(h) })
            }
        }
    }
}

@Composable
fun ForgotSheet(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_forgot_title), onDismiss = onDismiss) {
        InsetGroup(footer = stringResource(R.string.settings_forgot_footer)) {
            FORGOT_HOURS.forEachIndexed { i, h ->
                if (i > 0) GroupDivider()
                CheckRow(forgotValue(h), selected = h == selected, onClick = { onPick(h) })
            }
        }
    }
}

/** Automatic or custom daily water goal, with a 250 ml stepper. */
@Composable
fun WaterGoalSheet(display: WaterGoalDisplay, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.settings_water_goal), onDismiss = onDismiss) {
        InsetGroup(footer = stringResource(R.string.settings_water_footer)) {
            CheckRow(
                stringResource(R.string.settings_water_use_auto),
                selected = display.automatic,
                onClick = { onSet(null) },
                subtitle = stringResource(R.string.settings_liters, formatLiters(display.automaticMl)),
            )
            GroupDivider()
            GroupRow(
                title = stringResource(R.string.settings_water_custom),
                value = stringResource(R.string.settings_liters, formatLiters(display.ml)),
            ) {
                StepperControl(
                    onDecrease = { onSet(WaterGoalDisplay.stepped(display.ml, -1)) },
                    onIncrease = { onSet(WaterGoalDisplay.stepped(display.ml, 1)) },
                    canDecrease = display.ml > WaterGoalDisplay.MIN_ML,
                    canIncrease = display.ml < WaterGoalDisplay.MAX_ML,
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plan_book_code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = { onRedeem(code.trim()) }) { Text(stringResource(R.string.plan_redeem)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
