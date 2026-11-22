package com.fastingshift.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fastingshift.app.ui.design.rememberHaptics

/*
 * Form rows for inset groups (iOS-style), shared by onboarding, safety, first-week and fast
 * screens. They live next to the design system's GroupRow and follow the same metrics.
 */

/** Single choice row: title, optional subtitle and a checkmark when selected (radio semantics). */
@Composable
fun SelectGroupRow(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null, badge: String? = null) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.tick(); onClick() })
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        CheckMark(selected)
    }
}

/** Multiple choice row with a checkmark (checkbox semantics; the whole row toggles). */
@Composable
fun CheckGroupRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { haptics.tick(); onCheckedChange(it) })
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckCircle(checked)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckMark(visible: Boolean) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Round iOS-style check circle: outlined when off, filled accent with a tick when on. */
@Composable
private fun CheckCircle(checked: Boolean) {
    val color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (checked) color else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * A text field laid out as a group row: label on the left, the value on the right. The label is
 * part of the field itself, so the whole row focuses it and TalkBack reads the label.
 */
@Composable
fun TextFieldGroupRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    singleLine: Boolean = true,
    valueWidth: Dp = 120.dp,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (singleLine) TextAlign.End else TextAlign.Start,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (singleLine) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box(Modifier.widthIn(min = 48.dp, max = valueWidth), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.outline))
                        }
                        inner()
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Box {
                        if (value.isEmpty()) Text(label, style = style.copy(color = MaterialTheme.colorScheme.outline))
                        inner()
                    }
                }
            }
        },
    )
}

/** An icon in a soft tinted circle, used at the top of calm result and intro screens. */
@Composable
fun HeroIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 72.dp, container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
